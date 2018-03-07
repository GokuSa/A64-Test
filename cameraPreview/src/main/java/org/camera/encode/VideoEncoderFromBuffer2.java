package org.camera.encode;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.example.local.NetworkNative;

import org.camera.camera.CameraWrapper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoderFromBuffer2 {
    private static final String TAG = "VideoEncoderFromBuffer";
    private static final boolean VERBOSE = true; // lots of logging
    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/Movies/h264";
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private static final int FRAME_RATE = 25; // 15fps
    private static final int IFRAME_INTERVAL = 5; // 10 between
    // I-frames
    private static final int TIMEOUT_USEC = 10000;
    private static final int COMPRESS_RATIO = 256;
    private static final int BIT_RATE = CameraWrapper.IMAGE_HEIGHT * CameraWrapper.IMAGE_WIDTH * 3 * 8 * FRAME_RATE / COMPRESS_RATIO; // bit rate CameraWrapper.
    private int mWidth;
    private int mHeight;
    private MediaCodec mMediaCodec;
    //    private MediaMuxer mMuxer;
    private BufferInfo mBufferInfo;
    private int mTrackIndex = -1;
    private boolean mMuxerStarted;
    //    byte[] mFrameData;
    FileOutputStream mFileOutputStream = null;
    private int mColorFormat;
    private long mStartTime = 0;
    private byte[] configbyte;
    private NetworkNative mNetworkNative = new NetworkNative();


    public VideoEncoderFromBuffer2(int width, int height) {
        Log.i(TAG, "VideoEncoder()");
        this.mWidth = width;
        this.mHeight = height;
//        mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];
        mNetworkNative.OpenSocket();
        mBufferInfo = new BufferInfo();
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            // Don't fail CTS if they don't have an AVC codec (not here,
            // anyway).
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        if (VERBOSE)
            Log.d(TAG, "found codec: " + codecInfo.getName());
        mColorFormat = selectColorFormat(codecInfo, MIME_TYPE);
        if (VERBOSE)
            Log.d(TAG, "found colorFormat: " + mColorFormat);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, this.mWidth, this.mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 500000);
//        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 5*mWidth*mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE)
            Log.d(TAG, "format: " + mediaFormat);
        try {
            mMediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();

        mStartTime = System.nanoTime();

    }


    public void encodeFrame(byte[] input/* , byte[] output */) {
//        Log.d(TAG, "encodeFrame() called with: ");
//        Log.i(TAG, "encodeFrame()");
//        long encodedSize = 0;
//        NV21toI420SemiPlanar(input, mFrameData, this.mWidth, this.mHeight);

//        int i=0;
//        i++;
//        if(i==1) {
//            return;//20170816 byh
//        }
        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);

//        Log.d(TAG, "inputBufferIndex-->" + inputBufferIndex);
        if (inputBufferIndex >= 0) {
//            long endTime = System.nanoTime();
//            long ptsUsec = (endTime - mStartTime) / 1000;
//            Log.i(TAG, "resentationTime: " + ptsUsec);
//            ByteBuffer inputBuffer= mMediaCodec.getInputBuffer(inputBufferIndex);
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
//            inputBuffer.put(mFrameData);
            inputBuffer.put(input);
//            mMediaCodec.queueInputBuffer(inputBufferIndex, 0,mFrameData.length, System.nanoTime() / 1000, 0);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, System.nanoTime() / 1000, 0);
        } else {
            // either all in use, or we timed out during initial setup
            if (VERBOSE)
                Log.d(TAG, "input buffer not available");
        }

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
//        Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
//            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
            byte[] outData = new byte[mBufferInfo.size];
            outputBuffer.get(outData);
            if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG " + mBufferInfo.size);
//                configbyte = new byte[mBufferInfo.size];
                configbyte = outData;
//                Log.d(TAG,"configbyte "+ Arrays.toString(configbyte));

            } else if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                byte[] keyframe = new byte[mBufferInfo.size + configbyte.length];
//                Log.d(TAG, "BUFFER_FLAG_KEY_FRAME "+keyframe.length);
                System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
//                Log.d("testCamera", "send key frame");
                mNetworkNative.SendFrame(keyframe, keyframe.length, 1);
            } else {
//                Log.d(TAG, "send "+outData.length);
//                Log.d("testCamera", "send  frame");
                mNetworkNative.SendFrame(outData, outData.length, 0);
            }

            mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        }

    }

    public void close() {

        Log.i(TAG, "close()");
        try {
            mMediaCodec.stop();
            mMediaCodec.release();
            mNetworkNative.CloseSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * NV21 is a 4:2:0 YCbCr, For 1 NV21 pixel: YYYYYYYY VUVU I420YUVSemiPlanar
     * is a 4:2:0 YUV, For a single I420 pixel: YYYYYYYY UVUV Apply NV21 to
     * I420YUVSemiPlanar(NV12) Refer to https://wiki.videolan.org/YUV/
     */
    private void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes,
                                      int width, int height) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
        for (int i = width * height; i < nv21bytes.length; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
        }
    }

    /**
     * Returns a color format that is supported by the codec and by this test
     * code. If no match is found, this throws a test failure -- the set of
     * formats known to the test should be expanded for new platforms.
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo,
                                         String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo
                .getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG,
                "couldn't find a good color format for " + codecInfo.getName()
                        + " / " + mimeType);
        return 0; // not reached
    }

    /**
     * Returns true if this is a color format that this test code understands
     * (i.e. we know how to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or
     * null if no match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }

    /**
     * Returns true if the specified color format is semi-planar YUV. Throws an
     * exception if the color format is not recognized (e.g. not YUV).
     */
    private static boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }
}
