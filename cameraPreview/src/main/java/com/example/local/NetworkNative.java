package com.example.local;

/**
 * Created by 123 on 2017/5/26.
 */

public class NetworkNative {

    static {
        System.loadLibrary("sendFrameInterface");
    }

    public native int CloseSocket();
    public native int OpenSocket();

    //true值为1,false值为0
    public native int SendFrame(byte[] b, int frameLenth, int isKeyFrame);
}
