package com.lxl.mediacodec_camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.widget.VideoView;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;


public class MainActivity extends AppCompatActivity implements Camera.PreviewCallback {
    private static final String TAG = "MainActivity";
    private TextureView mSv_preview;
    private Camera camera = null;
    private boolean isPreview=true;
    private boolean stop=false;
    private AvcEncoder avcCodec;
    int framerate = 30;
    int biterate = 8500*1000;
    public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(10);

    private int mWidth=640;
    private int mHeight=480;
    private VideoView mVideoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSv_preview = (TextureView) findViewById(R.id.preView);
        mVideoView = (VideoView) findViewById(R.id.videoView);
      /*  WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metric = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metric);
        mWidth = metric.heightPixels;
        mHeight = metric.widthPixels;*/

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "internet ");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)) {
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.INTERNET},2);
            } else {
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.INTERNET},2);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "CAMERA");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            } else {
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},1);
            }
        }else{
            mSv_preview.setSurfaceTextureListener(mSurfaceTextureListener);

        }

       /* File file = new File("extdata/liveStream.ini");
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(file));
            String url = properties.getProperty("url");
            if (!TextUtils.isEmpty(url)) {
                showVideo(url,mVideoView);
            }
        } catch (IOException e) {
            e.printStackTrace();
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

        mMedia.setHWDecoderEnabled(false, false);
//		mMedia.setHttpoptimize();
        final MediaPlayer mMediaPlayer = new MediaPlayer(mMedia);

        IVLCVout vlcVout2 = mMediaPlayer.getVLCVout();
        vlcVout2.setVideoView(videoView);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w_screen = dm.widthPixels;
        int h_screen = dm.heightPixels;
        Log.i("dddddddd", "屏幕尺寸2：宽度 = " + w_screen + "高度 = " + h_screen + "密度 = " + dm.densityDpi);

        vlcVout2.setWindowSize(w_screen, h_screen);
        vlcVout2.attachViews();

        mMediaPlayer.setVideoTrackEnabled(true);
        mMediaPlayer.play();
        mMediaPlayer.setEventListener(new MediaPlayer.EventListener() {
            @Override
            public void onEvent(MediaPlayer.Event event) {
                switch (event.type) {
                    case MediaPlayer.Event.EncounteredError:
                    case MediaPlayer.Event.EndReached:
                        mMediaPlayer.stop();
                        Media mMedia;
                        if (path.startsWith("http")) {
                            mMedia = new Media(libVLC, Uri.parse(path));
                        } else {
                            mMedia = new Media(libVLC, path);
                        }

                        mMedia.setHWDecoderEnabled(false,false);
                        // mMedia.setHttpoptimize();

                        mMediaPlayer.setMedia(mMedia);
                        mMedia.release();
                        mMediaPlayer.play();
                        break;
                }

            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mSv_preview.setSurfaceTextureListener(mSurfaceTextureListener);
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            camera.stopPreview();
            Log.d(TAG, "stop Preview");
        }
        if (camera != null) {
            camera.release(); // 释放照相机
            camera = null;
        }
    }



    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Log.d(TAG, "onSurfaceTextureUpdated");
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width, int height) {
            // Log.d(TAG, "onSurfaceTextureSizeChanged");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            // Log.d(TAG, "onSurfaceTextureDestroyed");
            // stopPreview();
            if (null != camera) {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
                camera = null;
                avcCodec.StopThread();
            }
            return true;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable() called with:, width = [" + width + "], height = [" + height + "]");
           /* Matrix matrix = new Matrix();
            matrix.setScale(-1, 1);
            matrix.postTranslate(width, 0);
            mSv_preview.setTransform(matrix);*/
            if (camera == null) {
                camera = Camera.open(1); // 打开摄像头
            }
            startPreview(surface); // 开始预览
            avcCodec = new AvcEncoder(mWidth,mHeight,framerate,biterate);
            avcCodec.StartEncoderThread();
        }
    };

    public void startPreview(SurfaceTexture surfaceTexture) {
        if (camera != null) {
            camera.setPreviewCallback(this);
            if (isPreview) {
                Camera.Parameters p = camera.getParameters();
                List<Integer> supportedPreviewFormats = p.getSupportedPreviewFormats();
                Log.d(TAG, "PreviewFormats "+supportedPreviewFormats.toString());
                p.setPreviewFormat(ImageFormat.NV21);
                p.setPreviewSize(mWidth, mHeight);
//                p.setPreviewSize(640, 480);
                camera.setParameters(p);
            } else {
                Camera.Parameters p = camera.getParameters();
                p.setPreviewFormat(ImageFormat.NV21);
                p.setPreviewSize(mWidth, mHeight);
//                p.setPreviewSize(320, 240);
                camera.setParameters(p);
            }

            try {
                camera.setPreviewTexture(surfaceTexture);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "set Preview failure");
            }
            Log.d(TAG, "start Preview");
            camera.startPreview();
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (YUVQueue.size() >= 10) {
            YUVQueue.poll();
        }
        YUVQueue.add(data);
    }
}
