package com.lxl.playvideo;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.VideoView;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class MainActivity extends AppCompatActivity implements CameraWrapper.CamOpenOverCallback {
    private static final String TAG = "MainActivity";
    private LibVLC libVLC;
    private CameraTexturePreview mCameraTexturePreview;
    private MediaPlayer mMediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        libVLC = new LibVLC(this);
//        mCameraTexturePreview = (CameraTexturePreview) findViewById(R.id.preview);
        VideoView video1 = (VideoView) findViewById(R.id.video1);
        File file = new File("extdata/liveStream.ini");
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(file));
            String url = properties.getProperty("url");
            if (!TextUtils.isEmpty(url)) {
                showVideo(url, video1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

       /* if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			Log.d(TAG, "request CAMERA");
			ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},1);
		}else{
			//打开摄像头
			Log.d(TAG, "here");
            Thread openThread = new Thread() {
                @Override
                public void run() {
                    CameraWrapper.getInstance().doOpenCamera(MainActivity.this);
                }
            };
            openThread.start();
		}*/
    }

    private void showVideo(final String path, VideoView videoView) {
        final LibVLC libVLC = new LibVLC(this);
        Media mMedia = null;
        if (path.startsWith("http")) {
            mMedia = new Media(libVLC, path);
        } else if (path.startsWith("rtsp")) {
            mMedia = new Media(libVLC, Uri.parse(path));
        } else {
            mMedia = new Media(libVLC, Uri.parse(path));
        }

//        mMedia.setHWDecoderEnabled(false, false);
        mMedia.setHWDecoderEnabled(true, true);
//		mMedia.setHttpoptimize();
        mMediaPlayer = new MediaPlayer(mMedia);

        IVLCVout vlcVout2 = mMediaPlayer.getVLCVout();
        vlcVout2.setVideoView(videoView);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w_screen = dm.widthPixels;
        int h_screen = dm.heightPixels;
        Log.d("dd", "屏幕尺寸2：宽度 = " + w_screen + "高度 = " + h_screen + "密度 = " + dm.densityDpi);

        vlcVout2.setWindowSize(w_screen, h_screen);
        vlcVout2.attachViews();

        mMediaPlayer.setVideoTrackEnabled(true);
        mMediaPlayer.play();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
    }

    /*
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Thread openThread = new Thread() {
                        @Override
                        public void run() {
                            CameraWrapper.getInstance().doOpenCamera(MainActivity.this);
                        }
                    };
                    openThread.start();
                }
                break;
        }
    }*/

    @Override
    public void cameraHasOpened() {
		/*SurfaceTexture surface = mCameraTexturePreview.getSurfaceTexture();
		CameraWrapper.getInstance().doStartPreview(surface, -1f);*/
    }
}
