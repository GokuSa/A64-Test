package shine.com.test.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import butterknife.ButterKnife;
import butterknife.OnClick;
import shine.com.test.R;
import shine.com.test.service.TestService;

public class RootCommandActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_root_command);
        ButterKnife.bind(this);
    }

    @OnClick({R.id.btn_back, R.id.btn_uninstall, R.id.btn_grant,R.id.btn_create_file,
            R.id.btn_change_file_content,R.id.open_web_setting,R.id.close_web_setting})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_back:
                finish();
                break;
            case R.id.btn_uninstall:
                startService(TestService.getIntent(this, TestService.ACTION_UNINSTALL));
                break;
            case R.id.btn_grant:
                startService(TestService.getIntent(this, TestService.ACTION_GRANT));
                break;
            case R.id.btn_create_file:
                startService(TestService.getIntent(this, TestService.ACTION_CREATE_FILE));
                break;
            case R.id.btn_change_file_content:
                startService(TestService.getIntent(this, TestService.ACTION_CHANGE_FILE));
                break;
            case R.id.open_web_setting:
                TestService.start(this, TestService.ACTION_OPEN_WEB_SETTING, "");
                break;
            case R.id.close_web_setting:
                TestService.start(this, TestService.ACTION_CLOSE_WEB_SETTING, "");
                break;
        }
    }


}
