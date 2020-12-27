package voicechat_server;

import voicechat_comm.*;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.support.igd.PortMappingListener;
import org.teleal.cling.support.model.PortMapping;

/**
 *
 * @author ASUS
 */
public class Server {
    
    private ArrayList<Message> broadCastQueue = new ArrayList<Message>();    
    private ArrayList<ClientConnection> clients = new ArrayList<ClientConnection>();
    private int port;
    
    private UpnpService u; //ketika upnp diaktifkan/dicentang, variabel ini akan menunjuk pada upnp service
    
    public void addToBroadcastQueue(Message m) { //menambah pesan ke broadcast queue. method ini digunakan oleh semua yang menginstasiasi ClientConnection
        try {
            broadCastQueue.add(m);
        } catch (Throwable t) {
            //jika mutex error, maka akan dicoba lagi
            Utils.sleep(1);
            addToBroadcastQueue(m);
        }
    }
    private ServerSocket s;
    
    public Server(int port, boolean upnp) throws Exception{
        this.port = port;
        if(upnp){
            Log.add("Setting up NAT Port Forwarding...");
            //pertama kita memerlukan address dari PC yang tersambung dengan local network
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            } catch (SocketException ex) {
                Log.add("Network error");
                throw new Exception("Network error");
            }
            String ipAddress = null;
            Enumeration<NetworkInterface> net = null;
            try {
                net = NetworkInterface.getNetworkInterfaces();
            } catch (SocketException e) {
                Log.add("Not connected to any network");
                throw new Exception("Network error");
            }

            while (net.hasMoreElements()) {
                NetworkInterface element = net.nextElement();
                Enumeration<InetAddress> addresses = element.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
                    if (ip instanceof Inet4Address) {
                        if (ip.isSiteLocalAddress()) {
                            ipAddress = ip.getHostAddress();
                            break;
                        }
                    }
                }
                if (ipAddress != null) {
                    break;
                }
            }
            if (ipAddress == null) {
                Log.add("Not connected to any IPv4 network");
                throw new Exception("Network error");
            }
            u = new UpnpServiceImpl(new PortMappingListener(new PortMapping(port, ipAddress, PortMapping.Protocol.TCP)));
            u.getControlPoint().search();
        }
        try {
            s = new ServerSocket(port); //mendengarkan dengan menentukan port tertentu
            Log.add("Port " + port + ": server started");
        } catch (IOException ex) {
            Log.add("Server error " + ex + "(port " + port + ")");
            throw new Exception("Error "+ex);
        }
        new BroadcastThread().start(); //membuat BroadcastThread dan memulai
        for (;;) { //looping untuk menerima semua koneksi yang connect
            try {
                Socket c = s.accept();
                ClientConnection cc = new ClientConnection(this, c); //create a ClientConnection thread
                cc.start();
                addToClients(cc);
                Log.add("new client " + c.getInetAddress() + ":" + c.getPort() + " on port " + port);
            } catch (IOException ex) {
            }
        }
    }

    private void addToClients(ClientConnection cc) {
        try {
            clients.add(cc); //menambah koneksi baru ke daftar koneksi
        } catch (Throwable t) {
            //mutex error, dicoba lagi
            Utils.sleep(1);
            addToClients(cc);
        }
    }

    /**
     * menyampaikan pesan ke setiap ClientConnection, dan menghapus yang sudah tidak connect
     */
    private class BroadcastThread extends Thread {
        
        public BroadcastThread() {
        }
        
        @Override
        public void run() {
            for (;;) {
                try {
                    ArrayList<ClientConnection> toRemove = new ArrayList<ClientConnection>(); //create a list of dead connections
                    for (ClientConnection cc : clients) {
                        if (!cc.isAlive()) { //jika koneksi mati, perlu diremoved
                            Log.add("dead connection closed: " + cc.getInetAddress() + ":" + cc.getPort() + " on port " + port);
                            toRemove.add(cc);
                        }
                    }
                    clients.removeAll(toRemove); //menghapus semua koneksi yang mati
                    if (broadCastQueue.isEmpty()) { //tidak ada yang bisa dikirim
                        Utils.sleep(10); //menghindari lag
                        continue;
                    } else { //ada yang bisa dikirim
                        Message m = broadCastQueue.get(0);
                        for (ClientConnection cc : clients) { //broadcast message
                            if (cc.getChId() != m.getChId()) {
                                cc.addToQueue(m);
                            }
                        }
                        broadCastQueue.remove(m); //hapus dari the broadcast queue
                    }
                } catch (Throwable t) {
                    //mutex error
                }
            }
        }
    }
}
