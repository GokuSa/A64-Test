package com.shine.timingboot;

/**
 * 设置开机时间 格式为 "yyyy-MM-dd HH:mm:ss"
 * A64 设备 需要在/system/lib64 有 相关库
 */
public class TimingBootUtils {
    static {
        System.loadLibrary("jni_rtc");
    }
    public native int setRtcTime(String str);
}

