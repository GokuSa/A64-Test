package org.camera;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * author:
 * 时间:2018/1/23
 * qq:1220289215
 * 类描述：视频播放封装类
 */

public class MoivePlayer {
    private static final String TAG = "MoivePlayer";
    private MediaCodec.BufferInfo mBufferInfo=new MediaCodec.BufferInfo();
    private Surface mOutputSurface;
    private File mFile;
    private FrameCallback mFrameCallback;
    private volatile  boolean isStopRequest=false;
    private final int mWidth;
    private final int mHeight;

    public MoivePlayer(Surface outputSurface, File file,FrameCallback frameCallback) throws IOException {
        mOutputSurface = outputSurface;
        mFile = file;
        mFrameCallback=frameCallback;
        MediaExtractor mediaExtractor=null;
        try {
            mediaExtractor=new MediaExtractor();
            mediaExtractor.setDataSource(file.getAbsolutePath());
            int track = selectTrack(mediaExtractor);
            if (track < 0) {
                throw new RuntimeException("no track found");
            }
            mediaExtractor.selectTrack(track);
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(track);
            mWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            mHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            Log.d(TAG, "width:" + mWidth);
            Log.d(TAG, "height:" + mHeight);
        } finally {
            if (mediaExtractor !=null){
                mediaExtractor.release();
            }
        }
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    private int selectTrack(MediaExtractor mediaExtractor) {
        int trackCount = mediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            String mine =mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            Log.d(TAG, mine);
            if (mine.startsWith("video/")) {
                return i;
            }
        }
        return -1;
    }

    public void requestStop() {
        isStopRequest=true;
    }

    public void play() throws IOException {
        Log.d(TAG, "play: ");
        MediaExtractor mediaExtractor=null;
        MediaCodec mediaCodec =null;
        try {
            mediaExtractor = new MediaExtractor();
            mediaExtractor.setDataSource(mFile.getAbsolutePath());
            int track = selectTrack(mediaExtractor);
            if (track < 0) {
                throw new RuntimeException("no track found");
            }
            mediaExtractor.selectTrack(track);
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(track);
            String mine = mediaFormat.getString(MediaFormat.KEY_MIME);
             mediaCodec = MediaCodec.createDecoderByType(mine);
            mediaCodec.configure(mediaFormat, mOutputSurface, null, 0);
            mediaCodec.start();

            doExtract(mediaExtractor, mediaCodec,track);
        } finally {
            if (mediaExtractor != null) {
                mediaExtractor.release();
                mediaExtractor=null;
            }
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
            }
        }

    }

    private void doExtract(MediaExtractor mediaExtractor, MediaCodec mediaCodec, int track) {
        boolean isInputDone=false;
        boolean isOutPutDone=false;
        long firstNanoSecs=-1;
        int inputChuck=0;
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        final int TIME_OUT=1000;
        while (!isOutPutDone) {
            if (isStopRequest) {
                return;
            }
            if (!isInputDone) {
                int index = mediaCodec.dequeueInputBuffer(TIME_OUT);
                if (index >= 0) {
                    if (firstNanoSecs == -1) {
                        firstNanoSecs = System.nanoTime();
                    }
                    ByteBuffer inputBuffer = inputBuffers[index];
                    int chunkSize = mediaExtractor.readSampleData(inputBuffer, 0);
                    if (chunkSize < 0) {
                        Log.d(TAG, "put eos");
                        mediaCodec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isInputDone=true;
                    }else{
                        mediaCodec.queueInputBuffer(index, 0, chunkSize, mediaExtractor.getSampleTime(), 0);
                        inputChuck++;
                        mediaExtractor.advance();
                    }
                }else{
                    Log.d(TAG, "doExtract: input buffer not available");
                }
            }

            if (!isOutPutDone) {
                int status = mediaCodec.dequeueOutputBuffer(mBufferInfo, TIME_OUT);
                if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "doExtract: output buffer not available");
                } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG, "doExtract: not important as we use surface");
                }else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = mediaCodec.getOutputFormat();
                    Log.d(TAG, "change format "+outputFormat.toString());
                } else if (status < 0) {
                    throw new RuntimeException("unexpected result of dequeueOutputBuffer "+status);
                }else{

                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM )!= 0) {
                        isOutPutDone=true;
                    }

                    boolean doRender=mBufferInfo.size!=0;

                    if (doRender && mFrameCallback != null) {
                        mFrameCallback.preRender(mBufferInfo.presentationTimeUs);
                    }

                    mediaCodec.releaseOutputBuffer(status,doRender);
                }
            }

        }

    }

    public static class PlayTask implements Runnable {
        MoivePlayer mMoivePlayer;
        PlayerFeedBack mPlayerFeedBack;
        private LocalHandler mLocalHandler;

        public PlayTask(MoivePlayer moivePlayer, PlayerFeedBack playerFeedBack) {
            mMoivePlayer = moivePlayer;
            mPlayerFeedBack = playerFeedBack;
            mLocalHandler=new LocalHandler();
        }
        public void execute() {
            Thread thread = new Thread(this, "worker");
            thread.start();
        }

        public void requestStop() {
            mMoivePlayer.requestStop();
        }

        @Override
        public void run() {
            try {
                mMoivePlayer.play();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }finally {
                Log.d(TAG, "run: fially");
            }

            mLocalHandler.obtainMessage(LocalHandler.MSG_PLAY_STOPPED,mPlayerFeedBack).sendToTarget();
        }
    }

    private static class LocalHandler extends Handler{
        private static final int MSG_PLAY_STOPPED = 0;
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PLAY_STOPPED:
                    PlayerFeedBack playerFeedBack = (PlayerFeedBack) msg.obj;
                    playerFeedBack.playBackStopped();
                    break;
                default:
                    throw new IllegalArgumentException("no such msg");
            }
        }
    }
    public interface FrameCallback {
        /**
         * Called immediately before the frame is rendered.
         * @param presentationTimeUsec The desired presentation time, in microseconds.
         */
        void preRender(long presentationTimeUsec);

        /**
         * Called immediately after the frame render call returns.  The frame may not have
         * actually been rendered yet.
         * TODO: is this actually useful?
         */
        void postRender();

        /**
         * Called after the last frame of a looped movie has been rendered.  This allows the
         * callback to adjust its expectations of the next presentation time stamp.
         */
        void loopReset();
    }

    public interface PlayerFeedBack{
        void playBackStopped();
    }
}
