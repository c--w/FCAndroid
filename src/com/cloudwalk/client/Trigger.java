/*
 * @(#)Trigger.java (part of 'Flight Club')
 * 
 * This code is covered by the GNU General Public License
 * detailed at http://www.gnu.org/copyleft/gpl.html
 *	
 * Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 * Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.client;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.Random;

import android.util.Log;

import com.cloudwalk.framework3d.CameraSubject;
import com.cloudwalk.framework3d.ClockObserver;
import com.cloudwalk.framework3d.Obj3d;
import com.cloudwalk.framework3d.Tools3d;

/**
 * This class implements a thermal trigger. Every cycle the trigger releases a
 * bubble of warm air that floats up to produce a cloud. Well, that is the
 * impression we hopefully evoke.
 * 
 * The trigger model contains lots of dubious assumptions and gross
 * simplifacations. Good !
 * 
 * We add a veneer of noise to the otherwise clockwork like behaviour of the
 * trigger without departing from the deterministic model.
 */
public class Trigger implements ClockObserver, CameraSubject {
	XCModelViewer xcModelViewer;
	float x, y;
	float thermalStrength;
	float cycleLength;
	float duration; // life span of cloud as a fraction of cycle length (ie.
					// 0..1)
	float phase;
	float nextCloudStartTime; // when will next cloud be created
	float lastTick;
	int mode = SLEEPING;
	boolean blueThermal = false;
	boolean show = true;

	// Fixed seed so that model is deterministic (state(T) is same every game
	// play).

	// unique id for each instance of this class
	static int nextID = 0;
	int myID;

	static final int SLEEPING = 0;
	static final int AWAKE = 1;

	/**
	 * Creates a trigger at (x, y). t is the current time and t0 is the time
	 * that the trigger creates its first bubble.
	 * 
	 * We may have t > t0. In this case we need to look back in time to see what
	 * clouds this trigger has already produced (which have yet to evaporate) !
	 */
	public Trigger(XCModelViewer xcModelViewer, StreamTokenizer st) throws IOException {
		this.xcModelViewer = xcModelViewer;

		// parse data
		st.nextToken();
		x = (float) st.nval;
		if (x < 0)
			x = -x;
		st.nextToken();
		y = (float) st.nval;
		if (y < 0)
			y = -y;
		st.nextToken();
		thermalStrength = (float) st.nval;
		if (thermalStrength > 5)
			thermalStrength = 5;
		if (thermalStrength < STRENGTH_MIN)
			thermalStrength = STRENGTH_MIN;
		st.nextToken();
		cycleLength = (float) st.nval;
		if (cycleLength > 120)
			cycleLength = 120;
		if (cycleLength < 10)
			cycleLength = 10;
		st.nextToken();
		duration = (float) st.nval;
		if (duration > 1)
			duration = 1;
		if (duration < 0.1)
			duration = 0.1f;
		st.nextToken(); // gobble new line or ...
		if (st.ttype != StreamTokenizer.TT_EOL) {
			blueThermal = (st.nval == 1 ? true : false);
			st.nextToken(); // gobble new line or...
			if (st.ttype != StreamTokenizer.TT_EOL) {
				show = (st.nval == 1 ? true : false);
				st.nextToken(); // gobble new line
			}
		}
		// random = new Random((long) (x * 10 + y));

		/**
		 * ~Random phase. We ensure that all clients get same phase for a
		 * trigger. Note that *all* triggers get created from the task file
		 * before the model comes alive - so using the pseudo rnd generator is
		 * ok.
		 */
		phase = ge01Value() * cycleLength;
		//Log.w("FC Trigger", "" + phase);
		// nextCloudStartTime = phase - cycleLength;
		myID = nextID++; // unique id (for debugging)
	}

	/** Creates a 'default' trigger at (x, y). */
	public Trigger(XCModelViewer xcModelViewer, float x, float y) {
		this.xcModelViewer = xcModelViewer;
		this.x = x;
		this.y = y;

		// use a gaussian distribution for area (= size * size) with mean=4 and
		// sd=2
		float a = 4.0f + (float) ge01Value2() * 2.0f;
		if (a < 0.5f) {
			a = 0.5f;
		}
		thermalStrength = (float) Math.sqrt(a);
		cycleLength = Cloud.getLifeSpan(thermalStrength) * 1.0f;
		duration = 1.0f;
		phase = ge01Value() * cycleLength;
		myID = nextID++;
		// Log.w("FC Trigger", "id+phase" + myID + " " + phase + " " +
		// cycleLength + " " + x + " " + y);
	}

	float ge01Value() {
		float a = (float) Math.sqrt((x + 1) / (y + 1));
		return (float) ((a * 10) - Math.floor(a * 10));
	}

