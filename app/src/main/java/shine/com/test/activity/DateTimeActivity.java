package shine.com.test.activity;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import shine.com.test.R;
import shine.com.test.utils.Common;

import static android.media.CamcorderProfile.get;

/**
 * 遥控器版的日期时间选择
 */
public class DateTimeActivity extends AppCompatActivity implements DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {
    private static final String TAG = "DateTimeActivity";
    @Bind(R.id.tv_clock)
    TextView mTvClock;
    private DatePickerDialog mDatePickerDialog;

    @Bind(R.id.tv_date)
    TextView mTvDate;
    @Bind(R.id.tv_time)
    TextView mTvTime;
    private Calendar mCalendar;
    private TimePickerDialog mTimePickerDialog;
    SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    private Handler mHandler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    String time = DateFormat.getDateTimeInstance().format(System.currentTimeMillis());
                    mTvClock.setText(time);
                    mHandler.sendEmptyMessageDelayed(0,60*1000);
                    break;
            }

        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_date_time);
        ButterKnife.bind(this);
        mCalendar = Calendar.getInstance();
        //整分更新时钟
        String time = DateFormat.getDateTimeInstance().format(System.currentTimeMillis());
        mTvClock.setText(time);
        int current_second=mCalendar.get(Calendar.SECOND);
        Log.d(TAG, "current_second:" + current_second);
        mHandler.sendEmptyMessageDelayed(0,(60-current_second)*1000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() called");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart() called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() called");
    }

    @OnClick({R.id.btn_pick_date, R.id.btn_pick_time,R.id.btn_send_msg})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_pick_date:
                showDatePicker();
                break;
            case R.id.btn_pick_time:
                showTimePicker();
                break;
            case R.id.btn_send_msg:
                String current = mDateTimeFormat.format(mCalendar.getTimeInMillis());

                break;
        }
    }

    /**
     * 对话框的样式
     * 深色 AlertDialog.THEME_HOLO_DARK
     * 浅色 AlertDialog.THEME_HOLO_LIGHT
     */
    private void showDatePicker() {
        mDatePickerDialog = new DatePickerDialog(DateTimeActivity.this, AlertDialog.THEME_HOLO_LIGHT, this, 2015, 2, 26);
        mDatePickerDialog.setOnKeyListener(mDataOnKeyListener);
        mDatePickerDialog.show();
    }

    private void showTimePicker() {
        mTimePickerDialog = new TimePickerDialog(this, AlertDialog.THEME_HOLO_LIGHT, this, 14, 23, true);
        mTimePickerDialog.setOnKeyListener(mTimeOnKeyListener);
        mTimePickerDialog.show();
    }

    @Override
    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
        mCalendar.set(Calendar.YEAR, year);
        mCalendar.set(Calendar.MONTH, month);
        mCalendar.set(Calendar.DAY_OF_MONTH, day);
        mTvDate.setText(mDateFormat.format(mCalendar.getTimeInMillis()));
    }

    /**
     * 日期对话框的对确定按键的监听 ，等价于确定按钮
     * 必须返回false，否则其他按键不能向下传递
     */
    private DialogInterface.OnKeyListener mDataOnKeyListener = new DialogInterface.OnKeyListener() {
        @Override
        public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent keyEvent) {
            Log.d(TAG, "i:" + keyCode);
            if (KeyEvent.KEYCODE_ENTER == keyCode && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                mDatePickerDialog.onClick(null, -1);
                dialogInterface.dismiss();
            }
            return false;
        }
    };
    /**
     * 对时间按键的监听
     */
    private DialogInterface.OnKeyListener mTimeOnKeyListener = new DialogInterface.OnKeyListener() {
        @Override
        public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent keyEvent) {
            Log.d(TAG, "i:" + keyCode);
            if (KeyEvent.KEYCODE_ENTER == keyCode && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                mTimePickerDialog.onClick(null, -1);
                dialogInterface.dismiss();
            }
            return false;
        }
    };


    @Override
    public void onTimeSet(TimePicker timePicker, int hour, int min) {
        mCalendar.set(Calendar.HOUR_OF_DAY, hour);
        mCalendar.set(Calendar.MINUTE, min);
        mCalendar.set(Calendar.SECOND, 0);
        mTvTime.setText(mTimeFormat.format(mCalendar.getTimeInMillis()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }
}
