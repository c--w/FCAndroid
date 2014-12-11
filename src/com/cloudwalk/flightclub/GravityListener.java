package com.cloudwalk.flightclub;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class GravityListener implements SensorEventListener {
	private SensorManager mSensorMgr;
	private Context mContext;
	private OnGravityListener mEventListener;
	Sensor gravitySensor;

	public interface OnGravityListener {
		public void onEvent(SensorEvent event);
	}

	public void setOnEventListener(OnGravityListener listener) {
		mEventListener = listener;
	}

	public GravityListener(Context context) {
		mContext = context;
	}

	public void resume() {
		mSensorMgr = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
		if (mSensorMgr == null) {
			throw new UnsupportedOperationException("Sensors not supported");
		}
		gravitySensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		boolean supported = mSensorMgr.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI);
		if (!supported) {
			mSensorMgr.unregisterListener(this, gravitySensor);
			throw new UnsupportedOperationException("Gravity not supported");
		}
	}

	public void pause() {
		if (mSensorMgr != null) {
			mSensorMgr.unregisterListener(this, gravitySensor);
			mSensorMgr.registerListener(null, gravitySensor, SensorManager.SENSOR_DELAY_UI);
			mSensorMgr = null;
			gravitySensor = null;
		}
	}

	public void onSensorChanged(SensorEvent event) {
		mEventListener.onEvent(event);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

}
