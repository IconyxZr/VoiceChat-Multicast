package voicechat_client;

import voicechat_comm.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPOutputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * @author ASUS
 */

//membaca data dari mikrofon dan mengirimkannya ke server

public class MicThread extends Thread {

    public static double amplification = 1.0;
    private ObjectOutputStream toServer;
    private TargetDataLine mic;

    public MicThread(ObjectOutputStream toServer) throws LineUnavailableException {
        this.toServer = toServer;
        //open microphone line, pengecualian dilemparkan jika terjadi kesalahan
        AudioFormat af = SoundPacket.defaultFormat;
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, null);
        mic = (TargetDataLine) (AudioSystem.getLine(info));
        mic.open(af);
        mic.start();
    }

    @Override
    public void run() {
        for (;;) {
            if (mic.available() >= SoundPacket.defaultDataLenght) { //kami mendapat cukup data untuk dikirim
                byte[] buff = new byte[SoundPacket.defaultDataLenght];
                while (mic.available() >= SoundPacket.defaultDataLenght) { //hapus data lama dari mikrofon untuk mengurangi kelambatan, dan membaca data terbaru
                    mic.read(buff, 0, buff.length); //membaca dari mikrofon
                }
                try {
                    //bagian ini digunakan untuk memutuskan apakah akan mengirim paket atau tidak. jika volume terlalu rendah, paket kosong akan dikirim dan klien jarak jauh akan memutar suara kenyamanan
                    long tot = 0;
                    for (int i = 0; i < buff.length; i++) {
                        buff[i] *= amplification;
                        tot += Math.abs(buff[i]);
                    }
                    tot *= 2.5;
                    tot /= buff.length;
                    //membuat dan mengirim paket
                    Message m = null;
                    if (tot == 0) {//kirim paket kosong
                        m = new Message(-1, -1, new SoundPacket(null));
                    } else { //send data
                        //kompres paket suara dengan GZIP
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        GZIPOutputStream go = new GZIPOutputStream(baos);
                        go.write(buff);
                        go.flush();
                        go.close();
                        baos.flush();
                        baos.close();
                        //buat pesan untuk server, akan menghasilkan chId dan timestamp dari IP komputer ini dan port soket ini
                        m = new Message(-1, -1, new SoundPacket(baos.toByteArray()));
                    }
                    toServer.writeObject(m); //send message
                } catch (IOException ex) { //connection error
                    stop();
                }
            } else {
                Utils.sleep(10); //sleep to avoid busy wait
            }
        }
    }
}