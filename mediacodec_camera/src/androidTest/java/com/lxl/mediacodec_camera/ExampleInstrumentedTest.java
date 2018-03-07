package com.lxl.mediacodec_camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.Surface;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Instrumentation test, which will execute on an Android device.
 *need to understand Surface process
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    private static final String TAG = "ExampleInstrumentedTest";
    private static final boolean VERBOSE = false;           // lots of logging
    Camera mCamera = null;
    int width = 640;
    int height = 480;
    private MediaCodec.BufferInfo mBufferInfo;
    public static final String MINE = "video/avc";
    private MediaCodec mMediaCodec;
    private CodecInputSurface mCodecInputSurface;
    private SurfaceTextureManager mSurfaceTextureManager;
    private static final long DURATION_SEC = 10;             // 8 seconds of video
    private MediaMuxer mMediaMuxer;
    private int mTrackIndex;
    private boolean mMuxerStart=false;

    @Test
    public void start() throws InterruptedException {
//      EncodeWrapper.runTest(this);
        new Thread(){
            @Override
            public void run() {
                try {
                    encodeCameraToMpeg();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private static class EncodeWrapper implements Runnable {
        private ExampleInstrumentedTest mExampleInstrumentedTest;

        private EncodeWrapper(ExampleInstrumentedTest exampleInstrumentedTest) {
            mExampleInstrumentedTest = exampleInstrumentedTest;}

        @Override
        public void run() {
            try {
                mExampleInstrumentedTest.encodeCameraToMpeg();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void runTest(ExampleInstrumentedTest test) throws InterruptedException {
            EncodeWrapper encodeWrapper = new EncodeWrapper(test);
            Thread thread = new Thread(encodeWrapper);
            thread.start();
            thread.join();

        }
    }

    public void encodeCameraToMpeg() throws Exception {
        prepareCamera();
        prepareEncode();
        mCodecInputSurface.makeCurrent();
        prepareSurfaceTexture();
        mCamera.startPreview();
        long startWhen = System.nanoTime();
        long desiredEnd = startWhen + DURATION_SEC * 1000000000L;
        SurfaceTexture st = mSurfaceTextureManager.getSurfaceTexture();
        int frameCount = 0;

        try {
            while (System.nanoTime() < desiredEnd) {
                drain(false);
                mSurfaceTextureManager.awaitNewFrame();
                mSurfaceTextureManager.drawImage();
                mCodecInputSurface.setPresentationTime(st.getTimestamp());
                Log.d(TAG, "sending frame to encoder");
                mCodecInputSurface.swapBuffers();
            }
            drain(true);
        } finally {
            realeseCamera();
            realeaseEncode();
            realeaseSurface();
        }

    }

    private void prepareCamera() {
        if (mCamera != null) {
            throw new RuntimeException("camera already run");
        }
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int numberOfCameras = Camera.getNumberOfCameras();
        Log.d(TAG, "numberOfCameras:" + numberOfCameras);
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            Log.d(TAG, "cameraInfo.facing:" + cameraInfo.facing);
            if (Camera.CameraInfo.CAMERA_FACING_FRONT == cameraInfo.facing) {
                mCamera = Camera.open(i);
                break;
            }
        }

        if (mCamera == null) {
            mCamera = Camera.open();
            Log.d(TAG, "use default camera");
        }
        if (mCamera == null) {
            throw new RuntimeException("found no camera");
        }

        Camera.Parameters parameters = mCamera.getParameters();
        choosePreviewSize(parameters, width, height);
        mCamera.setParameters(parameters);
    }

    private void realeseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private void realeaseEncode() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        if (mCodecInputSurface != null) {
            mCodecInputSurface.release();
            mCodecInputSurface = null;
        }
        if (mMediaMuxer != null) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;
        }
    }

    private void realeaseSurface() {
        if (mSurfaceTextureManager != null) {
            mSurfaceTextureManager.release();
            mSurfaceTextureManager = null;
        }
    }

    private void prepareEncode() {
        mBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(MINE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        try {
            mMediaCodec = MediaCodec.createEncoderByType(MINE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mCodecInputSurface = new CodecInputSurface(mMediaCodec.createInputSurface());
        mMediaCodec.start();
        String outpath = "extdata/test.mp4";
        try {
            mMediaMuxer = new MediaMuxer(outpath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "fail to get media muxer");
        }
        mTrackIndex=-1;
        mMuxerStart=false;
    }

    private void prepareSurfaceTexture() {
        mSurfaceTextureManager = new SurfaceTextureManager();
        try {
            mCamera.setPreviewTexture(mSurfaceTextureManager.getSurfaceTexture());
        } catch (IOException e) {
            throw new RuntimeException("fail to set Surface Texure");
        }

    }

    private void drain(boolean endOfStream) {
        Log.d(TAG, "drain() called with: endOfStream = [" + endOfStream + "]");
        final int TIMEOUT_USEC = 10000;

        if (endOfStream) {
            mMediaCodec.signalEndOfInputStream();
        }
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        while (true) {
            int encodeStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            Log.d(TAG, "encodeStatus:" + encodeStatus);
            if (MediaCodec.INFO_TRY_AGAIN_LATER == encodeStatus) {
                Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                if (!endOfStream) {
                    break;
                } else {
                    Log.d(TAG, "hei");
                }
            } else if (MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED == encodeStatus) {
                Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");

                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (MediaCodec.INFO_OUTPUT_FORMAT_CHANGED == encodeStatus) {
                Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                if (mMuxerStart) {
                    throw new RuntimeException("format change twice");
                }

                MediaFormat outputFormat = mMediaCodec.getOutputFormat();
                mTrackIndex = mMediaMuxer.addTrack(outputFormat);
                mMediaMuxer.start();
                mMuxerStart=true;

            } else if (encodeStatus < 0) {
                Log.d(TAG, "unexpected ");
            } else {
                ByteBuffer outputBuffer = outputBuffers[encodeStatus];
                if (outputBuffer == null) {
                    throw new RuntimeException("output buffer is null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size != 0) {
                    Log.d(TAG, "mBufferInfo.offset:" + mBufferInfo.offset+"mBufferInfo.size:" + mBufferInfo.size);
                    if (!mMuxerStart) {
                        throw new RuntimeException("mux not start");
                    }
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);
                    mMediaMuxer.writeSampleData(mTrackIndex,outputBuffer,mBufferInfo);
                }
                mMediaCodec.releaseOutputBuffer(encodeStatus, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.d(TAG, "unexcepted end");
                    } else {
                        Log.d(TAG, "reach end");
                    }
                    break;
                }
            }
        }
    }

    private static void choosePreviewSize(Camera.Parameters parms, int width, int height) {
        // We should make sure that the requested MPEG size is less than the preferred
        // size, and has the same aspect ratio.
        Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            Log.d(TAG, "Camera preferred preview size for video is " +
                    ppsfv.width + "x" + ppsfv.height);
        }

        for (Camera.Size size : parms.getSupportedPreviewSizes()) {
            Log.d(TAG, size.width + "--" + size.height);
            if (size.width == width && size.height == height) {
                parms.setPreviewSize(width, height);
                return;
            }
        }

        Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
        if (ppsfv != null) {
            parms.setPreviewSize(ppsfv.width, ppsfv.height);
        }
    }


    private static class SurfaceTextureManager implements SurfaceTexture.OnFrameAvailableListener {
        STextureRender mSTextureRender;
        private SurfaceTexture mSurfaceTexture;
        private boolean mFrameAvailble = false;
        private final Object mSynFrameObject = new Object();

        public SurfaceTextureManager() {
            mSTextureRender = new STextureRender();
            mSTextureRender.surfaceCreated();
            Log.d(TAG, "mSTextureRender.getTextureId():" + mSTextureRender.getTextureId());
            mSurfaceTexture = new SurfaceTexture(mSTextureRender.getTextureId());
            mSurfaceTexture.setOnFrameAvailableListener(this);
        }

        public void release() {
            mSTextureRender = null;
            mSurfaceTexture = null;
        }

        public void drawImage() {
            Log.d(TAG, "drawImage() called");
            mSTextureRender.drawFrame(mSurfaceTexture);
        }

        public SurfaceTexture getSurfaceTexture() {
            return mSurfaceTexture;
        }

        public void awaitNewFrame() {
            Log.d(TAG, "awaitNewFrame() called");
            int TIMEOUT_MS = 2500;
            synchronized (mSynFrameObject) {
                while (!mFrameAvailble) {
                    try {
                        mSynFrameObject.wait(TIMEOUT_MS);
                        if (!mFrameAvailble) {
                            throw new RuntimeException("time out for new frame");
                        }

                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                mFrameAvailble = false;
            }
            mSTextureRender.checkGlError("before updateTexImage");
            mSurfaceTexture.updateTexImage();
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            Log.d(TAG, "new Frame");
            synchronized (mSynFrameObject) {
                if (mFrameAvailble) {
                    throw new RuntimeException("something wrong");
                }
                mFrameAvailble = true;
                mSynFrameObject.notifyAll();
            }
        }
    }

    private static class CodecInputSurface {
        private static final int EGL_RECORDABLE_ANDROID = 0x3142;

        private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
        private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

        private Surface mSurface;

        /**
         * Creates a CodecInputSurface from a Surface.
         */
        public CodecInputSurface(Surface surface) {
            if (surface == null) {
                throw new NullPointerException();
            }
            mSurface = surface;

            eglSetup();
        }

        /**
         * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
         */
        private void eglSetup() {
            mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException("unable to get EGL14 display");
            }
            int[] version = new int[2];
            if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
                throw new RuntimeException("unable to initialize EGL14");
            }

            // Configure EGL for recording and OpenGL ES 2.0.
            int[] attribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL_RECORDABLE_ANDROID, 1,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                    numConfigs, 0);
            checkEglError("eglCreateContext RGB888+recordable ES2");

            // Configure context for OpenGL ES 2.0.
            int[] attrib_list = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                    attrib_list, 0);
            checkEglError("eglCreateContext");

            // Create a window surface, and attach it to the Surface we received.
            int[] surfaceAttribs = {
                    EGL14.EGL_NONE
            };
            mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurface,
                    surfaceAttribs, 0);
            checkEglError("eglCreateWindowSurface");
        }

        /**
         * Discards all resources held by this class, notably the EGL context.  Also releases the
         * Surface that was passed to our constructor.
         */
        public void release() {
            if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
                EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(mEGLDisplay);
            }
            mSurface.release();

            mEGLDisplay = EGL14.EGL_NO_DISPLAY;
            mEGLContext = EGL14.EGL_NO_CONTEXT;
            mEGLSurface = EGL14.EGL_NO_SURFACE;

            mSurface = null;
        }

        /**
         * Makes our EGL context and surface current.
         */
        public void makeCurrent() {
            EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
            checkEglError("eglMakeCurrent");
        }

        /**
         * Calls eglSwapBuffers.  Use this to "publish" the current frame.
         */
        public boolean swapBuffers() {
            boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
            checkEglError("eglSwapBuffers");
            return result;
        }

        /**
         * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
         */
        public void setPresentationTime(long nsecs) {
            EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
            checkEglError("eglPresentationTimeANDROID");
        }

        /**
         * Checks for EGL errors.  Throws an exception if one is found.
         */
        private void checkEglError(String msg) {
            int error;
            if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
                throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
            }
        }
    }

    private static class STextureRender {
        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        private final float[] mTriangleVerticesData = {
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0, 0.f, 0.f,
                1.0f, -1.0f, 0, 1.f, 0.f,
                -1.0f, 1.0f, 0, 0.f, 1.f,
                1.0f, 1.0f, 0, 1.f, 1.f,
        };

        private FloatBuffer mTriangleVertices;

        private static final String VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n" +
                        "uniform mat4 uSTMatrix;\n" +
                        "attribute vec4 aPosition;\n" +
                        "attribute vec4 aTextureCoord;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "void main() {\n" +
                        "    gl_Position = uMVPMatrix * aPosition;\n" +
                        "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                        "}\n";

        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +      // highp here doesn't seem to matter
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "void main() {\n" +
                        "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                        "}\n";

        private float[] mMVPMatrix = new float[16];
        private float[] mSTMatrix = new float[16];

        private int mProgram;
        private int mTextureID = -12345;
        private int muMVPMatrixHandle;
        private int muSTMatrixHandle;
        private int maPositionHandle;
        private int maTextureHandle;

        public STextureRender() {
            mTriangleVertices = ByteBuffer.allocateDirect(
                    mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            mTriangleVertices.put(mTriangleVerticesData).position(0);

            Matrix.setIdentityM(mSTMatrix, 0);
        }

        public int getTextureId() {
            return mTextureID;
        }

        public void drawFrame(SurfaceTexture st) {
            checkGlError("onDrawFrame start");
            st.getTransformMatrix(mSTMatrix);

            // (optional) clear to green so we can see if we're failing to set pixels
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

            GLES20.glUseProgram(mProgram);
            checkGlError("glUseProgram");

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maPosition");
            GLES20.glEnableVertexAttribArray(maPositionHandle);
            checkGlError("glEnableVertexAttribArray maPositionHandle");

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maTextureHandle");
            GLES20.glEnableVertexAttribArray(maTextureHandle);
            checkGlError("glEnableVertexAttribArray maTextureHandle");

            Matrix.setIdentityM(mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");

            // IMPORTANT: on some devices, if you are sharing the external texture between two
            // contexts, one context may not see updates to the texture unless you un-bind and
            // re-bind it.  If you're not using shared EGL contexts, you don't need to bind
            // texture 0 here.
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        /**
         * Initializes GL state.  Call this after the EGL surface has been created and made current.
         */
        public void surfaceCreated() {
            mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (mProgram == 0) {
                throw new RuntimeException("failed creating program");
            }
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            checkLocation(maPositionHandle, "aPosition");
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            checkLocation(maTextureHandle, "aTextureCoord");

            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            checkLocation(muMVPMatrixHandle, "uMVPMatrix");
            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
            checkLocation(muSTMatrixHandle, "uSTMatrix");

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);

            mTextureID = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
            checkGlError("glBindTexture mTextureID");

            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            checkGlError("glTexParameter");
        }

        /**
         * Replaces the fragment shader.  Pass in null to reset to default.
         */
        public void changeFragmentShader(String fragmentShader) {
            if (fragmentShader == null) {
                fragmentShader = FRAGMENT_SHADER;
            }
            GLES20.glDeleteProgram(mProgram);
            mProgram = createProgram(VERTEX_SHADER, fragmentShader);
            if (mProgram == 0) {
                throw new RuntimeException("failed creating program");
            }
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            checkGlError("glCreateShader type=" + shaderType);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES20.glCreateProgram();
            if (program == 0) {
                Log.e(TAG, "Could not create program");
            }
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }

        public void checkGlError(String op) {
            int error;
            while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                Log.e(TAG, op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }

        public static void checkLocation(int location, String label) {
            if (location < 0) {
                throw new RuntimeException("Unable to locate '" + label + "' in program");
            }
        }
    }
}
