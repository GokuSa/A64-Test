package com.maowei.uvccamera1;
/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MainActivity.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageButton;

import com.maowei.widget.UVCCameraTextureView;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity  {
    private static final String TAG = "MainActivity";
    // for thread pool
    private static final int CORE_POOL_SIZE = 1;		// initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;			// maximum threads
    private static final int KEEP_ALIVE_TIME = 10;		// time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER
		= new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
			TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
	private UVCCamera mUVCCamera;
	private UVCCameraTextureView mUVCCameraView;
	// for open&start / stop&close camera preview
	private ImageButton mCameraButton;
	private Surface mPreviewSurface;

	public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(10);

    @Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		mCameraButton = (ImageButton)findViewById(R.id.camera_button);
		mCameraButton.setOnClickListener(mOnClickListener);

		mUVCCameraView = (UVCCameraTextureView)findViewById(R.id.UVCCameraTextureView1);
//		mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
		mUSBMonitor.register();

		final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
		final List<UsbDevice> deviceList = mUSBMonitor.getDeviceList(filter);
		Log.d(TAG, "deviceList:" + deviceList);
		if (deviceList.size() > 0) {
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					mUSBMonitor.requestPermission(deviceList.get(0));

				}
			}, 5000);

		}else{
			Log.e(TAG, "onCreate: no camera device");
		}
	}



    @Override
	public void onResume() {
		super.onResume();
		/*if (mUVCCamera != null){
            Log.d(TAG, "onResume() called");
            mUVCCamera.startPreview();
        }*/
	}

	@Override
	public void onPause() {
		/*if (mUVCCamera != null)
			mUVCCamera.stopPreview();*/
//		mUSBMonitor.unregister();
		super.onPause();
	}

	@Override
	public void onDestroy() {
		mUSBMonitor.unregister();

		if (mUVCCamera != null) {
			mUVCCamera.destroy();
			mUVCCamera = null;
		}
		if (mUSBMonitor != null) {
			mUSBMonitor.destroy();
			mUSBMonitor = null;
		}
		mUVCCameraView = null;
		mCameraButton = null;
//        avcCodec.StopThread();
		super.onDestroy();
	}

	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			if (mUVCCamera == null) {
//				CameraDialog.showDialog(MainActivity.this);
				}
			else {
				mUVCCamera.destroy();
				mUVCCamera = null;
			}
		}
	};

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
//			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            Log.d(TAG, "onConnect");
            if (mUVCCamera != null)
				mUVCCamera.destroy();
			mUVCCamera = new UVCCamera();
			EXECUTER.execute(new Runnable() {
				@Override
				public void run() {
					mUVCCamera.open(ctrlBlock);
					mUVCCamera.setStatusCallback(new IStatusCallback() {
						@Override
						public void onStatus(final int statusClass, final int event, final int selector,
											 final int statusAttribute, final ByteBuffer data) {
							Log.d(TAG, "onStatus() called with: statusClass = [" + statusClass + "], event = [" + event + "], selector = [" + selector + "], statusAttribute = [" + statusAttribute + "], data = [" + data + "]");
						}
					});
					mUVCCamera.setButtonCallback(new IButtonCallback() {
						@Override
						public void onButton(final int button, final int state) {
							Log.d(TAG, "onButton() called with: button = [" + button + "], state = [" + state + "]");

						}
					});
//					mUVCCamera.setPreviewTexture(mUVCCameraView.getSurfaceTexture());
					if (mPreviewSurface != null) {
						mPreviewSurface.release();
						mPreviewSurface = null;
					}
					try {
						mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
					} catch (final IllegalArgumentException e) {
						// fallback to YUV mode
						/*try {
							mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT*//*, UVCCamera.DEFAULT_PREVIEW_MODE*//*);
						} catch (final IllegalArgumentException e1) {
							mUVCCamera.destroy();
							mUVCCamera = null;
						}*/
					}
					if (mUVCCamera != null) {
						final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
						if (st != null)
							mPreviewSurface = new Surface(st);
//						mUVCCamera.setPreviewDisplay(mPreviewSurface);
                        mUVCCamera.setPreviewTexture(st);

						mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.FRAME_FORMAT_MJPEG/*UVCCamera.PIXEL_FORMAT_NV21*/);
						mUVCCamera.startPreview();
                        Log.d(TAG, "StartEncoderThread");
//                        avcCodec = new AvcEncoder(640,480,30,8500*1000);
//                        avcCodec.StartEncoderThread();
					}
				}
			});
		}

		@Override
		public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
			// XXX you should check whether the comming device equal to camera device that currently using
			if (mUVCCamera != null) {
				mUVCCamera.close();
				if (mPreviewSurface != null) {
					mPreviewSurface.release();
					mPreviewSurface = null;
				}
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



//    int size = (int) (UVCCamera.DEFAULT_PREVIEW_WIDTH * UVCCamera.DEFAULT_PREVIEW_HEIGHT * 1.5);

    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {
//            Log.d(TAG, "onFrame() called with: frame =");
           /* if (YUVQueue.size() >= 10) {
                YUVQueue.poll();
            }*/
			Log.d(TAG, "onFrame: ");
//            Log.d(TAG, "outData"+outData.length+"-----"+Arrays.toString(outData));
//            Log.d(TAG, "outData"+Arrays.toString(frame.array()));
//            YUVQueue.add(outData);
        }
    };


	// if you need frame data as byte array on Java side, you can use this callback method with UVCCamera#setFrameCallback
	// if you need to create Bitmap in IFrameCallback, please refer following snippet.
/*	final Bitmap bitmap = Bitmap.createBitmap(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, Bitmap.Config.RGB_565);
	private final IFrameCallback mIFrameCallback = new IFrameCallback() {
		@Override
		public void onFrame(final ByteBuffer frame) {
			frame.clear();
			synchronized (bitmap) {
				bitmap.copyPixelsFromBuffer(frame);
			}
			mImageView.post(mUpdateImageTask);
		}
	};
	
	private final Runnable mUpdateImageTask = new Runnable() {
		@Override
		public void run() {
			synchronized (bitmap) {
				mImageView.setImageBitmap(bitmap);
			}
		}
	}; */
}
