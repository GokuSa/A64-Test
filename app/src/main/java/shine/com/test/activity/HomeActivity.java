package shine.com.test.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import shine.com.test.R;

public class HomeActivity extends AppCompatActivity {

    @Bind(R.id.tv_content)
    TextView mTvContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ButterKnife.bind(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                Log.d("HomeActivity", "event:" + event);
                int rawX = (int) event.getX();
                int rawY = (int) event.getY();
                Log.d("HomeActivity", "rawX:" + rawX);
                Log.d("HomeActivity", "rawY:" + rawY);
                if (0xffffc405 == rawX && 0xffffc25d == rawY) {
                    mTvContent.append("\n呼叫");
                }
                break;
        }

        return true;
    }
}
