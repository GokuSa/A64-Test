package org.camera.activity;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;

import com.example.camerapreview.R;

import org.camera.camera.CameraWrapper;
import org.camera.camera.CameraWrapper.CamOpenOverCallback;
import org.camera.preview.CameraTexturePreview;


public class CameraSurfaceTextureActivity extends Activity implements CamOpenOverCallback{
	private static final String TAG = "CameraPreviewActivity";
	private CameraTexturePreview mCameraTexturePreview;
	private float mPreviewRate = -1f;


    @Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_preview);
		initUI();
//		initViewParams();
		/*if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			Log.d(TAG, "request CAMERA");
			ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},1);
		}else{
			//打开摄像头
			Log.d(TAG, "here");
            Thread openThread = new Thread() {
                @Override
                public void run() {
                    CameraWrapper.getInstance().doOpenCamera(CameraSurfaceTextureActivity.this);
                }
            };
            openThread.start();
		}*/

        Thread openThread = new Thread() {
            @Override
            public void run() {
                CameraWrapper.getInstance().doOpenCamera(CameraSurfaceTextureActivity.this);
            }
        };
        openThread.start();


	}
	
	@Override  
    protected void onStart() {  
		Log.i(TAG, "onStart");
        super.onStart();  
        

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    private void initUI() {
		mCameraTexturePreview = (CameraTexturePreview) findViewById(R.id.camera_textureview);
    }

    private void initViewParams() {
		LayoutParams params = mCameraTexturePreview.getLayoutParams();
		DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();  
        int screenWidth = displayMetrics.widthPixels;  
        int screenHeight = displayMetrics.heightPixels; 
        params.width = screenWidth;  
        params.height = screenHeight;   
        this.mPreviewRate = (float)screenHeight / (float)screenWidth; 
        mCameraTexturePreview.setLayoutParams(params);
	}

/*    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Thread openThread = new Thread() {
                        @Override
                        public void run() {
                            CameraWrapper.getInstance().doOpenCamera(CameraSurfaceTextureActivity.this);
                        }
                    };
                    openThread.start();
                }
                break;
        }
    }*/

	@Override
	public void cameraHasOpened() {
		SurfaceTexture surface = this.mCameraTexturePreview.getSurfaceTexture();
		CameraWrapper.getInstance().doStartPreview(surface, mPreviewRate);
	}
}
