package com.lxl.audio;

import android.app.Application;

public class App extends Application {
	private String TAG = "App";
	private static App instance;
	public App() {
	}

	public synchronized static App getInstance() {

		return instance;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		instance=this;
	}



}
