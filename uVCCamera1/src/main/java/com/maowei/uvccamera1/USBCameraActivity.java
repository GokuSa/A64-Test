package com.maowei.uvccamera1;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.VideoView;
import android.widget.ViewSwitcher;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.shine.usbcameralib.gles.FullFrameRect;
import com.shine.usbcameralib.gles.Texture2dProgram;
import com.shine.usbcameralib.gles.TextureMovieEncoder;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static com.shine.usbcameralib.gles.TextureMovieEncoder.mNetworkNative;

/**
 * 预览尺寸和摄像头一定要匹配
 *   mCamera.setPreviewSize(640,480, UVCCamera.FRAME_FORMAT_YUYV);
 */
public class USBCameraActivity extends AppCompatActivity implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "USBCameraActivity";
    private USBMonitor mUSBMonitor;
    private static final int CORE_POOL_SIZE = 1;        // initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;            // maximum threads
    private static final int KEEP_ALIVE_TIME = 10;        // time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private GLSurfaceView mGLView;
    private CameraSurfaceRenderer mRenderer;
    private CameraHandler mCameraHandler;
    private boolean mRecordingEnabled;      // controls button state
    // this is static so it survives activity restarts
    private static TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();
    private UVCCamera mCamera;
    private VideoView mVideo;
    private EditText mEditTextUrl;
    private EditText mEditTextBitRate;
    private Button mBtnTogglePlay;
    private ViewSwitcher mViewSwitch;
    private MediaPlayer mMediaPlayer;
    private LibVLC mLibVLC;
    private int mWScreen;
    private int mHScreen;
    private boolean isPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);
        mCameraHandler = new CameraHandler(this);
        mRecordingEnabled = sVideoEncoder.isRecording();
        mNetworkNative.OpenSocket();

        mGLView = (GLSurfaceView) findViewById(R.id.glSurfaceView);
