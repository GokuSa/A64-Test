package shine.com.test.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

import butterknife.Bind;
import butterknife.ButterKnife;
import shine.com.test.R;

/**
 * android 日期时间库
 */
public class JodaTimeActivity extends AppCompatActivity {

    @Bind(R.id.tv_content)
    TextView mTvContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_joda_time);
        ButterKnife.bind(this);
        File file = new File("/extdata/lcd/PanelContrl.ini");
        if (!file.exists()) {
            try {
                boolean newFile = file.createNewFile();
                Log.d("JodaTimeActivity", "newFile:" + newFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }
}
