package org.camera.activity;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.example.camerapreview.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import static com.example.camerapreview.R.id.webView;

/**
 * author:
 * 时间:2017/9/25
 * qq:1220289215
 * 类描述：js 调用java代码拍照 ，java返回图片路径
 */

public class WebCameraActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "WebCameraActivity";
    private WebView mWebView;
    private Camera mCamera;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_camera);
        mWebView = (WebView) findViewById(webView);
        mWebView.setWebChromeClient(new WebChromeClient());

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDefaultTextEncodingName("UTF-8");

        //为js添加java接口
        mWebView.addJavascriptInterface(new Android(), "Android");
        //加载url 使用本地演示
//        mWebView.loadUrl("file:///android_asset/test.html");
        mWebView.loadUrl("http://172.168.1.69:8081/rtaserver/");
        //显示摄像机预览
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.sv_preview);
        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
//        openCamera();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        releaseCamera();
    }

    private void openCamera() {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }
        Camera.Parameters parms = mCamera.getParameters();
        //设置预览尺寸
        parms.setPreviewSize(640, 480);
        //设置图片输出尺寸
        parms.setPictureSize(1600, 1200);
        parms.setPreviewFormat(ImageFormat.YV12);
        mCamera.setParameters(parms);


    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated: ");
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    //给js调用的接口
    public class Android {
        @JavascriptInterface
        public void takePictureInJava() {
            Toast.makeText(WebCameraActivity.this, "开始拍照", Toast.LENGTH_SHORT).show();
            takePicture();
        }
    }

    //开始照相，第三个回调返回的是原始数据，保存的时候会一些卡顿
    private void takePicture() {
        if (mCamera != null) {
            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    if (data != null) {
                        Log.d(TAG, "get jpej data " + data.length / 1000);
                        savePicture(data);
                        camera.startPreview();
                    }
                }
            });
        }
    }

    //保存图片到本地 没有压缩的原始数据 需要在子线程执行
    private void savePicture(final byte[] data) {
        new Thread() {
            @Override
            public void run() {
                try {
                    final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            , String.format(Locale.CHINA, "image_%d", System.currentTimeMillis()));
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    fileOutputStream.write(data);
                    fileOutputStream.flush();
                    //保存图片后，必须在主线程调用js接口
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            javaCallJS(file.getAbsolutePath());
                        }
                    });
                } catch (IOException e) {
                    Log.d(TAG, "fail to write");
                    e.printStackTrace();
                }

            }
        }.start();
    }


    private void javaCallJS(String absolutePath) {
        String result = String.format(Locale.CHINA, "javascript:getPicturePathFromJava('%s')", absolutePath);
        mWebView.loadUrl(result);
    }
}