//        mGLView.setZOrderOnTop(true);
        mGLView.setZOrderMediaOverlay(true);
        mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
        mRenderer = new CameraSurfaceRenderer(mCameraHandler, sVideoEncoder, new File(""));
        mGLView.setRenderer(mRenderer);
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mUSBMonitor.register();
        mVideo = (VideoView) findViewById(R.id.videoView);

        mEditTextUrl = (EditText) findViewById(R.id.et_url);
        mEditTextBitRate = (EditText) findViewById(R.id.et_bitrate);
        String url = PreferenceManager.getDefaultSharedPreferences(this).getString("url", "shine_net://tcp@172.168.1.153:5020?dec=hard?mode=quene?cache=300?fps=25");
        mEditTextUrl.setText(url);

        CheckBox cbEncodeSwitch = (CheckBox) findViewById(R.id.cb_auto_encode_switch);
        CheckBox cbPlaySwitch = (CheckBox) findViewById(R.id.cb_auto_play_switch);
        cbEncodeSwitch.setOnCheckedChangeListener(mCheckedChangeListener);
        cbPlaySwitch.setOnCheckedChangeListener(mCheckedChangeListener);

        mBtnTogglePlay = (Button) findViewById(R.id.toggleplay_button);
        mViewSwitch = (ViewSwitcher) findViewById(R.id.viewSwitch);
        setupMediaPlayer();

    }

    @Override
    protected void onResume() {
        super.onResume();
        openCamera();
        mGLView.onResume();
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
//                mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
                mRenderer.setCameraPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // Tell the renderer that it's about to be paused so it can clean up.
                mRenderer.notifyPausing();
            }
        });
        mGLView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        String url = mEditTextUrl.getText().toString();
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("url", url).apply();
    }
    @Override
    protected void onDestroy() {
        mUSBMonitor.unregister();
        if (mCamera != null) {
            mCamera.destroy();
            mCamera = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mNetworkNative.CloseSocket();
        mNetworkNative = null;
        super.onDestroy();
        System.exit(0);
    }


    private void setupMediaPlayer() {
        mLibVLC = new LibVLC(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        mWScreen = dm.widthPixels;
        mHScreen = dm.heightPixels;

    }

    private void showVideo(final String path, VideoView videoView) {
        Log.d(TAG, "showVideo: showVideo");
        Media media = new Media(mLibVLC, Uri.parse(path));
        media.setHWDecoderEnabled(true, true);
        mMediaPlayer = new MediaPlayer(media);
        IVLCVout vlcVout = mMediaPlayer.getVLCVout();
        vlcVout.setVideoView(videoView);
//        vlcVout.setWindowSize(mWScreen, (int)(mWScreen*0.75f));
        vlcVout.setWindowSize(mWScreen, mHScreen);
        vlcVout.attachViews();

        mMediaPlayer.setVideoTrackEnabled(true);
        mMediaPlayer.play();
        mMediaPlayer.setEventListener(mEventListener);

    }

    private void stopPlayStream() {
        Log.d(TAG, "stopPlayStream() called");
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }



    private MediaPlayer.EventListener mEventListener = new MediaPlayer.EventListener() {
        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch (event.type) {
                case MediaPlayer.Event.EncounteredError:
                case MediaPlayer.Event.EndReached:
                    Log.e(TAG, "onEvent: play error");
            }
        }
    };
    private boolean autoEncodeSwitch;
    private boolean autoPlaySwitch;
    private CompoundButton.OnCheckedChangeListener mCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            switch (buttonView.getId()) {
                case R.id.cb_auto_encode_switch:
                    autoEncodeSwitch = isChecked;
                    if (isChecked) {
                        if (!mCameraHandler.hasMessages(CameraHandler.MSG_AUTO_ENCODE_SWITCH)) {
                            mCameraHandler.sendEmptyMessage(CameraHandler.MSG_AUTO_ENCODE_SWITCH);
                        }
                    } else {
                        mCameraHandler.removeMessages(CameraHandler.MSG_AUTO_ENCODE_SWITCH);
                    }
                    break;
                case R.id.cb_auto_play_switch:
                    autoPlaySwitch = isChecked;
                    if (isChecked) {
                        if (!mCameraHandler.hasMessages(CameraHandler.MSG_AUTO_PLAY_SWITCH)) {
                            mCameraHandler.sendEmptyMessage(CameraHandler.MSG_AUTO_PLAY_SWITCH);
                        }
                    } else {
                        mCameraHandler.removeMessages(CameraHandler.MSG_AUTO_PLAY_SWITCH);
                    }
                    break;
            }

        }
    };
    private void openCamera() {
        final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
        List<UsbDevice> deviceList = mUSBMonitor.getDeviceList(filter);
        Log.d(TAG, "deviceList:" + deviceList);
        if (deviceList.size() > 0) {
            mUSBMonitor.requestPermission(deviceList.get(0));
        } else {
            Log.e(TAG, "onCreate: no camera device");
        }
    }

    /**
     * onClick handler for "record" button.
     */
    public void clickToggleRecording(@SuppressWarnings("unused") View unused) {
        mRecordingEnabled = !mRecordingEnabled;
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeRecordingState(mRecordingEnabled);
            }
        });
        updateControls();
    }

    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private void updateControls() {
        Button toggleRelease = (Button) findViewById(R.id.toggleRecording_button);
//        int id = mRecordingEnabled ? R.string.toggleRecordingOff : R.string.toggleRecordingOn;
        String id = mRecordingEnabled ? "正在编码点击停止" : "编码停止点击开始";
        toggleRelease.setText(id);
    }
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.destroy();
            mCamera = null;
        }
    }

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
//			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            Log.d(TAG, "onConnect");
            if (mCamera != null)
                mCamera.destroy();
            mCamera = new UVCCamera();
            EXECUTER.execute(new Runnable() {
                @Override
                public void run() {
                    mCamera.open(ctrlBlock);
                    List<Size> supportedSizeList = mCamera.getSupportedSizeList();
                    for (Size size : supportedSizeList) {
                        Log.d(TAG, "size:" + size);
                    }

//					mCamera.setPreviewTexture(mUVCCameraView.getSurfaceTexture());
                   /* if (mPreviewSurface != null) {
                        mPreviewSurface.release();
                        mPreviewSurface = null;
                    }*/
                    try {
//                        mCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_YUYV);
                        mCamera.setPreviewSize(640,480, UVCCamera.FRAME_FORMAT_YUYV);

                    } catch (final IllegalArgumentException e) {
                        Log.e(TAG, "run: ", e);
                        // fallback to YUV mode
                       /* try {
                            mCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT*//*, UVCCamera.DEFAULT_PREVIEW_MODE*//*);
                        } catch (final IllegalArgumentException e1) {
                            mCamera.destroy();
                            mCamera = null;
                        }*/
                    }
                   /* if (mCamera != null) {
                        final SurfaceTexture st = mTextureView.getSurfaceTexture();
                        if (st != null)
                            mPreviewSurface = new Surface(st);
//						mCamera.setPreviewDisplay(mPreviewSurface);
                        mCamera.setPreviewTexture(st);

//						mCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21*//*UVCCamera.PIXEL_FORMAT_NV21*//*);
                        mCamera.startPreview();
                        Log.d(TAG, "StartEncoderThread");
//                        avcCodec = new AvcEncoder(640,480,30,8500*1000);
//                        avcCodec.StartEncoderThread();
                    }*/
                }
            });
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            if (mCamera != null) {
                mCamera.close();
                /*if (mPreviewSurface != null) {
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }*/
            }
        }


        @Override
        public void onDetach(final UsbDevice device) {
            Log.d(TAG, "onDetach() called with: device = [" + device + "]");
        }

        @Override
        public void onCancel() {
        }
    };

    private void handleSetSurfaceTexture(SurfaceTexture st) {
        Log.d(TAG, "handleSetSurfaceTexture: ");
        st.setOnFrameAvailableListener(this);
        mCamera.setPreviewTexture(st);
        mCamera.startPreview();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mGLView.requestRender();

    }

    public void clickTogglePlay(View view) {
        String url = mEditTextUrl.getText().toString();
        if (TextUtils.isEmpty(url)) {
            return;
        }
//        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("url",url).apply();
        mViewSwitch.showNext();
        isPlaying = !isPlaying;
        if (isPlaying && mMediaPlayer == null) {
            showVideo(url, mVideo);
            mBtnTogglePlay.setText("停止播放直播流");
        } else {
            stopPlayStream();
            mBtnTogglePlay.setText("播放直播流");
        }
    }

    static class CameraHandler extends Handler {
        public static final int MSG_SET_SURFACE_TEXTURE = 0;
        public static final int MSG_AUTO_ENCODE_SWITCH = 1;
        public static final int MSG_AUTO_PLAY_SWITCH = 2;

        // Weak reference to the Activity; only access this from the UI thread.
        private WeakReference<USBCameraActivity> mWeakActivity;

        public CameraHandler(USBCameraActivity activity) {
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

            USBCameraActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.w(TAG, "CameraHandler.handleMessage: activity is null");
                return;
            }

            switch (what) {
                case MSG_SET_SURFACE_TEXTURE:
                    activity.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
                    break;
                case MSG_AUTO_ENCODE_SWITCH:
//                    activity.handleAutoEncodeSwitch();
                    break;
                case MSG_AUTO_PLAY_SWITCH:
//                    activity.handleAutoPlaySwitch();
                    break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }

    class CameraSurfaceRenderer implements GLSurfaceView.Renderer {
        private static final String TAG = "CameraSurfaceRenderer";
        private static final boolean VERBOSE = false;

        private static final int RECORDING_OFF = 0;
        private static final int RECORDING_ON = 1;
        private static final int RECORDING_RESUMED = 2;

        private USBCameraActivity.CameraHandler mCameraHandler;
        private TextureMovieEncoder mVideoEncoder;
        private File mFileOutput;

        private FullFrameRect mFullScreen;

        private final float[] mSTMatrix = new float[16];
        private int mTextureId;

        private SurfaceTexture mSurfaceTexture;
        private boolean mRecordingEnabled;
        private int mRecordingStatus;
        private int mFrameCount;

        // width/height of the incoming camera preview frames
        private boolean mIncomingSizeUpdated;
        private int mIncomingWidth;
        private int mIncomingHeight;


        /**
         * Constructs CameraSurfaceRenderer.
         * <p>
         *
         * @param cameraHandler Handler for communicating with UI thread
         * @param movieEncoder  video encoder object
         */
        public CameraSurfaceRenderer(USBCameraActivity.CameraHandler cameraHandler,
                                     TextureMovieEncoder movieEncoder, File fileOutput) {
            Log.d(TAG, "CameraSurfaceRenderer: ");
            mCameraHandler = cameraHandler;
            mVideoEncoder = movieEncoder;
            mFileOutput = fileOutput;

            mTextureId = -1;

            mRecordingStatus = -1;
            mRecordingEnabled = false;
            mFrameCount = -1;

            mIncomingSizeUpdated = false;
            mIncomingWidth = mIncomingHeight = -1;

        }


        /**
         * Notifies the renderer thread that the activity is pausing.
         * <p>
         * For best results, call this *after* disabling Camera preview.
         */
        public void notifyPausing() {
            if (mSurfaceTexture != null) {
                Log.d(TAG, "renderer pausing -- releasing SurfaceTexture");
                mSurfaceTexture.release();
                mSurfaceTexture = null;
            }
            if (mFullScreen != null) {
                mFullScreen.release(false);     // assume the GLSurfaceView EGL context is about
                mFullScreen = null;             //  to be destroyed
            }
            mIncomingWidth = mIncomingHeight = -1;
        }

        /**
         * Notifies the renderer that we want to stop or start recording.
         */
        public void changeRecordingState(boolean isRecording) {
            Log.d(TAG, "changeRecordingState: was " + mRecordingEnabled + " now " + isRecording);
            mRecordingEnabled = isRecording;
        }


        /**
         * Records the size of the incoming camera preview frames.
         * <p>
         * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
         * so we assume it could go either way.  (Fortunately they both run on the same thread,
         * so we at least know that they won't execute concurrently.)
         */
        public void setCameraPreviewSize(int width, int height) {
            Log.d(TAG, "setCameraPreviewSize");
            mIncomingWidth = width;
            mIncomingHeight = height;
            mIncomingSizeUpdated = true;
        }

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            Log.d(TAG, "onSurfaceCreated");

            // We're starting up or coming back.  Either way we've got a new EGLContext that will
            // need to be shared with the video encoder, so figure out if a recording is already
            // in progress.
            mRecordingEnabled = mVideoEncoder.isRecording();
            if (mRecordingEnabled) {
                mRecordingStatus = RECORDING_RESUMED;
            } else {
                mRecordingStatus = RECORDING_OFF;
            }

            // Set up the texture blitter that will be used for on-screen display.  This
            // is *not* applied to the recording, because that uses a separate shader.
            mFullScreen = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

            mTextureId = mFullScreen.createTextureObject();

            // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
            // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
            // available messages will arrive on the main thread.
            mSurfaceTexture = new SurfaceTexture(mTextureId);

            // Tell the UI thread to enable the camera preview.
            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                    USBCameraActivity.CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));


        }


        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
        }

        @Override
        public void onDrawFrame(GL10 unused) {
//            if (VERBOSE) Log.d(TAG, "onDrawFrame tex=" + mTextureId);
//            boolean showBox = false;

            // Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
            // was there before.
            mSurfaceTexture.updateTexImage();

            // If the recording state is changing, take care of it here.  Ideally we wouldn't
            // be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
            // makes it hard to do elsewhere.
            if (mRecordingEnabled) {
                switch (mRecordingStatus) {
                    case RECORDING_OFF:
                        Log.d(TAG, "START recording");
                        // start recording
//                        String text = mEditTextBitRate.getText().toString().trim();
                        String text = "";
                        int bitRate = 3000 * 1000;
                        if (!TextUtils.isEmpty(text)) {
                            try {
                                double v = Double.parseDouble(text);
                                Log.d(TAG, "onDrawFrame: 当前码率 " + v);
                                if (v > 0) {
                                    bitRate *= v;
                                }
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                            }
                        }
                        mVideoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(mFileOutput, mIncomingWidth, mIncomingHeight, bitRate, EGL14.eglGetCurrentContext()));
                        mRecordingStatus = RECORDING_ON;
                        break;
                    case RECORDING_RESUMED:
                        Log.d(TAG, "RESUME recording");
                        mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                        mRecordingStatus = RECORDING_ON;
                        break;
                    case RECORDING_ON:
                        // yay
                        break;
                    default:
                        throw new RuntimeException("unknown status " + mRecordingStatus);
                }
            } else {
                switch (mRecordingStatus) {
                    case RECORDING_ON:
                    case RECORDING_RESUMED:
                        // stop recording
                        Log.d(TAG, "STOP recording");
                        mVideoEncoder.stopRecording();
                        mRecordingStatus = RECORDING_OFF;
                        break;
                    case RECORDING_OFF:
                        // yay
                        break;
                    default:
                        throw new RuntimeException("unknown status " + mRecordingStatus);
                }
            }

            // Set the video encoder's texture name.  We only need to do this once, but in the
            // current implementation it has to happen after the video encoder is started, so
            // we just do it here.
            //
            // TODO: be less lame.
            mVideoEncoder.setTextureId(mTextureId);

            // Tell the video encoder thread that a new frame is available.
            // This will be ignored if we're not actually recording.
            mVideoEncoder.frameAvailable(mSurfaceTexture);

            if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
                // Texture size isn't set yet.  This is only used for the filters, but to be
                // safe we can just skip drawing while we wait for the various races to resolve.
                // (This seems to happen if you toggle the screen off/on with power button.)
                Log.i(TAG, "Drawing before incoming texture size set; skipping");
                return;
            }

            if (mIncomingSizeUpdated) {
                mFullScreen.getProgram().setTexSize(mIncomingWidth, mIncomingHeight);
                mIncomingSizeUpdated = false;
            }

            // Draw the video frame.
            mSurfaceTexture.getTransformMatrix(mSTMatrix);
            mFullScreen.drawFrame(mTextureId, mSTMatrix);

        }


    }

}

