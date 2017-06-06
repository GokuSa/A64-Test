package shine.com.test.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import java.io.File;

import butterknife.ButterKnife;
import butterknife.OnClick;
import shine.com.test.R;
import shine.com.test.applist.AppListActivity;

/**
 * 此应用主要测试和验证A64硬件和底层功能，如开关机， 串口，自定义软键盘，创建文件
 * 为系统应用，所需的so库需要放在system/lib和system/lib64中
 * 使用系统签名打包
 * 此页面为程序入口
 */
public class MainActivity extends AppCompatActivity{
    private static final String TAG = "MainActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
    }

    private void showStoragedDirectory() {
        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        Log.d(TAG, file.getAbsolutePath());
        Log.d(TAG, "Environment.getExternalStorageDirectory():" + Environment.getExternalStorageDirectory());
    }
    @OnClick({R.id.btn_on_off,R.id.btn_serial,R.id.btn_root_command,R.id.btn_date_time,R.id.btn_joda_time,R.id.btn_key_board,
            R.id.btn_back,R.id.btn_xml,R.id.btn_share_preference,R.id.btn_app_list})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_back:
                finish();
                break;
            case R.id.btn_on_off:
                start(OnOffActivity.class);
                break;
            case R.id.btn_serial:
                start(SerialActivity.class);
                break;
            case R.id.btn_root_command:
                startActivity(new Intent(this,RootCommandActivity.class));
                break;
            case R.id.btn_date_time:
                start(DateTimeActivity.class);
                break;
            case R.id.btn_joda_time:
                start(JodaTimeActivity.class);
                break;
            case R.id.btn_key_board:
                start(KeyBoardActivity.class);
                break;
            case R.id.btn_xml:
                start(XmlActivity.class);
                break;
            case R.id.btn_share_preference:
                start(MyPreferenceActivity.class);
                break;
            case R.id.btn_app_list:
                start(AppListActivity.class);
                break;
        }
    }

    private void start(Class<? extends Activity> cls) {
        startActivity(new Intent(this,cls));
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
