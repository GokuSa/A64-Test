package com.lxl.audio;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.lxl.audio.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity  {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding mBind;
    private AudioViewModel mAudioViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBind = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mAudioViewModel = new AudioViewModel(this);
        mBind.setViewModel(mAudioViewModel);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        mTalkManager.exit();
        mAudioViewModel.exit();
    }


}
