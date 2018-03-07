package org.camera.activity;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;

import com.example.camerapreview.R;

import java.io.IOException;
import java.lang.ref.WeakReference;

import test.CameraUtils;
import test.ScaledDrawable2d;
import test.gles.Drawable2d;
import test.gles.EglCore;
import test.gles.GlUtil;
import test.gles.Sprite2d;
import test.gles.Texture2dProgram;
import test.gles.WindowSurface;

/**
 * 控制相机预览的演示 可旋转 缩放 变焦
 * 目前仅预览
 */
public class TextureFromCameraActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "TextureFromCamera";
    private static final int DEFAULT_ZOOM_PERCENT = 0;      // 0-100
    private static final int DEFAULT_SIZE_PERCENT = 50;     // 0-100
    private static final int DEFAULT_ROTATE_PERCENT = 0;    // 0-100
    private RenderThread mRenderThread;
    private static SurfaceHolder sSurfaceHolder;
    private SurfaceView mSurfaceView;
    private boolean fullScreen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: ");
        setContentView(R.layout.activity_texture_from_camera);
        mSurfaceView = (SurfaceView) findViewById(R.id.sv_camera);
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mRenderThread = new RenderThread();
        mRenderThread.setName("render");
        mRenderThread.start();
        mRenderThread.waitUntilReady();
    }

    @Override
    protected void onPause() {
        super.onPause();
        RenderHandler renderHandler = mRenderThread.getRenderHandler();
        renderHandler.handleShutdown();

        try {
            mRenderThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException("unexpected interrupted");
        }

        mRenderThread=null;

        Log.d(TAG, "onPause: end");

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (sSurfaceHolder != null) {
            throw new RuntimeException("surface is already set");
        }
        sSurfaceHolder=holder;
        if (mRenderThread != null) {
            RenderHandler renderHandler = mRenderThread.getRenderHandler();
            renderHandler.handleSurfaceAvailable(holder, true);
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged() called with: holder = [" + holder + "], format = [" + format + "], width = [" + width + "], height = [" + height + "]");
        if (mRenderThread != null) {
            RenderHandler renderHandler = mRenderThread.getRenderHandler();
            renderHandler.handleSurfaceChange(width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mRenderThread != null) {
            RenderHandler renderHandler = mRenderThread.getRenderHandler();
            renderHandler.handleSurfaceDestory();
        }
        sSurfaceHolder = null;
    }

    public void change(View view) {
        fullScreen = !fullScreen;
        RelativeLayout.LayoutParams layoutParams;
        if (fullScreen) {
            layoutParams = new RelativeLayout.LayoutParams(1600, 900);
        }else{
            layoutParams = new RelativeLayout.LayoutParams(426, 320);
        }
        mSurfaceView.setLayoutParams(layoutParams);

    }

    private static class RenderThread extends Thread implements SurfaceTexture.OnFrameAvailableListener {
        private Object mStartLock = new Object();
        private boolean mReady = false;

        private RenderHandler mRenderHandler;
        private EglCore mEglCore;
        private Camera mCamera;
        private WindowSurface mWindowSurface;

        private final ScaledDrawable2d mRectDrawable = new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect = new Sprite2d(mRectDrawable);

        private Texture2dProgram mTexture2dProgram;
        private SurfaceTexture mSurfaceTexture;
        private int mWindowWidth;
        private int mWindowHeight;
        private float[] mProjectionMatrix=new float[16];

        private int mZoomPercent = DEFAULT_ZOOM_PERCENT;
        private int mSizePercent = DEFAULT_SIZE_PERCENT;
        private int mRotatePercent = DEFAULT_ROTATE_PERCENT;
        private float mPosX, mPosY;
        @Override
        public void run() {
            Looper.prepare();
            mRenderHandler = new RenderHandler(this);
            synchronized (mStartLock) {
                mReady = true;
                mStartLock.notify();
            }
            Log.d(TAG, "ready to receive message ");
            mEglCore = new EglCore(null, 0);
            openCamera(1280, 720, 30);
            Looper.loop();

            releaseCamera();
            releaseGL();
            mEglCore.release();

            synchronized (mStartLock) {
                mReady=false;
            }

        }

        private void releaseCamera() {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }

        }

        //打开相机，设置预览的尺寸
        private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
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

            // Try to set the frame rate to a constant value.
            int thousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);
            Log.d(TAG, "thousandFps:" + thousandFps);
            // Give the camera a hint that we're recording video.  This can have a big
            // impact on frame rate.
            parms.setRecordingHint(true);

            mCamera.setParameters(parms);

            int[] fpsRange = new int[2];
            Camera.Size mCameraPreviewSize = parms.getPreviewSize();
            parms.getPreviewFpsRange(fpsRange);
            String previewFacts = mCameraPreviewSize.width + "x" + mCameraPreviewSize.height;
            if (fpsRange[0] == fpsRange[1]) {
                previewFacts += " @" + (fpsRange[0] / 1000.0) + "fps";
            } else {
                previewFacts += " @[" + (fpsRange[0] / 1000.0) +
                        " - " + (fpsRange[1] / 1000.0) + "] fps";
            }
            Log.i(TAG, "Camera config: " + previewFacts);
        }

        public RenderHandler getRenderHandler() {
            return mRenderHandler;
        }

        public void waitUntilReady() {
            Log.d(TAG, "waitUntilReady: ");
            synchronized (mStartLock) {
                while (!mReady) {
                    try {
                        mStartLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void shutdown() {
            Looper.myLooper().quit();
        }

        private void handleSurfaceAvailable(SurfaceHolder surfaceHolder, boolean newSurface) {
            Surface surface = surfaceHolder.getSurface();
            mWindowSurface = new WindowSurface(mEglCore, surface, false);
            mWindowSurface.makeCurrent();
            //创建并配置TextureSurface，用来从Camera接受数据帧
            mTexture2dProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
           int textureId = mTexture2dProgram.createTextureObject();
            mSurfaceTexture = new SurfaceTexture(textureId);
            //rect的作用不明确，好像用来绘制数据帧的
            mRect.setTexture(textureId);
            if (!newSurface) {
                mWindowWidth=mWindowSurface.getWidth();
                mWindowHeight=mWindowSurface.getHeight();
                finishSetupSurface();
            }
            mSurfaceTexture.setOnFrameAvailableListener(this);
        }

        private void handleSurfaceChange(int width, int height) {
            mWindowWidth=width;
            mWindowHeight=height;
            finishSetupSurface();
        }

        private void finishSetupSurface() {
            int width=mWindowWidth;
            int height = mWindowHeight;
            GLES20.glViewport(0, 0, width, height);
            Matrix.orthoM(mProjectionMatrix,0,0,width,0,height,-1,1);

            mPosX=width/2;
            mPosY=height/2;

            Log.d(TAG, "mPosX:" + mPosX);
            Log.d(TAG, "mPosY:" + mPosY);
            updateGeometry();
            Log.d(TAG, "finishSetupSurface: start preview");
            try {
                mCamera.setPreviewTexture(mSurfaceTexture);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mCamera.startPreview();
        }

        private void updateGeometry() {
            int width=mWindowWidth;
            int height = mWindowHeight;
            Log.d(TAG, "width:" + width);
            Log.d(TAG, "height:" + height);
            int smallDim = Math.min(width, height);
            // Max scale is a bit larger than the screen, so we can show over-size.
            float scaled = smallDim * (mSizePercent / 100.0f) * 1.25f;
            Log.d(TAG, "scaled:" + scaled);
            float cameraAspect = (float) 640 / 480;
            int newWidth = Math.round(scaled * cameraAspect);
            int newHeight = Math.round(scaled);

            float zoomFactor = 1.0f - (mZoomPercent / 100.0f);
            Log.d(TAG, "zoomFactor:" + zoomFactor);
            int rotAngle = Math.round(360 * (mRotatePercent / 100.0f));
            Log.d(TAG, "rotAngle:" + rotAngle);
//            mRect.setScale(newWidth, newHeight);
            mRect.setScale(width, height);
            mRect.setPosition(mPosX, mPosY);
            mRect.setRotation(rotAngle);
            mRectDrawable.setScale(zoomFactor);


        }
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mRenderHandler.sendFrameAvailable();
        }

        public void handleSurfaceDestory() {
            releaseGL();
        }

        private void releaseGL() {
            GlUtil.checkGlError("releaseGl start");

            if (mWindowSurface != null) {
                mWindowSurface.release();
                mWindowSurface = null;
            }
            if (mTexture2dProgram != null) {
                mTexture2dProgram.release();
                mTexture2dProgram = null;
            }
            GlUtil.checkGlError("releaseGl done");
            mEglCore.makeNothingCurrent();

        }

        private void handleFrameAvailable() {
            mSurfaceTexture.updateTexImage();
            draw();
        }

        private void draw() {
            GlUtil.checkGlError("start draw");
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexture2dProgram, mProjectionMatrix);
            mWindowSurface.swapBuffers();
            GlUtil.checkGlError(" draw done");
        }

    }

    private static class RenderHandler extends Handler {
        public static final int MSG_SHUT_DOWN = 1;
        public static final int MSG_SURFACE_AVAILABLE = 2;
        private static final int MSG_SURFACE_CHANGE = 3;
        private static final int MSG_SURFACE_DESTORY = 4;
        private static final int MSG_FRAME_AVAILABLE = 5;
        WeakReference<RenderThread> mWeakReference;

        public RenderHandler(RenderThread renderThread) {
            mWeakReference = new WeakReference<>(renderThread);
        }

        @Override
        public void handleMessage(Message msg) {
            RenderThread renderThread = mWeakReference.get();
            if (renderThread == null) {
                Log.e(TAG, "handleMessage: renderThread is null");
                return;
            }
            switch (msg.what) {
                case MSG_SHUT_DOWN:
                    renderThread.shutdown();
                    break;
                case MSG_SURFACE_AVAILABLE:
                    renderThread.handleSurfaceAvailable((SurfaceHolder)msg.obj,msg.arg1!=0);
                    break;
                case MSG_SURFACE_CHANGE:
                    renderThread.handleSurfaceChange(msg.arg1,msg.arg2);
                    break;
                case MSG_SURFACE_DESTORY:
                    renderThread.handleSurfaceDestory();
                    break;
                case MSG_FRAME_AVAILABLE:
                    renderThread.handleFrameAvailable();
                    break;

            }

        }

        public void handleShutdown() {
            sendMessage(obtainMessage(MSG_SHUT_DOWN));
        }

        public void handleSurfaceAvailable(SurfaceHolder surface, boolean newSurface) {
            sendMessage(obtainMessage(MSG_SURFACE_AVAILABLE,newSurface?1:0,9,surface));
        }

        public void handleSurfaceChange(int width, int height) {
            sendMessage(obtainMessage(MSG_SURFACE_CHANGE, width, height));
        }

        public void handleSurfaceDestory() {
            sendMessage(obtainMessage(MSG_SURFACE_DESTORY));
        }

        public void sendFrameAvailable() {
            sendMessage(obtainMessage(MSG_FRAME_AVAILABLE));
        }
    }


}
