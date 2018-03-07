package com.lxl.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.databinding.ObservableField;
import android.preference.PreferenceManager;
import android.support.annotation.IdRes;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RadioGroup;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * author:
 * 时间:2017/6/5
 * qq:1220289215
 * 类描述：音频通讯页面的视图模型，对页面的数据进行管理
 */

public class AudioViewModel {
    private static final String TAG = AudioViewModel.class.getSimpleName();
    private static final String KEY_COMMAND = "command";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String KEY_SERVER_PORT = "server_port";
    //双向绑定
    public final ObservableField<String> mServerIp = new ObservableField<>();
    public final ObservableField<String> mServerPort = new ObservableField<>();
    public final ObservableField<String> mCommand = new ObservableField<>();
    private TalkManager mTalkManager = new TalkManager();
    private SharedPreferences mSharedPreferences;
    private Context mContext;

    public AudioViewModel(Context context) {
        if (context == null) {
            throw new NullPointerException("context can not be null");
        }
        mContext = context;
        mTalkManager.initialize();
        populateUI();
    }

    private void populateUI() {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String command = mSharedPreferences.getString(KEY_COMMAND, "talk -p 7000 -ic 2 -il 1");
        mCommand.set(command);
        String serverIp = mSharedPreferences.getString(KEY_SERVER_IP, "172.168.1.44");
        mServerIp.set(serverIp);
        String serverPort = mSharedPreferences.getString(KEY_SERVER_PORT, "7000");
        mServerPort.set(serverPort);
    }

    public void startTalk(View view) {
        Log.d(TAG, "connect() called ");
        Observable.create(new ObservableOnSubscribe<Object>() {
            @Override
            public void subscribe(ObservableEmitter<Object> e) throws Exception {
                Log.d(TAG, "subcribe");
                final String serverIp = mServerIp.get();
                final String serverPort = mServerPort.get();
                if (TextUtils.isEmpty(serverIp) || TextUtils.isEmpty(serverPort)) {
                    e.onComplete();
                } else {
                    mTalkManager.startTalkProcess(serverIp, serverPort, true);
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(@NonNull Object o) throws Exception {
                        Log.d(TAG, "accept() called with: o = [" + o + "]");
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        Log.d(TAG, throwable.toString());
                    }
                }, new Action() {
                    @Override
                    public void run() throws Exception {
                        Log.d(TAG, "服务器地址和端口不能为空");
                    }
                });
    }

    public void startDirectTalk(View view) {
        Log.d(TAG, "connect() called ");
        Observable.create(new ObservableOnSubscribe<Object>() {
            @Override
            public void subscribe(ObservableEmitter<Object> e) throws Exception {
                Log.d(TAG, "subcribe");
                final String command = mCommand.get();
                if (TextUtils.isEmpty(command)) {
                    e.onComplete();
                } else {
                    mTalkManager.startTalkProcess(command);
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(@NonNull Object o) throws Exception {
                        Log.d(TAG, "accept() called with: o = [" + o + "]");
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        Log.d(TAG, throwable.toString());
                    }
                }, new Action() {
                    @Override
                    public void run() throws Exception {
                        Log.d(TAG, "服务器地址和端口不能为空");
                    }
                });
    }

    public void stopTalk(View view) {
        Log.d(TAG, "disconnect() called with:");
        Observable.just("").subscribeOn(Schedulers.io())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(@NonNull String s) throws Exception {
                        mTalkManager.stopTalkProcess();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        Log.d(TAG, throwable.toString());
                    }
                });
    }

    public void exit() {
        mTalkManager.exit();
        mSharedPreferences.edit()
                .putString(KEY_COMMAND, mCommand.get())
                .putString(KEY_SERVER_IP,mServerIp.get())
                .putString(KEY_SERVER_PORT,mServerPort.get())
                .apply();
    }

    public void onCheckedChange(RadioGroup group, @IdRes int checkedId) {
        if (checkedId == R.id.rb_mic) {
            Log.d(TAG, "mic");
            mTalkManager.setMic(true);
        } else {
            mTalkManager.setMic(false);
        }
    }
}
