package shine.com.test.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import shine.com.test.R;
import shine.com.test.service.BackgroundService;

/**
 * A64 开关机 开关屏的测试
 * 所调用的类包名要正确，加载的.so库要放在system/lib64下
 */
public class OnOffActivity extends AppCompatActivity {
    private static final String TAG = "OnOffActivity";

    @Bind(R.id.et_shutdown)
    EditText mEtShutdown;
    @Bind(R.id.et_start)
    EditText mEtStart;
    @Bind(R.id.et_close_screen)
    EditText mEtCloseScreen;
    @Bind(R.id.et_open_screen)
    EditText mEtOpenScreen;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onoff);
        ButterKnife.bind(this);
    }

    @OnClick({R.id.btn_close_screen, R.id.btn_shutdown, R.id.btn_back})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_back:
                finish();
                break;
            case R.id.btn_close_screen:
                String closeScreenTime = mEtCloseScreen.getText().toString().trim();
                String openScreenTime = mEtOpenScreen.getText().toString().trim();
                //默认0秒后关屏 10秒后开屏
                int timeCloseScreen = 0;
                int timeOpenScreen = 10;
                if (!TextUtils.isEmpty(closeScreenTime)) {
                    timeCloseScreen = Integer.parseInt(closeScreenTime);
                }
                if (!TextUtils.isEmpty(openScreenTime)) {
                    timeOpenScreen = Integer.parseInt(openScreenTime);
                }
                closeScreen(timeCloseScreen, timeOpenScreen);
                break;
            case R.id.btn_shutdown:
                String shundownTime = mEtShutdown.getText().toString().trim();
                String startTime = mEtStart.getText().toString().trim();
                //默认5秒后关机 60秒后开机
                int timeShutdown = 5;
                int timeStart = 60;
                if (!TextUtils.isEmpty(shundownTime)) {
                    timeShutdown = Integer.parseInt(shundownTime);
                }
                if (!TextUtils.isEmpty(startTime)) {
                    timeStart = Integer.parseInt(startTime);
                }
                shutdown(timeShutdown, timeStart * 1000);
                break;
        }
    }

    /**
     * 使用命令行发送关机任务 带空格的字符串参数要用单引号
     * adb shell am startservice -n shine.com.test/.service.BackgroundService -a action_start --es param2 '2016-12-08 12:06:40'
     * @param timeShutdown 关机延时
     * @param timeStart    开机延时 UI上以秒为单位，记得乘1000转化成毫秒值
     *
     */
    private void shutdown(int timeShutdown, int timeStart) {
        if (timeShutdown < 0) {
            timeShutdown = 0;
        }
        if (timeShutdown > timeStart) {
            Toast.makeText(this, "开关机设置无效", Toast.LENGTH_SHORT).show();
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        Calendar instance = Calendar.getInstance();
        String current = sdf.format(instance.getTimeInMillis());
        Log.d(TAG, "当前时间为 " + current);
        String start = sdf.format(instance.getTimeInMillis() + timeStart);
        Log.d(TAG, "开机时间为: " + start);
        //一定要先设置开机时间
        BackgroundService.start(this, BackgroundService.ACTION_START, -1, start);
        //关机
        Log.d(TAG, timeShutdown + " 秒后关机");
        BackgroundService.start(this, BackgroundService.ACTION_SHUTDOWN, timeShutdown * 1000, "");
    }

    /**
     * 关屏并开屏 发送任务到后台服务执行，无论何处调用都有效
     * 可在命令行操作，-n 指定service路径 -a指定action  ei 表示Int类型参数 es是String类型参数 param1是参数名 2是参数值
     * adb shell am startservice -n shine.com.test/.service.BackgroundService -a action_open_screen --ei param1 2
     *
     * @param timeCloseScreen 关屏延时 不能大于开屏延时 这个方法里不对参数作
     * @param timeOpenScreen  开屏延时
     */
    private void closeScreen(int timeCloseScreen, int timeOpenScreen) {
        if (timeCloseScreen < 0) {
            timeCloseScreen = 0;
        }
        if (timeCloseScreen > timeOpenScreen) {
            Toast.makeText(this, "开关屏时间设置无效", Toast.LENGTH_SHORT).show();
            return;
        }
        //关屏
        BackgroundService.start(this, BackgroundService.ACTION_CLOSE_SCREEN, timeCloseScreen * 1000, "");
        //开屏
        BackgroundService.start(this, BackgroundService.ACTION_OPEN_SCREEN, timeOpenScreen * 1000, "");
    }


}
