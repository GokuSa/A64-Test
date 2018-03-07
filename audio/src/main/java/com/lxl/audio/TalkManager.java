package com.lxl.audio;


import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * 增辉WebRtc,由底层talk 进程 执行音频采集 发送 接收 播放 等相关一切操作
 * 上层应用仅负责开启 关闭，异常处理
 * 通话类型为通过服务器转发，也有点对点 启动参数不一样
 * 启动talk前，先发送停止命令，防止重复调用导致多次连接
 * 启动后，通过UDP通信，开始监听底层talk反馈，
 * 不论是talk意外退出还是上层应用主动让talk退出，都返回无差别quit消息，上层应用需区分并负责意外退出时重新启动
 * <p>
 *
 * 如果是USB麦克风  talk进程和android应用占用声卡问题，无法播放音频，
 * 解决方案：底层修改声卡使用，在使用talk进程之前，禁止android程序使用；talk退出，恢复声卡使用
 * 需要添加 <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>权限
 * <p>
 * <p>
 * 切换声卡导致子码流失常问题->使用talk完成混音和转发两个功能，不再切换声卡的使用
 * <p>
 * 注意 如果传给talk的地址和端口不正确，目前无法获取启动结果
 * <p>
 * 需要根据屏幕尺寸判断声卡是板载的还是USB麦克风
 */

public class TalkManager {
    private static final String TAG = TalkManager.class.getSimpleName();
    /**
     * 麦克风设备
     */
    private static final int DEVICE_MIC = 1;
    /**
     * 共达设备
     */
    private static final int DEVICE_GONGDA = 2;
    /**
     * 没有音频输入设备
     */
    private static final int DEVICE_NONE = 3;
    /**
     * 调节声卡占用问题
     */
    private final AudioManager mAudioManager;
    //对UDP端口12310的订阅
    private Disposable mDisposable;
    /**
     * 应用主动停止talk进程
     */
    private volatile boolean stopByCustom = false;
    /**
     * talk是否已启用
     */
    private volatile boolean isTalking = false;
    /**
     * 启动talk进程的命令行工具
     */
    private RootCommand mRootCommand;
    /**
     * 当前音频输入设备代号
     */
    private int mVoiceDeviceType;
    /**
     * 监听12310UDP端口与talk通讯的socket
     */
    private DatagramSocket mSocket;
    /**
     * 连接服务器的地址和端口
     */
    private String mServerIp;
    private String mServerPort;

    //    private VolumeListener mVolumeListener;
    private boolean mCanTalk;
    private boolean isMic=true;

    public TalkManager() {
//        mVoiceDeviceType = judgeVoiceInputType();
//        getAudioDeviceType();
        mAudioManager = (AudioManager) App.getInstance().getSystemService(Context.AUDIO_SERVICE);
        mRootCommand = new RootCommand();
    }

    /**
     * 是否使用的外置USB MIC（1920*X） 还是板载的声卡（1600*X）
     * 宽度为1920的使用usb 麦克风，启动talk的命令不一样
     * 板载的不需要关闭声卡
     */
    private void getAudioDeviceType() {
        DisplayMetrics dm = App.getInstance().getResources().getDisplayMetrics();
        isMic = 1920 == dm.widthPixels;
        //
    }


    public void setMic(boolean mic) {
        isMic = mic;
    }

