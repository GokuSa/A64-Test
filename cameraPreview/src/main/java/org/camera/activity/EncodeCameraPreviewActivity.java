package org.camera.activity;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.example.camerapreview.R;

import java.io.IOException;
import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import test.CameraUtils;
import test.TextureMovieEncoder;
import test.gles.FullFrameRect;
import test.gles.Texture2dProgram;

/**
 * 编码摄像头并发送到服务端
 */
public class EncodeCameraPreviewActivity extends AppCompatActivity implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "EncodeCameraPreview";
    private Camera mCamera;
    private int mCameraPreviewWidth;
    private int mCameraPreviewHeight;
    private static TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();
    private CameraHandler mCameraHandler;
    private GLSurfaceView mGlSurfaceView;
    private CameraSurfaceRender mCameraRender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encode_camera_preview);
        mCameraHandler = new CameraHandler(this);
        mGlSurfaceView = (GLSurfaceView) findViewById(R.id.gl_camera_preview);
        mGlSurfaceView.setEGLContextClientVersion(2);

        mCameraRender = new CameraSurfaceRender(mCameraHandler, sVideoEncoder);
        mGlSurfaceView.setRenderer(mCameraRender);
        mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        Log.d(TAG, "onCreate: complete");
    }

    @Override
    protected void onResume() {
        super.onResume();
        //set preview size
        openCamera(1280, 720);
        mGlSurfaceView.onResume();
        mGlSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mCameraRender.setPreviewParameter(mCameraPreviewWidth, mCameraPreviewHeight);
            }
        });


    }

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    private void handleSetSurfaceTexture(SurfaceTexture st) {
        Log.d(TAG, "handleSetSurfaceTexture: ");
        st.setOnFrameAvailableListener(this);
        try {
            mCamera.setPreviewTexture(st);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mCamera.startPreview();
    }


    private void openCamera(int desiredWidth, int desiredHeight) {
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

        Camera.Parameters parms = mCamera.getParameters();

        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);


        // leave the frame rate set to default
        mCamera.setParameters(parms);

        int[] fpsRange = new int[2];
        Camera.Size mCameraPreviewSize = parms.getPreviewSize();
        parms.getPreviewFpsRange(fpsRange);

        mCameraPreviewWidth = mCameraPreviewSize.width;
        mCameraPreviewHeight = mCameraPreviewSize.height;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mGlSurfaceView.requestRender();
    }


    private static class CameraSurfaceRender implements GLSurfaceView.Renderer {
        private CameraHandler mCameraHandler;
        private TextureMovieEncoder mTextureMovieEncoder;
        private int mWidth;
        private int mHeight;
        private boolean sizeChanged = false;
        private boolean mIsRecording;
        private static final int STATE_RESUME = 0;
        private static final int STATE_OFF = 1;
        private static final int STATE_ON = 2;
        private int mStatus;
        private FullFrameRect mFullFrameRect;
        private int mTextureId;
        private SurfaceTexture mSurfaceTexture;
        private float[] stMatrix=new float[16];
//        private Texture2dProgram mTexture2dProgram;
//        private final float[] mProjectionMatrix = new float[16];
        public CameraSurfaceRender(CameraHandler cameraHandler, TextureMovieEncoder textureMovieEncoder) {
            mCameraHandler = cameraHandler;
            mTextureMovieEncoder = textureMovieEncoder;
        }

        private void setPreviewParameter(int width, int height) {
            Log.d(TAG, "setPreviewParameter() called with: width = [" + width + "], height = [" + height + "]");
            mWidth = width;
            mHeight = height;
            sizeChanged = true;

        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.d(TAG, "onSurfaceCreated: ");
            mIsRecording = mTextureMovieEncoder.isRecording();
            if (mIsRecording) {
                mStatus = STATE_RESUME;
            } else {
                mStatus = STATE_OFF;
            }
            mFullFrameRect = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
            mTextureId = mFullFrameRect.createTextureObject();
            //GLSurface没有looper 所以surfaceTexture 的更新通知要在主线程做
            mSurfaceTexture = new SurfaceTexture(mTextureId);

            mCameraHandler.obtainMessage(CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture).sendToTarget();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {

            Log.d(TAG, "onSurfaceChanged: ");
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            mSurfaceTexture.updateTexImage();
            //根据编码状态来启动编码或停止
            if (mIsRecording) {
                switch (mStatus) {
                    case STATE_OFF:
                        mTextureMovieEncoder.startRecording(
                                new TextureMovieEncoder.EncoderConfig(null,mWidth,mHeight,100000, EGL14.eglGetCurrentContext()));
                        mStatus=STATE_ON;
                        break;
                    case STATE_RESUME:
                        mTextureMovieEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                        mStatus=STATE_ON;
                        break;
                }
            }else{
                switch (mStatus) {
                    case STATE_ON:
                    case STATE_RESUME:
                        mTextureMovieEncoder.stopRecording();
                        mStatus = STATE_OFF;
                        break;
                }
            }
            if (sizeChanged) {
                mFullFrameRect.getProgram().setTexSize(mWidth, mHeight);
                sizeChanged=false;
            }

            mSurfaceTexture.getTransformMatrix(stMatrix);
            mFullFrameRect.drawFrame(mTextureId,stMatrix);
        }
    }


    private static class CameraHandler extends Handler {
        public static final int MSG_SET_SURFACE_TEXTURE = 0;

        // Weak reference to the Activity; only access this from the UI thread.
        private WeakReference<EncodeCameraPreviewActivity> mWeakActivity;

        public CameraHandler(EncodeCameraPreviewActivity activity) {
            mWeakActivity = new WeakReference<>(activity);
        }

        /**
         * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
         * attempts to access a stale Activity through a handler are caught.
         */
        public void invalidateHandler() {
            mWeakActivity.clear();
        }

        @Override  // runs on UI thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Log.d(TAG, "CameraHandler [" + this + "]: what=" + what);

            EncodeCameraPreviewActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.w(TAG, "CameraHandler.handleMessage: activity is null");
                return;
            }

            switch (what) {
                case MSG_SET_SURFACE_TEXTURE:
                    activity.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
                    break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }
}
