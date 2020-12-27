package voicechat_comm;

import java.io.Serializable;
import javax.sound.sampled.AudioFormat;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * some sound
 * @author ASUS
 */
public class SoundPacket implements Serializable{
    public static AudioFormat defaultFormat=new AudioFormat(11025f, 8, 1, true, true); //11.025khz, 8bit, mono, signed, big endian (tidak mengubah apa pun dalam 8 bit) ~8kb/s
    public static int defaultDataLenght=1200; //mengirim 1200 sampel / paket secara default
    private byte[] data; //data aktual. jika null, suara kenyamanan akan dimainkan

    public SoundPacket(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }   
}
