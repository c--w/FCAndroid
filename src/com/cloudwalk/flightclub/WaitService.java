package com.cloudwalk.flightclub;

import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.cloudwalk.server.Client;

public class WaitService extends IntentService  {

	public WaitService(String name) {
		super(name);
	}

	public WaitService() {
		super("FC Wait Service");
	}

	final static String TAG = "FC WaitService";
	public static final int NEW_USERS = 29879;
	public static final int MIN = 60*1000;
	private PendingIntent pendingIntent;
	private AlarmManager alarmManager;
	private SharedPreferences prefs = null;
	boolean running = true;

	@Override
	public void onCreate() {
		super.onCreate();
		try {
			Log.w(TAG, "onCreate");
			prefs = PreferenceManager.getDefaultSharedPreferences(this);
			alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			Intent intent = new Intent(this, WaitService.class);
			pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		} catch (Exception e) {
			Log.e(TAG, e.toString(), e);
		}
	};


	@Override
	public void onDestroy() {
		super.onDestroy();
		if(running)
			alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 5 * MIN, pendingIntent);
		Log.i(TAG, "onDestroy");
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.i(TAG, "onStartCommand");
		try {
			Client.ROOM = "3Any";
			running = prefs.getBoolean("wait_service_running", false);
			int interval = Integer.parseInt(prefs.getString("wait_online", "10"));
			long start = System.currentTimeMillis();
			while (running) {
				long now = System.currentTimeMillis();
				if(now - start > interval * MIN)
					break;
				Client.send("PING");
				JSONObject rooms = new JSONObject(Client.send("ROOMS"));
				if(rooms.length()>1) {
					NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					Intent notificationIntent = new Intent(this, ChooseActivity.class);
					notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					notificationIntent.putExtra("net_online", true);
					notificationIntent.setAction("android.intent.action.MAIN");
					notificationIntent.addCategory("android.intent.category.DEFAULT");
					PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
					Notification notification = new NotificationCompat.Builder(this)
			         .setContentTitle("Flight Club")
			         .setContentText("There are new users waiting for/playing an online game!")
			         .setSmallIcon(R.drawable.ic_launcher)
			         .setContentIntent(contentIntent)
			         .setAutoCancel(true)
			         .setLights(Color.BLUE, 1000, 500)
			         .build();

					mNotificationManager.notify(NEW_USERS, notification);
					running = false;
					break;
				}
				Thread.sleep(1000);
				running = prefs.getBoolean("wait_service_running", false);
			}
			running = false;
			prefs.edit().putBoolean("wait_service_running", false).commit();
			//alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 30000l, pendingIntent);
		} catch (Exception e) {
			Log.e(TAG, e.toString(), e);
		}
	}

}
