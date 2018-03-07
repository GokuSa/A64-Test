package org.camera.activity;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import com.example.camerapreview.R;

import org.camera.MoivePlayer;
import org.camera.SpeedControlCallback;

import java.io.File;
import java.io.IOException;

public class PlayMoiveActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, MoivePlayer.PlayerFeedBack {
    private static final String TAG = "PlayMoiveActivity";
    private TextureView mVideoView;
    private MoivePlayer.PlayTask mPlayTask;
    private boolean isPlaying=false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: ");
        setContentView(R.layout.activity_play_moive);
        mVideoView = (TextureView) findViewById(R.id.textureView);
        mVideoView.setSurfaceTextureListener(this);
        updateControl();
    }

    public void play(View view) {
        if (isPlaying) {
            mPlayTask.requestStop();
        }else{
            if (mPlayTask!=null) {
                Log.d(TAG, "is playing");
                return;
            }
            String url = "/data/gen-eight-rects.mp4";
            File file = new File(url);
            if (file.canRead()) {
                SurfaceTexture surfaceTexture = mVideoView.getSurfaceTexture();
                Surface surface = new Surface(surfaceTexture);
                SpeedControlCallback speedControlCallback=new SpeedControlCallback();
                MoivePlayer moivePlayer;
                try {
                    moivePlayer = new MoivePlayer(surface,file,speedControlCallback);
                } catch (IOException e) {
                    surface.release();
                    return;
                }
                float videoAspectRadio= moivePlayer.getHeight()*1.0f/moivePlayer.getWidth();

                adjustRadio(videoAspectRadio);

                mPlayTask = new MoivePlayer.PlayTask(moivePlayer,this);
                isPlaying = true;
                updateControl();
                mPlayTask.execute();
            }else{
                Log.d("PlayMoiveActivity", "unable to play");
            }
        }
    }

    private void adjustRadio(float videoAspectRadio) {
        Log.d(TAG, "videoAspectRadio:" + videoAspectRadio);
        int viewWidth=mVideoView.getWidth();
        int viewHeight = mVideoView.getHeight();
        Log.d(TAG, "adjustRadio: " + viewHeight + " --- " + viewWidth);
        float viewAspectRadio = viewHeight * 1.0f / viewWidth;
        Log.d(TAG, "viewAspectRadio:" + viewAspectRadio);
        int newWidth,newHeight;
        //如果视频的高/宽大于视图的 说明视图的高不够，需要以高（小值）为基准缩放;否则以宽为基准
        if (videoAspectRadio > viewAspectRadio) {
            newHeight=viewHeight;
            newWidth= (int) (viewHeight/videoAspectRadio);
        }else{
            newWidth=viewWidth;
            newHeight= (int) (viewWidth*videoAspectRadio);
        }
        int xOff = (viewWidth - newWidth) / 2;
        int yOff=(viewHeight - newHeight) / 2;
        Matrix matrix=new Matrix();
        mVideoView.getTransform(matrix);
        matrix.setScale((float) newWidth/viewWidth,(float) newHeight/viewHeight);
//        matrix.postRotate(15, viewWidth / 2, viewHeight / 2);
        matrix.postTranslate(xOff, yOff);
        mVideoView.setTransform(matrix);


    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable() called with:  width = [" + width + "], height = [" + height + "]");
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureDestroyed: ");
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }


    @Override
    public void playBackStopped() {
        isPlaying=false;
        mPlayTask=null;
        updateControl();
    }

    private void updateControl() {
        Button button = (Button) findViewById(R.id.btn_controller);
        if (isPlaying) {
            button.setText("stop");
        }else{
            button.setText("start");
        }
    }
}
