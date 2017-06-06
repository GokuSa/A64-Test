package shine.com.test.activity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import android_serialport_api.SerialPort;
import butterknife.ButterKnife;
import butterknife.OnClick;
import shine.com.test.R;
import shine.com.test.utils.Common;
import shine.com.test.utils.RootCommand;

/**
 * 调试串口的演示
 * 因为要获取超级用户权限给串口授权 所以清单文件要有Internet权限
 * 如果是系统签名 在system/lib 和system/lib64 要有串口库
 */
public class SerialActivity extends AppCompatActivity implements Handler.Callback {
    private static final String TAG = "SerialActivity";
    private Handler mHandler;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private SerialPort mSerialPort;
    private String[] mInstructions;


    @Override
    public boolean handleMessage(Message message) {
        switch (message.what) {
            case 0:
                RootCommand rootCommand = new RootCommand();
                boolean result = rootCommand.executeCommand("chmod 666 " + (String)message.obj);
                //result 只表示是否正确执行命令 不表示授权结果
                Log.d(TAG, "result:" + result);
                mHandler.sendEmptyMessage(1);
                break;
            case 1:
                judge("/dev/ttyS4", false);
                break;
            case 2:
                getDataFromSerial();
                break;
            case 3:
                send(message.arg1);
                break;
            case 4:
                turnOffScan();
                break;
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_serial);
        ButterKnife.bind(this);
        mInstructions = getResources().getStringArray(R.array.instructions);
        HandlerThread handlerThread = new HandlerThread("serial");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper(), this);
        judge("/dev/ttyS4", true);

    }

    /**
     * 从串口读取返回的数据
     * 在此项目中可以直接忽略，但有些项目需要处理返回的数据
     * 每读一个数据就调用自身 如果没有数据读取将堵塞
     * 如果在handlerThread中使用，注意会堵塞其后的信息
     */
    private void getDataFromSerial() {
        int size;
        try {
            byte[] buffer = new byte[64];

            if (mInputStream == null) return;
            //阻塞式的 如果没有消息将一直阻塞，此时往handler发message将无法执行
            size = mInputStream.read(buffer);
            if (size > 0) {
                String s1 = Arrays.toString(buffer);
                Log.d(TAG, s1);

            }
            mHandler.sendEmptyMessage(2);
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    private void send(int i) {
        try {
            if (mOutputStream != null) {
                mOutputStream.write(Common.hex2Bytes(mInstructions[i]));
                mOutputStream.write('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void turnOffScan() {
        Log.d(TAG, "turnOffScan() called");
        try {
            if (mOutputStream != null) {
                mOutputStream.write("NLS0006010;".getBytes());
                //无照明
                mOutputStream.write("NLS0200020;".getBytes());
                //无瞄准
                mOutputStream.write("NLS0201020;".getBytes());
                mOutputStream.write("NLS0006000;".getBytes());
                mOutputStream.flush();
                Log.d(TAG, "write already");
            }
        } catch (IOException e) {
            Log.d(TAG, "写失败");
        }
    }

    private void judge(String fileName, boolean requireGrant) {
        File file = new File(fileName);
        if (!file.exists()) {
            Log.d(TAG, "串口不存在");
            return;
        }
        if (file.canRead() && file.canWrite()) {
            mSerialPort = new SerialPort(file, 9600, 0);
            mOutputStream = mSerialPort.getOutputStream();
            mInputStream = mSerialPort.getInputStream();
            if (null != mOutputStream && null != mInputStream) {
                //开始监听串口发送的数据
                mHandler.sendEmptyMessage(2);
            }
        } else {
            //如果文件还没权限,并且请求授权
            if (requireGrant) {
                mHandler.obtainMessage(0,fileName).sendToTarget();
            }
        }
    }


    @OnClick({R.id.btn_back, R.id.btn_send_instruction,R.id.btn_clsoe_light})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_back:
                finish();
                break;
            case R.id.btn_send_instruction:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setSingleChoiceItems(R.array.lights, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.d(TAG, "i:" + i);
                        send(i);
                        dialogInterface.dismiss();
                    }
                });
                builder.setTitle("选择指令");
                builder.show();
                break;
            case R.id.btn_clsoe_light:
                Log.d(TAG, "clcik");
//                mHandler.sendEmptyMessage(4);
                new Thread(){
                    @Override
                    public void run() {
                        turnOffScan();
                    }
                }.start();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called");
        mHandler.getLooper().quit();
        if (mSerialPort != null) {
            mSerialPort.close();
        }

    }


}
