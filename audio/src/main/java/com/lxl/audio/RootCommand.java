package com.lxl.audio;

import android.os.Looper;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Created by 李晓林 on 2016/11/30
 * qq:1220289215
 * 获取系统权限工具
 * ROM的操作系统和普通手机获取超级权限的方式不一样
 *
 * 系统使用socket开启一个端口监听客户端指令输入，从而执行超级用户权限
 * 这个就是su_server，在开机脚本里启动，
 * 如果这个进程死掉无法连接,可以在shell里通过以下命令启动su_server
 * export CLASSPATH=/extdata/local/tmp/class.jar
 * app_process /extdata/loca/tmp/ cn.shine.suserver.Server &
 *
 * 需要在manifest中加入Internet权限
 *
 */

public class RootCommand {
    private static final String TAG = "RootCommand";
    private static final String IP = "127.0.0.1";
    private static final int PORT = 4757;
    private static final String PWD = "SHINE_USER";
    private static final String NO_ERROR = "0";
    private static final String HEAD_EXEC = "EXEC_CMD|";
    private static final String HEAD_MOVE_FILE = "MOVE_FILE|";
    private DataOutputStream mDataOutputStream;
    private DataInputStream mDataInputStream;
    private Socket mSocket;
    private boolean isConnected=false;
    //获取超级管理员权限
    private boolean connect() {
        try {
            mSocket = new Socket(IP, PORT);
            mDataOutputStream = new DataOutputStream(mSocket.getOutputStream());
            mDataInputStream = new DataInputStream(mSocket.getInputStream());
            mDataOutputStream.writeUTF(PWD);
            isConnected=NO_ERROR.equals(mDataInputStream.readUTF());
            return isConnected;
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
        return false;
    }


    /**
     * 执行多个命令
     * @param commands
     */
    public void executeCommands(String ...commands) {
        checkThread("must be called in non UI Thread");
//        如果还没连接先连接
        if (!isConnected) {
            connect();
        }
//        如果连接成功
        if (isConnected) {
            boolean result=false;
            for (String command : commands) {
                try {
                    mDataOutputStream.writeUTF(HEAD_EXEC + command);
                    result=NO_ERROR.equals(mDataInputStream.readUTF());
                    Log.d(TAG, command+" 执行结果 "+result);
                } catch (IOException e) {
                    Log.d(TAG, command+" 执行中IOException");
                }
            }
        }else{
            Log.d(TAG, "fail to get super authority");
        }

    }

    //socket通信必需在子线程中执行
    private void checkThread(String info) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            throw new IllegalStateException(info);
        }
    }

    //关闭sockt流
    public void close() {
        isConnected=false;
        if (mDataOutputStream != null) {
            try {
                mDataOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mDataInputStream != null) {
            try {
                mDataInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
