package voicechat_client;

import voicechat_comm.*;
import voicechat_server.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */

/**
 * @author ASUS
 */

//Menghubungkan ke server, kemudian mulai menerima pesan.
//Membuat MicThread yang mengirimkan data mikrofon ke server, dan membuat instance AudioThread untuk setiap pengguna.

public class Client extends Thread {

    private Socket s;
    private ArrayList<AudioChannel> chs = new ArrayList<AudioChannel>();
    private MicThread st;

    public Client(String serverIp, int serverPort) throws UnknownHostException, IOException {
        s = new Socket(serverIp, serverPort);
    }

    @Override
    public void run() {
        try {
            ObjectInputStream fromServer = new ObjectInputStream(s.getInputStream());  //membuat aliran objek dengan server
            ObjectOutputStream toServer = new ObjectOutputStream(s.getOutputStream());
            try {
                Utils.sleep(100); //tunggu tes mikrofon GUI untuk melepaskan mikrofon
                st = new MicThread(toServer);  //membuat MicThread yang mengirimkan data mikrofon ke server
                st.start(); //memulai MicThread
            } catch (Exception e) { //kesalahan mendapatkan mikrofon. penyebab: mikrofon atau mikrofon tidak sibuk
                System.out.println("mic unavailable " + e);
            }
            for (;;) { //siklus tak terbatas ini memeriksa data baru dari server, lalu mengirimkannya ke Saluran Audio yang benar. jika perlu, Saluran Audio baru dibuat
                
                if (s.getInputStream().available() > 0) { //kami mendapat sesuatu dari server (solusi: menggunakan metode yang tersedia dari InputStream alih-alih yang dari ObjetInputStream karena bug di JRE)
                    Message in = (Message) (fromServer.readObject()); //membaca pesan
                    //putuskan saluran audio mana yang akan menerima pesan ini
                    AudioChannel sendTo = null; 
                    for (AudioChannel ch : chs) {
                        if (ch.getChId() == in.getChId()) {
                            sendTo = ch;
                        }
                    }
                    if (sendTo != null) {
                        sendTo.addToQueue(in);
                    } else { //AudioChannel baru diperlukan
                        AudioChannel ch = new AudioChannel(in.getChId());
                        ch.addToQueue(in);
                        ch.start();
                        chs.add(ch);
                    }
                }else{ //lihat apakah beberapa saluran perlu dimatikan dan dimatikan
                    ArrayList<AudioChannel> killMe=new ArrayList<AudioChannel>();
                    for(AudioChannel c:chs) if(c.canKill()) killMe.add(c);
                    for(AudioChannel c:killMe){c.closeAndKill(); chs.remove(c);}
                    Utils.sleep(1); //avoid busy wait
                }
            }
        } catch (Exception e) { //connection error
            System.out.println("client err " + e.toString());
        }
    }
}