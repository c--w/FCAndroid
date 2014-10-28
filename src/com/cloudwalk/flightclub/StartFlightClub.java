package com.cloudwalk.flightclub;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.TextView;
import android.widget.Toast;

import com.cloudwalk.client.Task;
import com.cloudwalk.client.XCCameraMan;
import com.cloudwalk.client.XCModelViewer;
import com.cloudwalk.framework3d.ClockObserver;
import com.cloudwalk.framework3d.ModelView;
import com.cloudwalk.server.XCGameServer;
import com.cloudwalk.startup.ModelEnv;
import com.cloudwalk.startup.ModelViewerThin;

public class StartFlightClub extends Activity implements ModelEnv, OnTouchListener, ClockObserver {

	String task;
	int pilotType;
	String hostPort = null;
	int[] typeNums = new int[4];
	ModelViewerThin modelViewerThin = null;
	MediaPlayer mp = new MediaPlayer();
	AudioManager audioManager;
	private SoundPool soundPool;
	float volume = 0;
	int[] soundIds;
	ModelView surfaceView;
	GestureDetector detector;
	boolean finished = false, landed = false;

	XCGameServer server;
	boolean broadcasting = false;
	boolean serving = false;
	boolean wasPaused;
	Menu menu;
	SharedPreferences prefs;

	Handler h = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			try {

				if (((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.finished && !finished && ((XCModelViewer) modelViewerThin).xcModel.task.type == Task.TIME) {
					try {
						int best_time = prefs.getInt("best_time0" + task + pilotType, 100000);
						int current_time = (int) ((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.timeFinished;
						Log.i("FC StartFlightClub", "currenttime " + current_time + " " + best_time);
						if (current_time < best_time) {
							submitScore(pilotType, current_time);
						}
					} catch (Exception e) {
						Log.e("FC", e.getMessage(), e);
					}
					finished = true;
				} else if (((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.distanceFlown != 0 && ((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.getLanded() && !landed && ((XCModelViewer) modelViewerThin).xcModel.task.type == Task.DISTANCE) {
					try {
						int best_distance = prefs.getInt("best_distance0" + task + pilotType, 0);
						int current_distance = (int) (((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.distanceFlown()/2 * 100);
						Log.i("FC StartFlightClub", "current_distance " + current_distance + " " + best_distance);
						if (current_distance > best_distance) {
							submitScore(pilotType, current_distance);
						}
					} catch (Exception e) {
						Log.e("FC", e.getMessage(), e);
					}
					landed = true;
				}
				View v = findViewById(R.id.startbuttons);
				if (((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.getLanded()) {
					if (v.getVisibility() == View.GONE)
						v.setVisibility(View.VISIBLE);
				} else {
					if (v.getVisibility() == View.VISIBLE)
						v.setVisibility(View.GONE);
				}
				((TextView) findViewById(R.id.info)).setText(Html.fromHtml(surfaceView.getInfoText()));
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

		task = "default";

		pilotType = 0; // 0 = pg, 1 = hg, 2 = sp, 3 = ballon

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Intent intent = getIntent();
		pilotType = intent.getIntExtra("glider", 0);
		task = intent.getStringExtra("task");
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
			findViewById(R.id.pause).setVisibility(View.GONE);
		}
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
		soundIds = new int[4];
		soundIds[0] = soundPool.load(this, R.raw.beep0, 1);
		soundIds[1] = soundPool.load(this, R.raw.beep1, 1);
		soundIds[2] = soundPool.load(this, R.raw.beep2, 1);
		soundIds[3] = soundPool.load(this, R.raw.beep3, 1);
		audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		float actualVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		float maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		volume = actualVolume / maxVolume;
		/*
		 * } else { if (s[2].indexOf(":") > 0) { hostPort = s[2]; typeNums =
		 * null; } else { hostPort = null; try { for (int i = 0; i < 3; i++) {
		 * typeNums[i] = parseInt(s[i + 2]); } } catch (Exception e) {
		 * Log.i("FC", "Error reading AI glider numbers: " + e); typeNums = new
		 * int[] {2, 5, 2}; } } }
		 */
		final ModelView modelView = (ModelView) findViewById(R.id.xcmodelview);
		surfaceView = modelView;
		modelViewerThin = new XCModelViewer(modelView);
		modelView.modelViewer = (XCModelViewer) modelViewerThin;
		modelView.setOnTouchListener(StartFlightClub.this);

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
				((XCModelViewer) modelViewerThin).xcModel.togglePause();
				wasPaused = true;
			}
		});
		findViewById(R.id.speedy).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				((XCModelViewer) modelViewerThin).xcModel.toggleFastForward();
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
				float x = e.getX();
				float y = e.getY();
				if (x > v.getWidth() * 2 / 7 && x < v.getWidth() * 5 / 7 && y > v.getHeight() * 2 / 7 && y < v.getHeight() * 5 / 7) {
					if (v.getVisibility() == View.GONE)
						v.setVisibility(View.VISIBLE);
					else
						v.setVisibility(View.GONE);
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
	public void play(int sound) {
		// Log.w("FC", "Playing sound: "+sound);
		soundPool.play(soundIds[sound], volume, volume, 1, 0, 1f);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		((XCModelViewer) modelViewerThin).xcModel.gliderManager.gliderUser.handleTouch(v, event);
		((ModelView) findViewById(R.id.xcmodelview)).handleTouch(v, event);
		detector.onTouchEvent(event);
		return true;
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
				Intent launchPreferencesIntent = new Intent().setClass(this, Preferences.class);
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

}
