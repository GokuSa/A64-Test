package test;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.example.camerapreview.R;
import com.example.local.NetworkNative;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * author:
 * 时间:2017/8/18
 * qq:1220289215
 * 类描述：使用Camera2 预览并编码、发送数据 效果不理想 待完善
 */

public class Camera2EncodeActivity extends AppCompatActivity {
    private static final String TAG = "Camera2EncodeActivity";
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);


    private MediaCodec mediaCodec;
    int videoBitrate = 90000;
    int videoFramePerSecond = 25;
    int iframeInterval = 2;
    int width = 640;
    int height = 480;
    private Surface mEncodeSurface;
    private TextureView mTextureView;
    private Size mPreviewSize;
    private CameraCaptureSession mSession;
    private byte[] configbyte;
    private NetworkNative mNetworkNative=new NetworkNative();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2_encode);
        mNetworkNative.OpenSocket();
        mTextureView = (TextureView) findViewById(R.id.camera_textureview);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mNetworkNative.CloseSocket();
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * A reference to the opened {@link android.hardware.camera2.CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mPreviewSession;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
//            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera(int width, int height) {
       /* if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "request CAMERA");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
            return;
        }*/
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];

            // Choose the sizes for camera preview and video recording
            mPreviewSize = new Size(640, 480);

           /* int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();*/
            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Log.d(TAG, "Cannot access the camera.");
            finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.

        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            try {
                startPreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mCameraOpenCloseLock.release();
           /* if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }*/
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            finish();
        }

    };

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void startPreview() throws CameraAccessException {
        setUpMediaCoderc();
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(mEncodeSurface);
        final Surface mPreviewSurface = new Surface(mTextureView.getSurfaceTexture());
        surfaces.add(mPreviewSurface);

        mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                mSession = session;
                try {
                    CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    builder.addTarget(mPreviewSurface);
                    mSession.setRepeatingRequest(builder.build(), null, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                Log.d(TAG, "onConfigureFailed() called with: session = [" + session + "]");
            }
        }, mBackgroundHandler);

        CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        builder.addTarget(mEncodeSurface);
        mSession.setRepeatingRequest(builder.build(), null, mBackgroundHandler);

        mediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                Log.d(TAG, "onInputBufferAvailable");

            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int outputBufferIndex, MediaCodec.BufferInfo bufferInfo) {
                Log.d(TAG, "onOutputBufferAvailable");
                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);
                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG " + Arrays.toString(outData));
                    configbyte = outData;
                } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    Log.d(TAG, "send key frame");
                    byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
                    System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                    System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                    mNetworkNative.SendFrame(keyframe, keyframe.length, 1);
                } else {
                    mNetworkNative.SendFrame(outData, outData.length, 0);
                }
                codec.releaseOutputBuffer(outputBufferIndex, false);
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

            }
        });
        mediaCodec.start();
    }

    private void setUpMediaCoderc() {
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFramePerSecond);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframeInterval);

        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncodeSurface = mediaCodec.createInputSurface();

    }


}
