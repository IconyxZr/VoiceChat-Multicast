package voicechat_client;

import voicechat_comm.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
/**
 *
 * @author ASUS
 */
public class AudioChannel extends Thread {

    private long chId; //sebuah id unik untuk setiap pengguna. dihasilkan oleh IP dan port
    private ArrayList<Message> queue = new ArrayList<Message>(); //antria pesan untuk dimulai
    private int lastSoundPacketLen = SoundPacket.defaultDataLenght;
    private long lastPacketTime = System.nanoTime();

    public boolean canKill() { //mengembalikan nilai tru,sejak paket terakhir diterima
        if (System.nanoTime() - lastPacketTime > 5000000000L) {
            return true; //5 detik tanpa data
        } else {
            return false;
        }
    }

    public void closeAndKill() {
        if (speaker != null) {
            speaker.close();
        }
        stop();
    }

    public AudioChannel(long chId) {
        this.chId = chId;
    }

    public long getChId() {
        return chId;
    }

    public void addToQueue(Message m) { //menambahkan pesan ke antrian putar
        queue.add(m);
    }
    private SourceDataLine speaker = null; //speaker

    @Override
    public void run() {
        try {
            //open channel untuk kartu suara
            AudioFormat af = SoundPacket.defaultFormat;
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
            speaker = (SourceDataLine) AudioSystem.getLine(info);
            speaker.open(af);
            speaker.start();
            //sound card ready
            for (;;) { 
//siklus tak terbatas ini memeriksa paket baru untuk dimainkan dalam antrian, dan memutarnya. 
//untuk menghindari kesibukan menunggu, sleep (10) dijalankan di awal setiap iterasi
                if (queue.isEmpty()) { //tidak ada yang bisa dimainkan, menunngu
                    Utils.sleep(10);
                    continue;
                } else {//mendapatkan sesuatu untuk dimulai
                    lastPacketTime = System.nanoTime();
                    Message in = queue.get(0);
                    queue.remove(in);
                    if (in.getData() instanceof SoundPacket) { // paket suara, dirimkan ke kartu suara
                        SoundPacket m = (SoundPacket) (in.getData());
                        if (m.getData() == null) {//pengirim melakukan skip paket, memutar suara dengan baik
                            byte[] noise = new byte[lastSoundPacketLen];
                            for (int i = 0; i < noise.length; i++) {
                                noise[i] = (byte) ((Math.random() * 3) - 1);
                            }
                            speaker.write(noise, 0, noise.length);
                        } else {
                            //decompress data / pengurangan data
                            GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(m.getData()));
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            for (;;) {
                                int b = gis.read();
                                if (b == -1) {
                                    break;
                                } else {
                                    baos.write((byte) b);
                                }
                            }
                            //play decompressed data
                            byte[] toPlay=baos.toByteArray();
                            speaker.write(toPlay, 0, toPlay.length);
                            lastSoundPacketLen = m.getData().length;
                        }
                    } else { //tidak ada suara (sampah)
                        continue; //pesan tidak valid
                    }
                }
            }
        } catch (Exception e) { //kesalahan kartu suara atau kesalahan koneksi, stop
            System.out.println("receiverThread " + chId + " error: " + e.toString());
            if (speaker != null) {
                speaker.close();
            }
            stop();
        }
    }
}