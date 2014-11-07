/*
  @(#)Clock.java (part of 'Flight Club')
	
  This code is covered by the GNU General Public License
  detailed at http://www.gnu.org/copyleft/gpl.html
	
  Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
  Copyright 2001-2002 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.framework3d;

import java.util.Vector;

import android.os.SystemClock;
import android.util.Log;

import com.cloudwalk.client.Trigger;
import com.cloudwalk.client.XCModelViewer;

/**
 * This class implements the clock that manages model time. The clock runs on its own thread and maintains a list of observers. Each time round the run loop it
 * calls the tick method of each of its observers. The frame rate starts out as 25 but it may go up or down depending on how long it takes to execute all the
 * observer's tick methods. The clock keeps track of the *model* time.
 */
public class Clock implements Runnable {

	Thread ticker = null;
	int sleepTime;
	Vector<ClockObserver> observers = new Vector<ClockObserver>();

	private long currentTick = 0;
	long tickCount = 0;

	// for tuning the tick rate
	private long idleTime = 0;
	private long blockStart;
	private float modelTime;
	private int frameRate;
	private float modelTimePerFrame;

	public boolean paused = false;
	public boolean speedy = false; // Flag true => speed model time up by a
									// factor of 10

	private static final int INIT_RATE = 20;
	private static final int MAX_RATE = 60;
	private static final int BLOCK = 2; // how often do we review the frame rate
										// ?
	private static final float MODEL_TIME_PER_SECOND = 1.0f; // units of model
																// time per
																// second
	private static final float MODEL_TIME_PER_TICK = MODEL_TIME_PER_SECOND / 1000f;
	private static final float IDLE_PERCENT_MIN = 0.05f; // idle for at least 5%
															// of the time - do
															// not
															// thrash the CPU

	/*
	 * Creates the clock. Pass in the current model time. This will be zero if you are not connected to a game server, otherwise it will be the current model
	 * time as defined on the server.
	 */
	public Clock(float modelTime) {
		synchTime(modelTime);
		setFrameRate(INIT_RATE);
	}

	public void addObserver(ClockObserver observer) {
		observers.addElement(observer);
	}

	public void removeObserver(ClockObserver observer) {
		observers.removeElement(observer);
	}

	public void start() {
		if (ticker == null) {
			ticker = new Thread(this);
			ticker.setPriority(Thread.MIN_PRIORITY);
		}
		ticker.start();
		blockStart = currentTick = System.currentTimeMillis();
		modelTime = getTimeNow();
		for (int i = 0; i < observers.size(); i++) {
			ClockObserver observer = (ClockObserver) observers.elementAt(i);
			if (observer instanceof Trigger) {
				Trigger t = ((Trigger) observer);
				if (t.mode == Trigger.SLEEPING)
					t.wakeUp(modelTime);
			}
		}
	}

	public void stop() {
		if (ticker != null) {
			ticker.interrupt();
		}
		ticker = null;
		modelTime = getTimeNow();
		for (int i = 0; i < observers.size(); i++) {
			ClockObserver observer = (ClockObserver) observers.elementAt(i);
			if (observer instanceof Trigger) {
				((Trigger) observer).sleep(modelTime);
			}
		}
	}
	float t, _t = 0, dt;
	public void oneFrame(ModelViewRenderer renderer) {
		if (ticker != null) {
			if (((XCModelViewer) observers.elementAt(0)).netFlag && !((XCModelViewer) observers.elementAt(0)).netTimeFlag) {
				SystemClock.sleep(10);
				return;
			}
			currentTick = System.currentTimeMillis();
			tickCount++;

			modelTime = t = getTimeNow();
			
			 if (_t == 0) { dt = modelTimePerFrame; } else { dt = t - _t; }
			
			for (int i = 0; i < observers.size(); i++) {
				// when paused still tick the modelviewer so
				// we can change our POV and *un*pause !
				if (i == 0 || !paused) {
					ClockObserver c = (ClockObserver) observers.elementAt(i);
					try {
						if (paused) {
							if (((ModelViewer) c).modelView.dragging) {
								c.tick(modelTime, modelTimePerFrame);
							}
						} else
							c.tick(modelTime, modelTimePerFrame);
					} catch (Exception e) {
						Log.e("FC", e.getMessage(), e);
					}
				}
			}
			renderer.drawEverything();
		}

		long now = System.currentTimeMillis();
		long timeLeft = sleepTime + currentTick - now;

		// idle for a bit
		if (timeLeft > 0) {
			idleTime += timeLeft;
			try {
				Thread.sleep(timeLeft);
			} catch (InterruptedException e) {
				ticker = null;
				return;
			}
		}

		// check frame rate every so often
		if (tickCount % BLOCK == 0) {
			reviewRate(now);
		}
		_t = t;

	}

	public void run() {
		Log.w("FC Clock", "" + this + " " + getTimeNow());

		// ticker = null;
	}

	/* Returns the current model time as defined by the run loop (discrete). */
	public final float getTime() {
		return modelTime;
	}

	/* Returns the current model time right now (continuous). */
	public final float getTimeNow() {
		return (System.currentTimeMillis() - realTimeAtSync) * MODEL_TIME_PER_TICK + modelTimeAtSync;
	}

	private long realTimeAtSync;
	private float modelTimeAtSync;

	/*
	 * Synchronises this client's copy of the model time with the master time held on the game server.
	 */
	public void synchTime(float t) {
		realTimeAtSync = System.currentTimeMillis();
		modelTimeAtSync = t;
		Log.w("FC Clock", "Synctime:" + t + " Modeltime diff:" + (getTimeNow() - modelTime));
		modelTime = getTimeNow();
	}

	private void setFrameRate(int r) {
		frameRate = r;
		sleepTime = 1000 / frameRate;
		modelTimePerFrame = MODEL_TIME_PER_SECOND / frameRate;
		if (speedy)
			modelTimePerFrame *= 10;
	}

	/*
	 * Tune the frame rate up or down depending on how long we have been idle over the last N ticks.
	 */
	void reviewRate(long t) {
		long elapsed = t - blockStart;
		float idlePercent = (float) idleTime / elapsed;

		if (idlePercent < IDLE_PERCENT_MIN && frameRate > 2) {
			// working too hard so slow down
			setFrameRate(frameRate - 2);
			// Log.w("FC", "slowing down");
		} else if (frameRate < MAX_RATE) {
			setFrameRate(frameRate + 1);
		}

		// re init vars
		idleTime = 0;
		blockStart = t;
	}

	/** Gets the current frame rate. */
	public final int getFrameRate() {
		return frameRate;
	}
}
