package org.camera.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.example.camerapreview.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * author:
 * 时间:2017/9/22
 * qq:1220289215
 * 类描述：
 */

public class TakePictureActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = "TakePictureActivity";
    private Camera mCamera;
    //默认横屏
    private boolean isLand = false;
    private Button mButtonSwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_texture_from_camera);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.sv_camera);
        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(this);
        Button button = (Button) findViewById(R.id.btn_click);
        mButtonSwitch = (Button) findViewById(R.id.btn_switch);
        button.setText("拍照");
        button.setOnClickListener(this);
        mButtonSwitch.setOnClickListener(this);

    }

    @Override
    protected void onStart() {
        super.onStart();
        openCamera();

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseCamera();
    }

    private void openCamera() {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
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
//        mCamera.setDisplayOrientation(90);
        Camera.Parameters parms = mCamera.getParameters();
        parms.setPreviewSize(640,480);
        //默认横屏
        parms.setPictureSize(1600, 1200);
        //竖屏
//        parms.setPictureSize(1200, 1600);
        parms.setPreviewFormat(ImageFormat.YV12);
//        parms.setPictureFormat(ImageFormat.YV12);
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
        Log.d(TAG, "surfaceChanged: ");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed: ");
    }

    private void saveRotatePicture(final byte[] data) {
        new Thread() {
            @Override
            public void run() {
                Bitmap bMap;
                try {
                    bMap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    Log.d(TAG, "bMap.getWidth():" + bMap.getWidth());
                    Log.d(TAG, "bMap.getHeight():" + bMap.getHeight());
                    Matrix matrix = new Matrix();
                    matrix.reset();
                    matrix.postRotate(90);
                    Bitmap bMapRotate = Bitmap.createBitmap(bMap, 0, 0, bMap.getWidth(), bMap.getHeight(), matrix, true);
//                    bMap = bMapRotate;
                    Log.d(TAG, "bMapRotate.getWidth():" + bMapRotate.getWidth());
                    Log.d(TAG, "bMapRotate.getHeight():" + bMapRotate.getHeight());

                    // Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                    File file = new File("extdata/test_rotate");
                    BufferedOutputStream bos =
                            new BufferedOutputStream(new FileOutputStream(file));
                    bMapRotate.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                    bos.flush();//输出
                    bos.close();//关闭
                    bMap.recycle();

                } catch (Exception e) {
                    e.printStackTrace();
                }


            }
        }.start();
    }

    private void savePicture(final byte[] data) {
        new Thread() {
            @Override
            public void run() {
                Log.d(TAG, "data.length:" + data.length / 1000);
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream("extdata/test");
                    fileOutputStream.write(data);
                    fileOutputStream.flush();

                } catch (IOException e) {
                    Log.d(TAG, "fail to write");
                    e.printStackTrace();
                }

            }
        }.start();
    }

    private void takePicture() {
        if (mCamera != null) {
            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    if (data == null) {
                        Log.e(TAG, "onPictureTaken: data is null");
                    } else {
                        Log.d(TAG, "get jpej data");
                        savePicture(data);
                        camera.startPreview();
                    }
                }
            });
        }
    }
    @Override
    public void onClick(View v) {
        Log.d(TAG, "onClick: ");
        switch (v.getId()) {
            case R.id.btn_click:
                takePicture();
                break;
            case R.id.btn_switch:
                isLand = !isLand;
                if (isLand) {
                    mButtonSwitch.setText("竖屏");
                }else{
                    mButtonSwitch.setText("横屏");
                }
                mCamera.stopPreview();
                Camera.Parameters parameters = mCamera.getParameters();
                //横屏
                if (isLand) {
                    parameters.setPictureSize(1600, 1200);
                   mCamera.setDisplayOrientation(0);
                } else {
                    parameters.setPictureSize(1200, 1600);
                    mCamera.setDisplayOrientation(90);
                }
                mCamera.setParameters(parameters);
                mCamera.startPreview();

                break;
        }

    }
}
