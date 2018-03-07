package org.camera.activity;

import android.app.ActivityManager;
import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.example.camerapreview.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.GL_COMPILE_STATUS;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_LINK_STATUS;
import static android.opengl.GLES20.GL_TRIANGLE_FAN;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glDeleteShader;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetProgramInfoLog;
import static android.opengl.GLES20.glGetProgramiv;
import static android.opengl.GLES20.glGetShaderInfoLog;
import static android.opengl.GLES20.glGetShaderiv;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glUniform4f;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

/**
 * author:
 * 时间:2017/9/11
 * qq:1220289215
 * 类描述：
 */

public class PractiseActivity extends AppCompatActivity {
    private static final String TAG = "PractiseActivity";
    private GLSurfaceView mGlSurfaceView;
    private boolean renderSet = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGlSurfaceView = new GLSurfaceView(this);
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        boolean support = activityManager.getDeviceConfigurationInfo().reqGlEsVersion > 0x2000;
        Log.d(TAG, "support:" + support);
        if (support) {

            renderSet = true;
            mGlSurfaceView.setEGLContextClientVersion(2);
            mGlSurfaceView.setRenderer(new MyRender(this));
            mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }

        setContentView(mGlSurfaceView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (renderSet) {
            mGlSurfaceView.onResume();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (renderSet) {
            mGlSurfaceView.onPause();
        }

    }

    private final class MyRender implements GLSurfaceView.Renderer {
        private static final String TAG = "MyRender";
        private Context mContext;
        private int mProgram;
        private FloatBuffer mFloatBuffer;
        private float[] vertex = {
                0, 0,
                -.5f, -.5f,
                .5f, -.5f,
                .5f, .5f,
                -.5f, .5f,
                -.5f, -.5f,

        };
        private int mColorLocation;

        public MyRender(Context context) {
            mContext = context;
            mFloatBuffer = ByteBuffer.allocateDirect(vertex.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(vertex);
        }

        private String loadShader(int resId) {
            StringBuilder stringBuilder = new StringBuilder();
            InputStream inputStream = mContext.getResources().openRawResource(resId);
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            try {
                while ((line = br.readLine()) != null) {
                    stringBuilder.append(line).append('\n');
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return stringBuilder.toString();
        }

        private int compileShader(int type, String source) {
            int shaderObjectId = glCreateShader(type);
            if (shaderObjectId == 0) {
                Log.d(TAG, "fail to create shader ");
                return 0;
            }
            //upload
            glShaderSource(shaderObjectId, source);
            //compile
            glCompileShader(shaderObjectId);
            final int[] result = new int[1];
            glGetShaderiv(shaderObjectId, GL_COMPILE_STATUS, result, 0);
            Log.d(TAG, source + "\n compile result" + glGetShaderInfoLog(shaderObjectId));
            if (result[0] == 0) {
                glDeleteShader(shaderObjectId);
                Log.d(TAG, "compileShader: fail to compile");
                return 0;
            }
            return shaderObjectId;
        }

        private int createProgram(int vertexId, int fragmentId) {
            int programId = glCreateProgram();
            if (programId == 0) {
                Log.d(TAG, "createProgram: fail to create");
                return 0;
            }
            glAttachShader(programId, vertexId);
            glAttachShader(programId, fragmentId);
            glLinkProgram(programId);
            final int[] result = new int[1];
            glGetProgramiv(programId, GL_LINK_STATUS, result, 0);
            Log.d(TAG, "result " + glGetProgramInfoLog(programId));
            if (result[0] == 0) {
                Log.d(TAG, "createProgram: fail to link program");
                glDeleteProgram(programId);
                return 0;
            }
            return programId;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            String vertexShader = loadShader(R.raw.vertex_shader);
            String fragmentShader = loadShader(R.raw.fragment_shader);
            int vertexShaderId = compileShader(GL_VERTEX_SHADER, vertexShader);
            int fragmentShaderId = compileShader(GL_FRAGMENT_SHADER, fragmentShader);
            mProgram = createProgram(vertexShaderId, fragmentShaderId);

            mFloatBuffer.position(0);
            int positionLocation = glGetAttribLocation(mProgram, "a_Position");
            glVertexAttribPointer(positionLocation, 2, GL_FLOAT, false, 0, mFloatBuffer);
            glEnableVertexAttribArray(positionLocation);

            mColorLocation = glGetUniformLocation(mProgram, "u_Color");
            glUseProgram(mProgram);

        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {

        }

        @Override
        public void onDrawFrame(GL10 gl) {
            glClear(GLES20.GL_COLOR_BUFFER_BIT);
            glUniform4f(mColorLocation, 1f, 1f, 1f, 1f);
            glDrawArrays(GL_TRIANGLE_FAN, 0, 6);
        }
    }


}
