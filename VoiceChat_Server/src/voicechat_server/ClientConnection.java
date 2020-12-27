package voicechat_server;

import voicechat_comm.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

/*
 *
 * @author ASUS
 */
public class ClientConnection extends Thread {

    private Server serv; // instance dari server, diperlukan untuk meletakkan pesan di antrian broadcast server
    private Socket s; // koneksi ke klien 
    private ObjectInputStream in; // objek Stream untuk / dari klien
    private ObjectOutputStream out;
    private long chId; // id unik klien ini, dibuat di konstruktor
    private ArrayList<Message> toSend = new ArrayList<Message>(); // antrian pesan untuk dikirim ke klien

    public InetAddress getInetAddress() { // mengembalikan ip address klien 
        return s.getInetAddress();
    }

    public int getPort() { // mengembalikan port tcp klien 
        return s.getPort();
    }

    public long getChId() { // mengembalikan id unik klien 
        return chId;
    }

    public ClientConnection(Server serv, Socket s) {
        this.serv = serv;
        this.s = s;
        byte[] addr = s.getInetAddress().getAddress();
        chId = (addr[0] << 48 | addr[1] << 32 | addr[2] << 24 | addr[3] << 16) + s.getPort(); //generate chId unik dari IP dan port klien
    }

    public void addToQueue(Message m) { //chId unik dari IP dan port klien
        try {
            toSend.add(m);
        } catch (Throwable t) {
            // mutex error, abaikan karena server harus secepat mungkin
        }
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(s.getOutputStream()); //buat object streams untuk / dari klien
            in = new ObjectInputStream(s.getInputStream());
        } catch (IOException ex) { //koneksi error, tutup koneksi
            try {
                s.close();
                Log.add("ERROR " + getInetAddress() + ":" + getPort() + " " + ex);
            } catch (IOException ex1) {
            }
            stop();
        }
        for (;;) {
            try {
                if (s.getInputStream().available() > 0) { // kita mendapat sesuatu dari klien
                    Message toBroadcast = (Message) in.readObject(); //membaca data dari klien
                    if (toBroadcast.getChId() == -1) { //atur chId dan timestamp-nya dan berikan ke server
                        toBroadcast.setChId(chId);
                        toBroadcast.setTimestamp(System.nanoTime() / 1000000L);
                        serv.addToBroadcastQueue(toBroadcast);
                    } else {
                        continue; //pesan tidak valid
                    }
                }
                try {
                    if (!toSend.isEmpty()) {
                        Message toClient = toSend.get(0); // kita punya sesuatu untuk dikirim ke klien
                        if (!(toClient.getData() instanceof SoundPacket) || toClient.getTimestamp() + toClient.getTtl() < System.nanoTime() / 1000000L) { //is the message too old or of an unknown type?
                            Log.add("dropping packet from " + toClient.getChId() + " to " + chId);
                            continue;
                        }
                        out.writeObject(toClient); // kirim pesan
                        toSend.remove(toClient); // dan hapus dari antrian
                    } else {
                        Utils.sleep(10); //avoid busy wait (hindari menunggu sibuk)
                    }
                } catch (Throwable t) {
                    if (t instanceof IOException) {// koneksi terputus atau koneksi error
                        throw (Exception) t;
                    } else {//mutex error, coba lagi
                        System.out.println("cc fixmutex");
                        continue;
                    }
                }
            } catch (Exception ex) { // koneksi terputus atau kesalahan koneksi, matikan thread
                try {
                    s.close();
                } catch (IOException ex1) {
                }
                stop();
            }
        }

    }
}