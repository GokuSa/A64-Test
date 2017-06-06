package shine.com.test.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import shine.com.test.R;

public class MyPreferenceActivity extends AppCompatActivity {
    SharedPreferences mSharedPreferencesOne;
    SharedPreferences mSharedPreferencesTwo;
    SharedPreferences mSharedPreferencesThree;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_preference);
//        init();

    }

    private void init() {
        //使用类名生成SharePreference文件，acivity.MyPreferenceActivity.xml
        mSharedPreferencesOne = getPreferences(Context.MODE_PRIVATE);
        //指定生产的文件名：Test.xml
        mSharedPreferencesTwo = getSharedPreferences("Test", Context.MODE_PRIVATE);
        //使用包名生产文件 shine.com.test_preference.xml
        mSharedPreferencesThree = PreferenceManager.getDefaultSharedPreferences(this);

        mSharedPreferencesOne.edit().putString("one","hei").apply();
        mSharedPreferencesTwo.edit().putString("two","hei").apply();
        mSharedPreferencesThree.edit().putString("three","hei").apply();
    }


}
