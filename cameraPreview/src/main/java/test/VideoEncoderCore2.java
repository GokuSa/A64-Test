/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import static test.CameraEncodeActivity.mNetworkNative;


/**
 * This class wraps up the core components used for surface-input video encoding.
 * <p>
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 * <p>
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
public class VideoEncoderCore2 {
    private static final String TAG = "VideoEncoderCore2";
    private static final boolean VERBOSE = false;

    // TODO: these ought to be configurable as well
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 25;               // 30fps
    private static final int IFRAME_INTERVAL = 1;           // 5 seconds between I-frames

    private Surface mInputSurface;
    private MediaCodec mEncoder;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    ;
    private byte[] configbyte;
    private HandlerThread mHandlerThread;
    private final PushStreamHandler mHandler;
    private static final int FRAME_BUFFER_SIZE=30;
    private ArrayBlockingQueue<FrameHolder> mFrameMessages = new ArrayBlockingQueue<>(FRAME_BUFFER_SIZE);
//    private final PushStreamWork mPushStreamWork;

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    public VideoEncoderCore2(int width, int height, int bitRate)
            throws IOException {

        mHandlerThread = new HandlerThread("encode_worker");
        mHandlerThread.start();
        mHandler = new PushStreamHandler(mHandlerThread.getLooper(), mFrameMessages);


        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE) Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int outputBufferIndex, @NonNull MediaCodec.BufferInfo bufferInfo) {
                try {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                    byte[] outData = new byte[bufferInfo.size];
                    outputBuffer.get(outData);
                    FrameHolder frameHolder;
                    if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                        configbyte = outData;
                    } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                        Log.d(TAG, "send key frame");
                        byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
                        System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                        System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                        frameHolder = new FrameHolder(keyframe, true);
                        mFrameMessages.add(frameHolder);
                    } else {
                        frameHolder = new FrameHolder(outData, false);
                        mFrameMessages.add(frameHolder);
                    }
                    //如果队列的数据在容纳范围说明消费端没有阻塞，继续发消息处理；否则推出最旧的数据，保持队列是最新的编码数据
                    if (mFrameMessages.size() < FRAME_BUFFER_SIZE) {
                        mHandler.obtainMessage(1).sendToTarget();
                    }else{
                        Log.d(TAG, "update frame");
                        mFrameMessages.poll();
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false);
                } catch (Exception e) {
                    Log.e(TAG, "onframe available" + e.toString());
                }
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                Log.e(TAG, "onError" + e.toString());
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                Log.d(TAG, "onOutputFormatChanged");
            }
        });
        mEncoder.start();
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Releases encoder resources.
     */
    public void release() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        mFrameMessages.clear();

        if (mHandler != null) {
            mHandler.getLooper().quit();

        }
        if (mHandlerThread != null) {
            try {
                mHandlerThread.join();
                mHandlerThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class PushStreamHandler extends Handler {
        private ArrayBlockingQueue<FrameHolder> mMessages;

        public PushStreamHandler(Looper looper, ArrayBlockingQueue<FrameHolder> messages) {
            super(looper);
            mMessages = messages;
        }

        @Override
        public void handleMessage(Message msg) {
            FrameHolder peek = mMessages.poll();
            if (peek != null) {
                if (peek.getKeyFrame()) {
                    //可能会阻塞，此时后边的消息不会处理，队列数据也不断增加，生产端会丢弃队列的旧数据
                    mNetworkNative.SendFrame(peek.getFrame(), peek.getFrame().length, 1);
                } else {
                    mNetworkNative.SendFrame(peek.getFrame(), peek.getFrame().length, 0);
                }
            }
        }
    }

    private static class PushStreamWork extends Thread {
        private ArrayBlockingQueue<FrameHolder> mMessages;
        private volatile boolean mStart = true;

        public PushStreamWork(ArrayBlockingQueue<FrameHolder> messages) {
            mMessages = messages;
        }

        public void setStart(boolean start) {
            mStart = start;
        }

        @Override
        public void run() {
            while (mStart) {
                FrameHolder peek = mMessages.peek();

                if (peek != null) {
                    if (peek.getKeyFrame()) {
                        Log.d(TAG, Arrays.toString(peek.getFrame()));

                        mNetworkNative.SendFrame(peek.getFrame(), peek.getFrame().length, 1);
                    } else {
                        mNetworkNative.SendFrame(peek.getFrame(), peek.getFrame().length, 0);
                    }
                }
            }
        }
    }

    final int TIMEOUT_USEC = 10000;

    //[0, 0, 0, 1, 103, 66, 0, 41, -27, 64, 80, 30, -56, 0, 0, 0, 1, 104, -50, 49, 18]
    public void encodeFrame() {
        int outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = mEncoder.getOutputBuffer(outputBufferIndex);
            byte[] outData = new byte[mBufferInfo.size];
            outputBuffer.get(outData);
            if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG " + Arrays.toString(outData));
                configbyte = outData;
            } else if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                Log.d(TAG, "send key frame");
                byte[] keyframe = new byte[mBufferInfo.size + configbyte.length];
                System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
//                mNetworkNative.SendFrame(keyframe, keyframe.length, 1);
            } else {
//                mNetworkNative.SendFrame(outData, outData.length, 0);
            }
            mEncoder.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        }

    }

    public void signalEndofStream() {
        try {
            mEncoder.signalEndOfInputStream();
        } catch (Exception e) {
            Log.e(TAG, "signalEndofStream: exception");
            e.printStackTrace();
        }
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     * <p>
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    public void drainEncoder(boolean endOfStream) {
        if (endOfStream) {
            mEncoder.signalEndOfInputStream();
        }
        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus < 0) {
                Log.d(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
//                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                    break;
                }
                byte[] outData = new byte[mBufferInfo.size];
                encodedData.get(outData);
                if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    configbyte = outData;
                } else if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    byte[] keyframe = new byte[mBufferInfo.size + configbyte.length];
                    Log.d(TAG, "SEND_KEY_FRAME ");
                    System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                    System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
//                    mNetworkNative.SendFrame(keyframe, keyframe.length, 1);
                } else {
//                    mNetworkNative.SendFrame(outData, outData.length, 0);
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                /*if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    }
                    break;
                }*/
            }
        }
    }

    private static class FrameHolder {
        private byte[] mframe;
        private boolean keyFrame;

        public FrameHolder() {
        }

        public FrameHolder(byte[] mframe, boolean keyFrame) {
            this.mframe = mframe;
            this.keyFrame = keyFrame;
        }

        public byte[] getFrame() {
            return mframe;
        }

        public boolean getKeyFrame() {
            return keyFrame;
        }
    }

}
