/*
 * Copyright (C) 2010 ZXing authors
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

package com.google.zxing.common;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.util.Log;

/**
 * Finishes an activity after a period of inactivity if the device is on battery
 * power. <br/>
 * <br/>
 * 
 * 该活动监控器全程监控扫描活跃状态，与CaptureActivity生命周期相同
 */
public class InactivityTimer {

	private static final String TAG = InactivityTimer.class.getSimpleName();

	/**
	 * 如果在5min内扫描器没有被使用过，则自动finish掉activity
	 */
	private static final long INACTIVITY_DELAY_MS = 5 * 60 * 1000L;

	/**
	 * 在本app中，此activity即为CaptureActivity
	 */
	private final Activity activity;
	/**
	 * 接受系统广播：手机是否连通电源
	 */
	private final BroadcastReceiver powerStatusReceiver;
	private boolean registered;
	private AsyncTask<?, ?, ?> inactivityTask;

	public InactivityTimer(Activity activity) {
		this.activity = activity;
		powerStatusReceiver = new PowerStatusReceiver();
		registered = false;
		onActivity();
	}

	/**
	 * 首先终止之前的监控任务，然后新起一个监控任务
	 */
	public synchronized void onActivity() {
		cancel();
		inactivityTask = new InactivityAsyncTask();
		Runnable.execAsync(inactivityTask);
	}

	public synchronized void onPause() {
		cancel();
		if (registered) {
			activity.unregisterReceiver(powerStatusReceiver);
			registered = false;
		}
		else {
			Log.w(TAG, "PowerStatusReceiver was never registered?");
		}
	}

	public synchronized void onResume() {
		Log.d(TAG, "here");
		if (registered) {
			Log.w(TAG, "PowerStatusReceiver was already registered?");
		} else {
			activity.registerReceiver(powerStatusReceiver, new IntentFilter(
					Intent.ACTION_BATTERY_CHANGED));
			registered = true;
		}
		onActivity();
	}

	/**
	 * 取消监控任务
	 */
	private synchronized void cancel() {
		AsyncTask<?, ?, ?> task = inactivityTask;
		if (task != null) {
			task.cancel(true);
			inactivityTask = null;
		}
	}

	public void shutdown() {
		cancel();
	}

	/**
	 * 监听是否连通电源的系统广播。如果连通电源，则停止监控任务，否则重启监控任务
	 */
	private final class PowerStatusReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
				// 0 indicates that we're on battery
				boolean onBatteryNow = intent.getIntExtra(
						BatteryManager.EXTRA_PLUGGED, -1) <= 0;
				if (onBatteryNow) {
					InactivityTimer.this.onActivity();
				}
				else {
					InactivityTimer.this.cancel();
				}
			}
		}
	}

	/**
	 * 该任务很简单，就是在INACTIVITY_DELAY_MS时间后终结activity
	 */
	private final class InactivityAsyncTask extends
			AsyncTask<Object, Object, Object> {
		@Override
		protected Object doInBackground(Object... objects) {
			try {
				Thread.sleep(INACTIVITY_DELAY_MS);
				Log.i(TAG, "Finishing activity due to inactivity");
				activity.finish();
			}
			catch (InterruptedException e) {
				// continue without killing
			}
			return null;
		}
	}

}