	float ge01Value2() {
		float a = (float) Math.sqrt((x + 2) / (y + 2));
		return (float) ((a * 10) - Math.floor(a * 10));
	}

	/**
	 * Create any clouds that bubbled up before I was created (but have yet to
	 * evaporate). We loop back thru' the cycles from nextCloudStartTime.
	 */
	private void existingClouds(float t) {
		// Log.i("FC Trigger",
		// "existingClouds:"+myID+" "+t+" "+nextCloudStartTime+" "+cycleLength);

		float currentCloudStartTime = nextCloudStartTime - cycleLength;
		if (t - currentCloudStartTime < cycleLength * duration) {
			makeCloud(t - currentCloudStartTime);
		}
	}

	/** Make a cloud every <code>cycle</code> period of time. */
	public void tick(float t, float dt) {
		if (t >= nextCloudStartTime) {
			makeCloud();
			nextCloudStartTime += cycleLength;
		}
	}

	private static float STRENGTH_MIN = 0.1f;

	// Makes a new cloud start bubbling up
	private void makeCloud() {
		if (thermalStrength >= STRENGTH_MIN) {
			new Cloud(xcModelViewer, x, y, thermalStrength, duration * cycleLength, 0, blueThermal);
		}
	}

	/** Makes a cloud that bubbled up at time dt *before* now. */
	private void makeCloud(float dt) {
		// Log.i("FC Trigger", "make cloud of age:"+dt);
		if (thermalStrength >= STRENGTH_MIN) {
			Task task = xcModelViewer.xcModel.task;
			float x_ = x + dt * task.wind_x;
			float y_ = y + dt * task.wind_y;
			new Cloud(xcModelViewer, x_, y_, thermalStrength, duration * cycleLength, dt, blueThermal);
		}
	}

	private float sleepT = -1;

	/**
	 * Makes the trigger sleep. When asleep the trigger will not be rendered and
	 * does not produce any clouds.
	 */
	void sleep(float t) {
		if (mode == SLEEPING) {
			return;
		}
		xcModelViewer.clock.removeObserver(this);
		mode = SLEEPING;
		renderMe();
		sleepT = t;
		Log.i("FC Trigger", "Sleep:" + myID);
	}

	/**
	 * Wakes up this trigger. One fiddly bit - if the wake up comes immediately
	 * after sleep was called then we do not need to create existing clouds.
	 * Triggers on overlapping nodes get a sleep call from one node followed by
	 * a wake call from another.
	 */
	void wakeUp(float t) {
		if (mode == AWAKE) {
			return;
		}
		xcModelViewer.clock.addObserver(this);
		if (t != sleepT) {
			initNextCycle(t);
			existingClouds(t);
		} else {
			// Log.i("FC", "Waking up immediately after a sleep");
		}
		mode = AWAKE;
		renderMe();
	}

	/**
	 * Utility fn to set nextCloudStartTime, the time when the next cloud will
	 * be released. <code>phase</code> is time of first bubble and bubbles occur
	 * every <code>cycleLength</code>.
	 */
	private void initNextCycle(float t) {
		nextCloudStartTime = phase;
		if (t > phase) {
			int n = (int) Math.floor((t - phase) / cycleLength);
			nextCloudStartTime += (n + 1) * cycleLength;
		}
	}

	public float[] getFocus() {
		return new float[] { x, y, xcModelViewer.xcModel.task.CLOUDBASE / 2 };
	}

	public float[] getEye() {
		return new float[] { x + xcModelViewer.xcModel.task.CLOUDBASE * 2, y, xcModelViewer.xcModel.task.CLOUDBASE / 2 };
	}

	/**
	 * Add a visual representation of this trigger to the model.
	 * 
	 * We draw a square on the ground whose shade varys from white to black as a
	 * fn of thermalStrength.
	 */
	private Obj3d obj3d;
	private static final int NUM_POINTS = 7;

	private void renderMe() {
		if (mode == AWAKE && show) {
			obj3d = new Obj3d(xcModelViewer, 0, true);
			obj3d.setNumPolywires(1);
			float radius = thermalStrength * 0.5f;
			float[][] ps = Tools3d.circleXY(NUM_POINTS, radius, new float[] { x, y, 0 });
			obj3d.addPolywireClosed(ps, Obj3d.COLOR_DEFAULT);
		} else if (obj3d != null) {
			obj3d.destroyMe();
			obj3d = null;
		}
	}

	/** Prints a debug string. */
	void asString() {
		Log.i("FC", "Trigger(" + myID + "): x=" + Tools3d.round(x) + ", y=" + Tools3d.round(y) + ", mode=" + mode);
	}

}
