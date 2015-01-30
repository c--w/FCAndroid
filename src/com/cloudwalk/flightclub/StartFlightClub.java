package com.cloudwalk.flightclub;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.SensorEvent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.cloudwalk.client.Glider;
import com.cloudwalk.client.GliderUser;
import com.cloudwalk.client.Task;
import com.cloudwalk.client.XCCameraMan;
import com.cloudwalk.client.XCModelViewer;
import com.cloudwalk.flightclub.GravityListener.OnGravityListener;
import com.cloudwalk.framework3d.ClockObserver;
import com.cloudwalk.framework3d.ModelView;
import com.cloudwalk.framework3d.ModelViewRenderer;
import com.cloudwalk.server.Client;
import com.cloudwalk.server.XCGameServer;
import com.cloudwalk.server.XCGameServerOnline;
import com.cloudwalk.startup.ModelEnv;
import com.cloudwalk.startup.ModelViewerThin;

public class StartFlightClub extends Activity implements ModelEnv, OnTouchListener, OnGravityListener, ClockObserver {

	String task;
	int pilotType;
	String hostPort = null;
	int[] typeNums = new int[4];
	ModelViewerThin modelViewerThin = null;
	ModelViewRenderer renderer;
	MediaPlayer mp = new MediaPlayer();
	AudioManager audioManager;
	private SoundPool soundPool;
	float volume = 0;
	int[] soundIds;
	int[] streamIDs;
	boolean sinking = false;
	float currentSpeed;
	ModelView surfaceView;
	GestureDetector detector;
	GravityListener mGravity;
	int control_type;
	float compassSize;
	int glider_color;
	String playerName;

	boolean finished = false, landed = false, flying = false;

	XCGameServer server;
	XCGameServerOnline serverOnline;
	boolean broadcasting = false;
	boolean serving = false;
	boolean wasPaused;
	boolean net = false;
	Menu menu;
	SharedPreferences prefs;
	ImageView compass = null;

