package shine.com.test.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.shine.timingboot.TimingBootUtils;
import com.shine.utilitylib.A64Utility;

/**
 * 长期运行的后台服务
 * 使用HandlerThread 在子线程处理任务，
 * 使用Handler可以延时处理任务
 * IntentService不能延时处理？？？
 *
 */
public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String PARAM1 = "param1";
    private static final String PARAM2 = "param2";
    /**
     * 关屏
     */
    public static final String ACTION_CLOSE_SCREEN = "action_close_screen";
    public static final int CLOSE_SCREEN = 1;
    /**
     * 开屏
     */
    public static final String ACTION_OPEN_SCREEN = "action_open_screen";
    public static final int OPEN_SCREEN = 2;
    /**
     * 关机
     */
    public static final String ACTION_SHUTDOWN = "action_shutdown";
    public static final int SHUTDOWN = 3;
    /**
     * 开机
     */
    public static final String  ACTION_START = "action_start";
    public static final int START = 4;

    public static final String ACTION_GRANT = "grant";
    private static final int GRANT = 5;

    private volatile Looper mLooper;
    private volatile BackgroundHandler mHandler;

    public BackgroundService() {}

    public static void start(Context context, String action,int param1, String param2) {
        Intent intent = new Intent(context,BackgroundService.class);
        intent.setAction(action);
        intent.putExtra(PARAM1, param1);
        intent.putExtra(PARAM2, param2);
        context.startService(intent);
    }
    public static void start(Context context,String action) {
        Intent intent = new Intent(context,BackgroundService.class);
        intent.setAction(action);
        context.startService(intent);
    }

    private final class BackgroundHandler extends Handler {
         BackgroundHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CLOSE_SCREEN:
                    new A64Utility().CloseScreen();
                    break;
                case OPEN_SCREEN:
                    new A64Utility().OpenScreen();
                    break;
                case SHUTDOWN:
                    new A64Utility().Shutdown();
                    break;

            }
        }
    }




    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread handlerThread = new HandlerThread("worker");
        handlerThread.start();
        mLooper = handlerThread.getLooper();
        mHandler = new BackgroundHandler(mLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        switch (action) {
            case ACTION_CLOSE_SCREEN:{
                int delay = intent.getIntExtra(PARAM1, -1);
                if (delay >= 0) {
                    mHandler.sendEmptyMessageDelayed(CLOSE_SCREEN,delay);
                }
            }
            break;
            case ACTION_OPEN_SCREEN: {
                int delay = intent.getIntExtra(PARAM1, -1);
                if (delay > 0) {
                    mHandler.sendEmptyMessageDelayed(OPEN_SCREEN, delay);
                }
            }
                break;
            case ACTION_SHUTDOWN:{
                int delay = intent.getIntExtra(PARAM1, -1);
                if (delay >= 0) {
                    mHandler.sendEmptyMessageDelayed(SHUTDOWN, delay);
                }
            }
                break;
            case ACTION_START:
                String start = intent.getStringExtra(PARAM2);
                Log.d(TAG, start);
                int result = new TimingBootUtils().setRtcTime(start);
                Log.d(TAG, "result:" + result);
                break;

        }


        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called");
        mLooper.quit();
    }
}
