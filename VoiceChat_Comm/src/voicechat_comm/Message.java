package voicechat_comm;

import java.io.Serializable;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * container for any type of object to be sent
 * @author ASUS
 */
public class Message implements Serializable{
    private long chId; //-1 berarti dari klien ke server, jika tidak dihasilkan oleh server
    private long timestamp, //-1 berarti dari klien ke server, jika tidak, timeStamp saat server menerima pesan
            ttl=2000; //2 detik TTL
    private Object data; //pembawa data(paket suara)

    
    public Message(long chId, long timestamp, Object data) {
        this.chId = chId;
        this.timestamp = timestamp;
        this.data = data;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getChId() {
        return chId;
    }

    public Object getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getTtl() {
        return ttl;
    }
    public void setTtl(long ttl) {
        this.ttl = ttl;
    }
    
    public void setChId(long chId) {
        this.chId = chId;
    }
    
}