	Handler h = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			try {
				if (msg != null && msg.getData() != null && msg.getData().getString("msg") != null) {
					Toast.makeText(getApplicationContext(), msg.getData().getString("msg"), Toast.LENGTH_LONG).show();
					return;
				}
				GliderUser glider = ((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser;
				if (flying == true && glider.getLanded()) {
					flying = false;
					sinking = false;
					soundPool.stop(streamIDs[1]);
					soundPool.play(soundIds[4], volume, volume, 1, 0, 1);
				}
				if (glider.finished && !finished && ((XCModelViewer) modelViewerThin).xcModel.task.type == Task.TIME) {
					try {
						soundPool.play(soundIds[5], volume, volume, 1, 0, 1);
						int best_time = prefs.getInt("best_time0" + task + pilotType, 100000);
						int current_time = (int) glider.timeFinished;
						Log.i("FC StartFlightClub", "currenttime " + current_time + " " + best_time);
						if (current_time < best_time) {
							submitScore(pilotType, current_time);
						}
					} catch (Exception e) {
						Log.e("FC", e.getMessage(), e);
					}
					finished = true;
				} else if (glider.distanceFlown != 0 && glider.getLanded() && !landed && ((XCModelViewer) modelViewerThin).xcModel.task.type == Task.DISTANCE) {
					try {
						int best_distance = prefs.getInt("best_time0" + task + pilotType, 0);
						int current_distance = (int) (glider.distanceFlown() / 2 * 100);
						Log.i("FC StartFlightClub", "current_distance " + current_distance + " " + best_distance);
						if (current_distance > best_distance) {
							submitScore(pilotType, current_distance);
						}
					} catch (Exception e) {
						Log.e("FC", e.getMessage(), e);
					}
					landed = true;
				} else if (glider.finished && !finished && ((XCModelViewer) modelViewerThin).xcModel.task.type == Task.TIME_PRECISE) {
					try {
						soundPool.play(soundIds[5], volume, volume, 1, 0, 1);
						int best_time = prefs.getInt("best_time0" + task + pilotType, 1000000);
						int current_time = (int) (glider.timeFinished * 100);
						Log.i("FC StartFlightClub", "currenttime " + current_time + " " + best_time);
						if (current_time < best_time) {
							submitScore(pilotType, current_time);
						}
					} catch (Exception e) {
						Log.e("FC", e.getMessage(), e);
					}
					finished = true;
				}
				View v = findViewById(R.id.startbuttons);
				if (((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.getOnGround()) {
					if (v.getVisibility() == View.GONE)
						v.setVisibility(View.VISIBLE);
				} else {
					if (v.getVisibility() == View.VISIBLE)
						v.setVisibility(View.GONE);
				}
				((TextView) findViewById(R.id.info)).setText(Html.fromHtml(surfaceView.getInfoText()));
				((SeekBar) findViewById(R.id.vario)).setProgress((int) (50 + ((XCModelViewer) modelViewerThin).xcModel.gliderManager.theGlider()
						.getActualSink() / 0.37f * 50));
				if (flying && glider.airv < 0 && !sinking && prefs.getBoolean("sink_tone", true)) {
					Log.w("FC", "startsink");
					streamIDs[7] = soundPool.play(soundIds[7], volume, volume, 1, -1, 1f + glider.airv * 3);
					sinking = true;
				} else if (flying && glider.airv >= 0) {
					//Log.w("FC", "stopsink");
					soundPool.stop(streamIDs[7]);
					sinking = false;
				}
				if (flying && sinking) {
					soundPool.setRate(streamIDs[7], 1f + glider.airv * 3);
				}
				if (flying == true && prefs.getBoolean("ambient_sound", true)) {
					if (currentSpeed != glider.getSpeed()) {
						soundPool.setRate(streamIDs[1], (float) Math.sqrt(glider.getSpeed() / 1.7));
						currentSpeed = glider.getSpeed();
					}
					int rnd = (int) (Math.random() * 3000);
					if (rnd == 0)
						soundPool.play(soundIds[2], (float) (volume * Math.random()) * .05f, (float) (volume * Math.random()) * .05f, 1, 0, 1);
					else if (rnd == 1)
						soundPool.play(soundIds[3], (float) (volume * Math.random()) * .05f, (float) (volume * Math.random()) * .05f, 1, 0, 1);
					else if (rnd == 2)
						soundPool.play(soundIds[6], (float) (volume * Math.random()) * .05f, (float) (volume * Math.random()) * .05f, 1, 0, 1);

				} else if (glider.racing && !glider.getLanded() && flying == false) {
					flying = true;
					if (prefs.getBoolean("ambient_sound", true))
						streamIDs[1] = soundPool.play(soundIds[1], volume / 2, volume / 2, 1, -1, (float) Math.sqrt(glider.getSpeed() / 1.7));
				}
				Glider active_glider = ((XCModelViewer) modelViewerThin).xcModel.gliderManager.theGlider();
				if (active_glider != null) {
					float angle = (float) Math.toDegrees(Math.atan2(active_glider.v[0], active_glider.v[1]));

					if (angle < 0) {
						angle += 360;
					}

					Matrix matrix = new Matrix();
					compass.setScaleType(ScaleType.MATRIX); // required
					matrix.postRotate((float) angle, compass.getDrawable().getBounds().width() / 2, compass.getDrawable().getBounds().height() / 2);
					float scale = compassSize / compass.getDrawable().getBounds().width();
					matrix.postScale(scale, scale);
					compass.setImageMatrix(matrix);
				}

			} catch (Exception e) {
				// TODO: handle exception
			}
		}
	};

	private void submitScore(final int pilotType, final int current_time) {
		if (wasPaused) {
			Toast.makeText(this, "Don't pause the game if you want to submit score to Google Leaderboards!", Toast.LENGTH_LONG).show();
			wasPaused = false;
			return;
		}
		prefs.edit().putInt("best_time0" + task + pilotType, current_time).commit();

		AlertDialog.Builder builder = new AlertDialog.Builder(StartFlightClub.this);

		builder.setTitle("HIGH SCORE!!");
		builder.setMessage("Do you want to save your score to Google Leaderboards?");

		builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(getApplicationContext(), ScoreActivity.class);
				intent.putExtra("task", task);
				intent.putExtra("pilot_type", pilotType);
				intent.putExtra("best_time", current_time);
				startActivity(intent);
				dialog.dismiss();
			}

		}).setNegativeButton("NO", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Do nothing
				dialog.dismiss();
			}
		}).show();

	}

	private void startWorldIfNeeded() {
		if (((XCModelViewer) modelViewerThin).xcModel == null) {
			modelViewerThin.stop();
			modelViewerThin.init((ModelEnv) StartFlightClub.this);
			((XCModelViewer) modelViewerThin).start();
			((XCModelViewer) modelViewerThin).clock.addObserver(StartFlightClub.this);
		}
		landed = false;
		finished = false;
		wasPaused = false;
		if (control_type == 1)
			mGravity.resume();

	}

	private void startGameServerOnline(int port) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				serverOnline = new XCGameServerOnline(Tools.SERVER_PORT, task, pilotType, playerName, glider_color);
				serving = true;
				serverOnline.serveClients();
			}
		}).start();
	}

	private void startGameServer(int port) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				server = new XCGameServer(Tools.SERVER_PORT, task, pilotType);
				serving = true;
				server.serveClients();
			}
		}).start();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_startwifigame);
		compass = (ImageView) findViewById(R.id.imageViewCompass);

		Resources r = getResources();
		compassSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, r.getDisplayMetrics());
		task = "default";

		pilotType = 0; // 0 = pg, 1 = hg, 2 = sp, 3 = ballon

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		control_type = Integer.parseInt(prefs.getString("control_type", "0"));
		if (control_type == 1) {
			Log.i("FC", "Start gravity listener");
			mGravity = new GravityListener(this);
			mGravity.setOnEventListener(this);
		}
		Intent intent = getIntent();
		pilotType = intent.getIntExtra("glider", 0);
		task = intent.getStringExtra("task");
		glider_color = prefs.getInt("glider_color", Color.BLUE);
		playerName = prefs.getString("playerName", "Player");

		int numpg = Integer.parseInt(prefs.getString("numpg", "3"));
		int numhg = Integer.parseInt(prefs.getString("numhg", "3"));
		int numsp = Integer.parseInt(prefs.getString("numsp", "3"));
		typeNums = new int[] { numpg, numhg, numsp, 0 };
		if (intent.hasExtra("net")) {
			if (intent.hasExtra("server")) {
				String server = intent.getStringExtra("server");
				if (server.contains(":")) {
					hostPort = server;
				} else {
					hostPort = intent.getStringExtra("server") + ":" + Tools.SERVER_PORT;
				}
			} else {
				String myip = Tools.getIPAddress(true);
				startGameServer(Tools.SERVER_PORT);
				while (!serving)
					SystemClock.sleep(10);
				broadcasting = true;
				startBroadcast(pilotType, task);
				hostPort = myip + ":" + Tools.SERVER_PORT;
			}
			((TextView) findViewById(R.id.pause)).setText("x");
			net = true;
		} else if (intent.hasExtra("net_online")) {
			String myip = Tools.getIPAddress(true);
			Client.MY_ID = prefs.getInt("online_id", new Random(System.currentTimeMillis()).nextInt(Integer.MAX_VALUE));
			startGameServerOnline(Tools.SERVER_PORT);
			while (!serving)
				SystemClock.sleep(10);
			hostPort = myip + ":" + Tools.SERVER_PORT;
			((TextView) findViewById(R.id.pause)).setText("x");
			net = true;
		}
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		soundPool = new SoundPool(8, AudioManager.STREAM_MUSIC, 0);
		soundIds = new int[8];
		streamIDs = new int[8];
		soundIds[0] = soundPool.load(this, R.raw.beep0, 1);
		soundIds[1] = soundPool.load(this, R.raw.wind, 1);
		soundIds[2] = soundPool.load(this, R.raw.hawk, 1);
		soundIds[3] = soundPool.load(this, R.raw.crow, 1);
		soundIds[4] = soundPool.load(this, R.raw.landed, 1);
		soundIds[5] = soundPool.load(this, R.raw.finish, 1);
		soundIds[6] = soundPool.load(this, R.raw.hawk2, 1);
		soundIds[7] = soundPool.load(this, R.raw.sink, 1);
		audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		float actualVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		float maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		volume = actualVolume / maxVolume;
		/*
		 * } else { if (s[2].indexOf(":") > 0) { hostPort = s[2]; typeNums = null; } else { hostPort = null; try { for (int i = 0; i < 3; i++) { typeNums[i] =
		 * parseInt(s[i + 2]); } } catch (Exception e) { Log.i("FC", "Error reading AI glider numbers: " + e); typeNums = new int[] {2, 5, 2}; } } }
		 */
		final ModelView modelView = (ModelView) findViewById(R.id.xcmodelview);
		((SeekBar) findViewById(R.id.vario)).setEnabled(false);
		// Check if the system supports OpenGL ES 2.0.
		final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
		final boolean supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000;

		if (supportsEs2) {
			// Request an OpenGL ES 2.0 compatible context.
			modelView.setEGLContextClientVersion(2);
			// Set the renderer to our demo renderer, defined below.
			renderer = new ModelViewRenderer(this, modelView);
			modelView.setRenderer(renderer);
		} else {
			// This is where you could create an OpenGL ES 1.x compatible
			// renderer if you wanted to support both ES 1 and ES 2.
			return;
		}

		surfaceView = modelView;
		modelViewerThin = new XCModelViewer(modelView);
		modelView.modelViewer = (XCModelViewer) modelViewerThin;
		modelView.setOnTouchListener(StartFlightClub.this);
		renderer.modelViewer = (XCModelViewer) modelViewerThin;

		modelView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				// Remove it here unless you want to get this callback for EVERY
				// layout pass, which can get you into infinite loops if you
				// ever
				// modify the layout from within this method.
				modelView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
				startWorldIfNeeded();
			}
		});
		findViewById(R.id.start_race).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				startWorldIfNeeded();
				((XCModelViewer) modelViewerThin).xcModel.start(pilotType);
				findViewById(R.id.startbuttons).setVisibility(View.GONE);
			}
		});
		findViewById(R.id.view0).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				((XCModelViewer) modelViewerThin).xcModel.xcCameraMan.setMode(XCCameraMan.USER);
			}
		});
		findViewById(R.id.view1).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				((XCModelViewer) modelViewerThin).xcModel.xcCameraMan.setMode(XCCameraMan.GAGGLE);
			}
		});
		findViewById(R.id.view2).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				((XCModelViewer) modelViewerThin).xcModel.xcCameraMan.setMode(XCCameraMan.PLAN);
			}
		});
		findViewById(R.id.view3).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				((XCModelViewer) modelViewerThin).xcModel.xcCameraMan.setMode(XCCameraMan.NODE);
			}
		});
		findViewById(R.id.view4).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				((XCModelViewer) modelViewerThin).xcModel.xcCameraMan.setMode(XCCameraMan.TASK);
			}
		});
		findViewById(R.id.view5).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				((XCModelViewer) modelViewerThin).xcModel.xcCameraMan.setMode(XCCameraMan.PILOT);
			}
		});
		findViewById(R.id.zoomin).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				((XCModelViewer) modelViewerThin).xcModel.xcCameraMan.pullIn();
			}
		});
		findViewById(R.id.zoomout).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				((XCModelViewer) modelViewerThin).xcModel.xcCameraMan.pullOut();
			}
		});
		findViewById(R.id.pause).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (net == true) {
					((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.hitTheSpuds();
				} else {
					((XCModelViewer) modelViewerThin).xcModel.togglePause();
					wasPaused = true;
				}
			}
		});
		if (!prefs.getBoolean("show_controls", true)) {
			findViewById(R.id.controls).setVisibility(View.GONE);
		} else
			findViewById(R.id.controls).setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					v.setVisibility(View.GONE);
				}
			});

		detector = new GestureDetector(this, new OnGestureListener() {

			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void onShowPress(MotionEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void onLongPress(MotionEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public boolean onDown(MotionEvent e) {
				// TODO Auto-generated method stub
				return false;
			}
		});
		detector.setOnDoubleTapListener(new OnDoubleTapListener() {

			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public boolean onDoubleTapEvent(MotionEvent e) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public boolean onDoubleTap(MotionEvent e) {
				View v = findViewById(R.id.overlay);
				View info = findViewById(R.id.info);
				float x = e.getX();
				float y = e.getY();
				if (x > v.getWidth() * 2 / 7 && x < v.getWidth() * 5 / 7 && y > v.getHeight() * 2 / 7 && y < v.getHeight() * 5 / 7) {
					if (v.getVisibility() == View.GONE)
						v.setVisibility(View.VISIBLE);
					else
						v.setVisibility(View.GONE);
					return false;
				} else if (x < v.getWidth() * 2 / 7 && y > v.getHeight() * 5 / 7) {
					if (info.getVisibility() == View.GONE)
						info.setVisibility(View.VISIBLE);
					else
						info.setVisibility(View.GONE);
					return false;

				}
				return false;
			}
		});

	}

	@Override
	public void onBackPressed() {
		if (modelViewerThin != null)
			((XCModelViewer) modelViewerThin).stop();
		if (server != null)
			server.stop();
		if (serverOnline != null)
			serverOnline.stop();
		broadcasting = false;
		modelViewerThin = null;
		super.onBackPressed();
	};

	@Override
	public void onDestroy() {
		if (modelViewerThin != null)
			((XCModelViewer) modelViewerThin).stop();
		if (server != null)
			server.stop();
		if (serverOnline != null)
			serverOnline.stop();
		broadcasting = false;
		modelViewerThin = null;
		super.onDestroy();
	};

	@Override
	public void onStop() {
		if (modelViewerThin != null && ((XCModelViewer) modelViewerThin).clock != null) {
			((XCModelViewer) modelViewerThin).clock.stop();
		}
		super.onStop();
	};

	@Override
	protected void onRestart() {
		super.onRestart();
		if (modelViewerThin != null && ((XCModelViewer) modelViewerThin).clock != null) {
			((XCModelViewer) modelViewerThin).clock.start();
		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		soundPool.autoPause();
		if (control_type == 1)
			mGravity.pause();
		if (modelViewerThin != null && ((XCModelViewer) modelViewerThin).clock != null) {
			((XCModelViewer) modelViewerThin).clock.paused = true;
			try {
				if (((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.racing)
					wasPaused = true;
			} catch (Exception e) {
			}
		}
	};

	@Override
	protected void onResume() {
		super.onResume();
		if (control_type == 1)
			mGravity.resume();
		soundPool.autoResume();
		if (modelViewerThin != null && ((XCModelViewer) modelViewerThin).clock != null)
			((XCModelViewer) modelViewerThin).clock.paused = false;
	};

	@Override
	public String getTask() {
		return task;
	}

	@Override
	public int getPilotType() {
		return pilotType;
	}

	@Override
	public String getHostPort() {
		return hostPort;
	}

	@Override
	public int[] getTypeNums() {
		return typeNums;
	}

	public InputStream openFile(String name) {
		InputStream is = null;
		try {
			is = getAssets().open(name);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return is;
	}

	private String lastAudio = "";

	@Override
	public void play(float sound, int index, int loop) {
		// Log.w("FC", "Playing sound: "+sound);
		streamIDs[index] = soundPool.play(soundIds[index], volume, volume, 1, loop, sound);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.handleTouch(v, event);
		((ModelView) findViewById(R.id.xcmodelview)).handleTouch(v, event);
		detector.onTouchEvent(event);
		return true;
	}

	@Override
	public void onEvent(SensorEvent event) {
		try {
			((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.handleGravity(event);
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	@Override
	public void tick(float t, float dt) {
		h.sendEmptyMessage(0);
	}

	public boolean onCreateOptionsMenu(android.view.Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		this.menu = menu;
		return true;
	};

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		try {
			if (item.getItemId() == R.id.settings) {
				Intent launchPreferencesIntent = new Intent(this, Preferences.class);
				startActivity(launchPreferencesIntent);
				return true;
			} else if (item.getItemId() == R.id.leaderboards) {
				Intent launchScoresIntent = new Intent().setClass(this, ScoreActivity.class);
				launchScoresIntent.putExtra("show_all", true);
				startActivity(launchScoresIntent);
				return true;
			}
		} catch (Exception e) {
			Log.e("FC", e.toString(), e);
		}
		return true;
	}

	private void startBroadcast(final int glider, final String task) {
		broadcasting = true;
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					// Keep a socket open to listen to all the UDP trafic that
					// is destined for this port
					DatagramSocket socket = new DatagramSocket(Tools.BROADCAST_PORT, InetAddress.getByName("0.0.0.0"));
					socket.setBroadcast(true);

					while (broadcasting) {
						Log.w("FC BC", ">>>Ready to receive broadcast packets!");

						// Receive a packet
						byte[] recvBuf = new byte[15000];
						DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
						socket.receive(packet);

						// Packet received
						Log.w("FC BC", ">>>Discovery packet received from: " + packet.getAddress().getHostAddress());
						Log.w("FC BC", ">>>Packet received; data: " + new String(packet.getData()));

						// See if the packet holds the right command (message)
						String message = new String(packet.getData()).trim();
						if (message.equals("DISCOVER_FC_REQUEST")) {
							byte[] sendData = ("DISCOVER_FC_RESPONSE:" + glider + ":" + task + ":").getBytes();

							// Send a response
							DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, packet.getAddress(), packet.getPort());
							socket.send(sendPacket);

							Log.w("FC BC", ">>>Sent packet to: " + sendPacket.getAddress().getHostAddress());
						}
					}
					socket.close();
				} catch (IOException ex) {
					Log.e("FC BC", ex.getMessage(), ex);
				}
			}
		}).start();
	}

	@Override
	public void setPilotType(int pilotType) {
		this.pilotType = pilotType;
	}

	@Override
	public void setTask(String task) {
		this.task = task;
	}

	@Override
	public Context getContext() {
		return this;
	}

	@Override
	public SharedPreferences getPrefs() {
		return prefs;
	}

	@Override
	public void sendMessage(String msg) {
		Bundle bundle = new Bundle();
		bundle.putString("msg", msg);
		Message message = h.obtainMessage();
		message.setData(bundle);
		h.sendMessage(message);
	}

}