    /**
     * 主要是开启UDP端口12310监听talk进程发送的退出消息，还有音频数据来动态显示声音波动
     * 在oncreate时调用，
     */
    public void initialize() {
        if (mDisposable != null) {
            return;
        }
        mDisposable = Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {
                // 监听12310端口，接受talk的通知
                Log.d(TAG, "start listen n 12310");
                mSocket = new DatagramSocket(12310, InetAddress.getLocalHost());
                //如果程序异常退出，talk可能没退出，先发送关闭进程命令
                stopTalkProcess();
                // 开始监听talk 的通知，talk进程退出前发quit消息,不论是意外退出还是上层命令退出，所以要区分
                byte[] buffer = new byte[64];
                DatagramPacket datagramPacketReceive = new DatagramPacket(buffer, 0, buffer.length);
                //取消订阅，接到消息receive不再阻塞才会退出循环
                while (!e.isDisposed()) {
//                    Log.d(TAG, "start receive");
                    //阻塞
                    mSocket.receive(datagramPacketReceive);
                    String result = new String(buffer).trim();
                    //talk退出前发送的信息
                    if ("quit".equals(result)) {
                        Log.d(TAG, "get notification quit");
                        //如果是上层主动关闭忽略quit信息,并设置stopByCustom为false，应对意外退出
                        if (stopByCustom) {
                            stopByCustom = false;
                        } else {
                            e.onNext(result);
                        }
                    } /*else if (result.startsWith("vol")&&mCanTalk) {
                        //提取声音波形，格式为vol=xxx
                        String vol = result.substring(4, result.length());
                        if (mVolumeListener != null) {
                            mVolumeListener.onVolume(Integer.parseInt(vol));
                        }
                    }*/
                }
                Log.d(TAG, "end of listening");
                stopTalkProcess();
//                关闭socket，停止与talk服务的通信
                mSocket.close();
                Log.d(TAG, "finish talk");
            }
        }).subscribeOn(Schedulers.io())
                .flatMap(new Function<String, ObservableSource<?>>() {
                    @Override
                    public ObservableSource<?> apply(@NonNull String s) throws Exception {
//                        talk 退出，接到通知后5秒重新启动talk
                        Log.d(TAG, "start 5 seconds later");
                        return Observable.timer(5, TimeUnit.SECONDS);
                    }
                }).subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(@NonNull Object o) throws Exception {
//                        需要判断声卡是否存在，否则重启后 talk不编码，导致关闭不了，主动退出不启动
                        isTalking = false;
                        startTalkProcess(mServerIp, mServerPort, mCanTalk);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        Log.d(TAG, "throwable:" + throwable.toString());
                    }
                });

    }


  /*  public void setVolumeListener(VolumeListener volumeListener) {
        mVolumeListener = volumeListener;
    }*/

    /**
     * 启动talk进程
     *
     * @param serverIp
     * @param serverPort canTalk 是否能说话，就是没有关闭mic
     *                   isMic，是否使用的USB麦克风  ，和板载声卡启动命令参数不一样
     */
    public void startTalkProcess(String serverIp, String serverPort, boolean canTalk) throws IOException {
        checkThread("启动talk进程需要在子线程中调用");
        //修改当前麦克风状态，CanTalk能说能听，否则静音；
        mCanTalk = canTalk;
        if (isTalking) {
            Log.d(TAG, "talk is starting");
            return;
        }
        isTalking = true;
        //每次主动停止talk进程都会设置此值为true，在接到talk返回的quit后设置为false，
        // 但如果talk不存在没有返回quit信息，将导致此值一直为true，即不能响应意外退出，所以在此处设为默认值false
        stopByCustom = false;
        //重启talk进程使用
        mServerIp = serverIp;
        mServerPort = serverPort;
        String commmand = "";
        if (isMic) {
            //USB麦克风声卡
            commmand = String.format(Locale.CHINA, "talk -m %s:%s -ic 2 -il 1 &", serverIp, serverPort);
        } else {
            //板载声卡
            commmand = String.format(Locale.CHINA, "talk -m %s:%s -ic 0 -il 1 &", serverIp, serverPort);
        }
        //MIC需要强制禁用android声卡，让talk进程使用
        if (isMic) {
            mAudioManager.setParameters("ForceCloseCard0Dev0");
        }
        //启动talk进程，command中含&，加&是把命令交给linux内核去运行一个进程任务，在后台运行
        Log.d(TAG, "startTalkProcess " + commmand);
        mRootCommand.executeCommands(commmand);

    }


    public void startTalkProcess(String command) throws IOException {
        checkThread("启动talk进程需要在子线程中调用");
        //修改当前麦克风状态，CanTalk能说能听，否则静音；
        mCanTalk = true;
        if (isTalking) {
            Log.d(TAG, "talk is starting");
            return;
        }
        isTalking = true;
        //每次主动停止talk进程都会设置此值为true，在接到talk返回的quit后设置为false，
        // 但如果talk不存在没有返回quit信息，将导致此值一直为true，即不能响应意外退出，所以在此处设为默认值false
        stopByCustom = false;
        //重启talk进程使用

        //MIC需要强制禁用android声卡，让talk进程使用
        if (isMic) {
            mAudioManager.setParameters("ForceCloseCard0Dev0");
        }
        //启动talk进程，command中含&，加&是把命令交给linux内核去运行一个进程任务，在后台运行
        Log.d(TAG, "startTalkProcess " + command);
        mRootCommand.executeCommands(command);

    }


    /**
     * 退出talk进程
     * 只要不exit，还能重新启动
     *
     * @throws IOException
     */
    public void stopTalkProcess() throws IOException {
        checkThread("关闭talk进程需要在子线程中调用");
        Log.d(TAG, "stopTalkProcess() called");
        if (mSocket != null) {
            stopByCustom = true;
            isTalking = false;
            String send = "quit";
            InetAddress localHost = InetAddress.getLocalHost();
            DatagramPacket datagramPacket = new DatagramPacket(send.getBytes(), send.length(), localHost, 12300);
            mSocket.send(datagramPacket);
            // 强制打卡android声卡
            if (isMic) {
                mAudioManager.setParameters("ForcOpenCard0Dev0");
            }
        } else {
            Log.d(TAG, "socket is null");
        }
    }

    private void checkThread(String info) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            throw new IllegalStateException(info);
        }
    }


    /**
     * 关闭talk进程，退出talk通讯监听
     * 一般在页面销毁时条用
     */
    public void exit() {
        Log.d(TAG, "exit() called");
        Observable.just("").map(new Function<Object, Object>() {
            @Override
            public Object apply(@NonNull Object o) throws Exception {
               /* if (mVolumeListener != null) {
                    mVolumeListener=null;
                }*/
                if (mDisposable != null) {
                    mDisposable.dispose();
                    mDisposable = null;
                }
//                由于UDP接受是传统的阻塞式，dispose后不能退出，所以模拟talk发退出命令
                stopListen(12310);
                mRootCommand.close();
                return "";
            }
        }).subscribeOn(Schedulers.io())
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(@NonNull Object o) throws Exception {
                        Log.d(TAG, "exit on ");
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        Log.d(TAG, throwable.toString());
                    }
                });
    }


    /**
     * 必须先mDisposable.dispose()
     * 发送命令给12310端口，让其从阻塞恢复，退出监听
     * 如果不想配合mDisposable.dispose()使用，在循环中自定义处理接受到的exit信息
     *
     * @param port
     * @throws IOException
     */
    private void stopListen(int port) throws IOException {
        Log.d(TAG, "stopListen");
        checkThread("必须在子线程发送关闭命令");
        DatagramSocket socket = new DatagramSocket();
        String send = "exit";
        InetAddress localHost = InetAddress.getLocalHost();
        DatagramPacket datagramPacket = new DatagramPacket(send.getBytes(), send.length(), localHost, port);
        socket.send(datagramPacket);
        socket.close();
    }


    //根据硬件producetId判断输入设备类型
    private int judgeVoiceInputType() {
        int type = DEVICE_NONE;
//        从本地配置获取指定麦克风的产品Id
        File file = new File("/data/work/show/system/mic_config.ini");
        int micProductId = 0x0014;
        if (file.exists() && file.canRead()) {
            try {
                Properties properties = new Properties();
                properties.load(new FileInputStream(file));
                String productId = properties.getProperty("productId", "0014");
                micProductId = Integer.parseInt(productId, 16);
                Log.d(TAG, "micProductId:" + micProductId);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        UsbManager usbManager = (UsbManager) App.getInstance().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Collection<UsbDevice> values = deviceList.values();
        for (UsbDevice value : values) {
            if (micProductId == value.getProductId()) {
                Log.d(TAG, "当前使用麦克风");
                type = DEVICE_MIC;
                break;
            } else if (0x0132 == value.getProductId()) {
//                如果找到共达还继续需找看有没有麦克风
                type = DEVICE_GONGDA;
                Log.d(TAG, "当前使用共达");
            }
        }
        return type;
    }


    public int getVoiceDeviceType() {
        return mVoiceDeviceType;
    }
}
