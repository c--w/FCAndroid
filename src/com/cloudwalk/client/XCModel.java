/*
  XCModel.java (part of 'Flight Club')
	
  This code is covered by the GNU General Public License
  detailed at http://www.gnu.org/copyleft/gpl.html
	
  Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
  Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.client;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.cloudwalk.flightclub.Tools;
import com.cloudwalk.framework3d.Model;

/**
 * This class implements the top level manager for the flight club game.
 */
public class XCModel extends Model {
	XCModelViewer xcModelViewer;
	public GliderManager gliderManager;
	public Task task;
	public Compass compass = null;
	public DataSlider slider = null;

	/**
	 * An intermediate var gives us a casted reference to the camera man. I'm
	 * not sure if this is good style. What if the camera man object changes ?
	 * Then this link points to the wrong object !
	 */
	public XCCameraMan xcCameraMan;
	public int mode;

	public static final int DEMO = 0;
	public static final int USER = 1;

	public XCModel(XCModelViewer xcModelViewer) {
		super(xcModelViewer);
		this.xcModelViewer = xcModelViewer;
		xcCameraMan = (XCCameraMan) xcModelViewer.cameraMan;
	}

	// don't want the super classes model (a cube).
	protected void makeModel() {
		;
	}

	/**
	 * Loads the task. This involves downloading a file so may take a while over
	 * the net. Being unable to load the task is a *fatal* error. Well it would
	 * be, but we create a dummy task in this case. This means the applet does
	 * *something* when the task file has gone belly up !
	 */
	public void loadTask(String id, int pilotType, int[] typeNums) {
		String msg;

		if (id == null || id.equals("") || id.equals("default")) {
			msg = "Loading default task...";
			Log.i("FC", msg);
			task = new Task(xcModelViewer); // default task
		} else {
			// xcModelViewer.modelView.setText("Loading task: " + id + "...",
			// PROMPT_LINE);
			try {
				task = new Task(xcModelViewer, id);
			} catch (Exception e) {
				msg = "Error loading task: " + id + "\n" + e;
				xcModelViewer.modelView.setText(msg, PROMPT_LINE);
				Log.e("FC", msg, e);
				System.exit(1); // ?
			}
		}

		if (!xcModelViewer.netFlag) {
			if (typeNums != null) {
				gliderManager.createAIs(typeNums[0], typeNums[1], typeNums[2], typeNums[3]);
			} else {
				// defaults
				gliderManager.createAIs(1, 1, 1, 1);
			}
		}
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(xcModelViewer.modelEnv.getContext());
		if (prefs.getBoolean("show_task_info", true) && task.desc.length() > 0) {
			Tools.showInfoDialog("Task info:", task.desc, xcModelViewer.modelEnv.getContext());
		}

	}

	private boolean userPlay_ = false; // flag true *after* calling startPlay

	/**
	 * Starts game play. The first call to this fn puts game into demo mode.
	 * Subsequent calls (when user presses <y>) launch the user glider.
	 */
	void startPlay() {
		xcCameraMan.gotoTaskStart();
		if (userPlay_) { // user pressed <y>
			// xcCameraMan.setMode(XCCameraMan.GAGGLE);
			gliderManager.launchUser();
			xcCameraMan.setMode(XCCameraMan.USER);

			if (!doneInstruments) {
				createInstruments();
				mode = USER;
			}
		}

		if (xcModelViewer.xcNet == null) {
			gliderManager.launchAIs();
			task.nodeManager.loadNodes(0, xcModelViewer.clock.getTime());
		} else {
			if (xcModelViewer.netTimeFlag) {
				task.nodeManager.loadNodes(0, xcModelViewer.clock.getTime());
			}
		}

		if (!userPlay_) {
			if (xcModelViewer.xcNet == null) {
				xcCameraMan.setMode(XCCameraMan.GAGGLE);
			} else {
				// stay put ?
			}
			mode = DEMO;
		}

		// check these toggles are off
		if (modelViewer.clock.paused)
			togglePause();
		if (modelViewer.clock.speedy)
			toggleFastForward();

		userPlay_ = true;
	}

	/**
	 * How much model time passes each second of game play ? Either 1 second
	 * (normal) or 10 seconds (speedy). Speedy time is handy for cloud watching.
	 */
	public void toggleFastForward() {
		modelViewer.clock.speedy = !modelViewer.clock.speedy;
	}

	public void togglePause() {
		modelViewer.clock.paused = !modelViewer.clock.paused;
	}

	private boolean doneInstruments = false;

	/**
	 * Creates the compass and vario.
	 */
	private void createInstruments() {
		int width = modelViewer.modelView.getWidth();
		int height = modelViewer.modelView.getHeight();
		float vmax = -2 * gliderManager.gliderUser.getMaxSink();
		int size = width / 15;
		compass = new Compass(modelViewer, size, width / 2 - size / 2, height - height / 40);
		slider = new DataSlider(modelViewer, -vmax, vmax, size, width / 2 + size / 2, height - height / 40);
		slider.label = "v";
		doneInstruments = true;
	}

	private float t_ = 0;
	private static final float T_INTERVAL = 0.2f;
	static final int GLIDER_LINE = 2;
	static final int SERVER_LINE = 1;
	static final int PROMPT_LINE = 0; // 0 is bottom line

	/**
	 * If we are in user mode then update the instruments and the status
	 * messages.
	 */
	public void tick(float t, float dt) {
		if (t < t_ + T_INTERVAL) {
			return;
		}

		t_ = t;

		if (mode == USER) {
			Glider g = gliderManager.gliderUser;
			compass.setArrow(g.v[0], g.v[1]);
			slider.setValue(g.getSink() + g.air[2]);

			modelViewer.modelView.setText(g.getStatusMsg(), GLIDER_LINE);
		}

		// server status
		serverStatus();

		// frame rate for when testing etc
		// String status = "FPS: " + modelViewer.clock.getFrameRate(); // tmp
		// modelViewer.modelView.setText(status, 0);
	}

	/**
	 * Display server info. Lumped camera status in here aswell !
	 */
	void serverStatus() {
		String s;
		if (xcModelViewer.xcNet == null) {
			s = "Offline";
		} else {
			int n = 1 + gliderManager.numNet;
			s = "Server: " + xcModelViewer.xcNet.host + " (" + n + " pilots online)";
		}
		modelViewer.modelView.setText(s + "\n" + xcCameraMan.getStatusMsg(), SERVER_LINE);
	}

	public void start(int gliderType) {
		gliderManager.createUser(gliderType);
		xcCameraMan.setMode(XCCameraMan.USER); // look at my new glider
		startPlay();
	}

	private boolean prompting = false;

}
