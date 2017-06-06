package shine.com.test.activity;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;

import java.util.Arrays;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import shine.com.test.R;

/**
 * 遥控版的软键盘不能使用 ，使用PopUpWindow自定义键盘
 * 如果popupWindow要监听按键事件就要setFocusableInTouchMode(true); 但影响布局上Button的按键
 * EditText 的类似属性 android:imeOptions="actionNext" android:inputType="text|textNoSuggestions"
 * 会影响焦点
 */
public class KeyBoardActivity extends AppCompatActivity implements View.OnKeyListener {
    private static final String TAG = "KeyBoardActivity";
    @Bind(R.id.et_one)
    EditText mEtOne;
    @Bind(R.id.et_two)
    EditText mEtTwo;
    // 显示键盘
    private PopupWindow mPopupWindow;
    // 使用自定义键盘的当前编辑框
    EditText mCurrentEditText;

    private int[] resIds = new int[]{
            R.id.btn_q, R.id.btn_w, R.id.btn_e, R.id.btn_r, R.id.btn_t,
            R.id.btn_y, R.id.btn_u, R.id.btn_i, R.id.btn_o, R.id.btn_p,
            R.id.btn_a, R.id.btn_s, R.id.btn_d, R.id.btn_f, R.id.btn_g,
            R.id.btn_h, R.id.btn_j, R.id.btn_k, R.id.btn_l, R.id.btn_del,
            R.id.btn_z, R.id.btn_x, R.id.btn_c, R.id.btn_v, R.id.btn_b,
            R.id.btn_n, R.id.btn_m, R.id.btn_coma, R.id.btn_dot, R.id.btn_slash};
    // 按键值
    private String[] keys = new String[]{ "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
            "a", "s", "d", "f", "g", "h", "j", "k", "l", "删除", "z", "x", "c",
            "v", "b", "n", "m", ",", ".", "/"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_board);
        ButterKnife.bind(this);
        mEtOne.setOnKeyListener(this);
        mEtTwo.setOnKeyListener(this);
        // 键盘视图
        setKeyBoardView();

    }

    // 设置键盘的按键的值和点击监听
    private void setKeyBoardView() {
        View contentView =getLayoutInflater().inflate(
                R.layout.layout_keyboard, null);

        mPopupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        // 如果PopupWindow中有Editor的话，focusable要为true。false,则PopUpWindow只是一个浮现在当前界面上的view而已，不影响当前界面的任何操作。
        mPopupWindow.setFocusable(true);
        mPopupWindow.setOutsideTouchable(false);
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(0x15856113));

        for (int i = 0; i < resIds.length; i++) {
            Button button = (Button) contentView.findViewById(resIds[i]);
            button.setText(keys[i]);
            button.setOnClickListener(mKeyBoardClick);
        }

    }
    @OnClick({R.id.activity_key_board,R.id.btn_check_mask})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_check_mask:
                String[] stringArray = getResources().getStringArray(R.array.masks);
                String mask = mEtTwo.getText().toString().trim();
                int index = Arrays.asList(stringArray).indexOf(mask);
                Log.d(TAG, "index:" + index);
                break;
        }

    }
    private void showKeyBoard() {
        if (mPopupWindow != null && !mPopupWindow.isShowing()) {
            mPopupWindow.showAtLocation(mEtOne, Gravity.BOTTOM, 0, 0);
        }

    }
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // 按确定键才显示键盘,而且只有用户名和密码编辑框需要
        if (keyCode == KeyEvent.KEYCODE_ENTER&& event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, keyCode + "---" + event);
            switch (v.getId()) {
                case R.id.et_one:
                    mCurrentEditText = mEtOne;
                    showKeyBoard();
                    break;
                case R.id.et_two:
                    mCurrentEditText = mEtTwo;
                    showKeyBoard();
                    break;
            }
            return true;
        }

        return false;

    }


    private View.OnClickListener mKeyBoardClick=new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //删除当前编辑框的字符
            if (v.getId() == R.id.btn_del) {
                int index = mCurrentEditText.getSelectionStart();
                mCurrentEditText.getText().delete(index - 1, index);
            } else {
                Button button = (Button) v;
                if (mCurrentEditText != null) {
                    int index = mCurrentEditText.getSelectionStart();
                    Log.d(TAG, "index:" + index);
                    mCurrentEditText.getText().insert(index,button.getText());

                }
            }
        }

    };
}
