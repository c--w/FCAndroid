/*
 * @(#)GliderAI.java (part of 'Flight Club')
 * 
 * This code is covered by the GNU General Public License
 * detailed at http://www.gnu.org/copyleft/gpl.html
 *	
 * Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 * Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.client;

import java.util.Arrays;

import android.graphics.Color;
import android.util.Log;

import com.cloudwalk.framework3d.CameraSubject;
import com.cloudwalk.framework3d.Tools3d;

/**
 * This class implements an AI glider. The glider will sniff out lift, climb to base then fly downwind in search of more lift.
 * 
 * TODO: When to climb and when to glide to a *better* climb ? Also, a rule that if h < h1then be *cautious* ?
 * 
 * TODO: Different AI profiles: eg. racer vs floater
 */
public class GliderAI extends GliderTask {
	MovementManager moveManager;
	private boolean tryLater = false; // no lift found yet so glide for a bit on
										// track
	LiftSource currentLS;
	// vars for delayed camera cuts
	boolean cutPending = false;
	float cutWhen = 0;
	CameraSubject cutSubject = null;

	// search in a sector this far either side of being on track
	final double narrowSearchSector = Math.PI / 5;
	final double searchSector = Math.PI / 3;
	final double desperateSearchSector = Math.PI;
	boolean narrowSearch = true;
	boolean desperate = false;
	boolean easyGlideToTP = false;

	// if a search for lift fails then try again after this much time
	private static final float T_LATER = 1.0f;
	static final int[] colors = { Color.BLACK, Color.CYAN, Color.GREEN, Color.RED, Color.MAGENTA, Color.YELLOW, Color.DKGRAY, Color.GRAY, Color.BLUE };

	public GliderAI(XCModelViewer xcModelViewer, GliderType gliderType, int id) {
		super(xcModelViewer, gliderType, id);
		moveManager = new MovementManager(xcModelViewer, this);
		this.color = colors[myID % colors.length];
		this.obj.setColor(0, this.color);
		this.obj.setColor(1, Color.YELLOW);
	}

	/**
	 * Start flying the task.
	 */
	public void takeOff(boolean really) {
		super.takeOff(really);
		makeDecision(xcModelViewer.clock.getTime());
	}

	// time of last search for lift (an expensive operation)
	private float t_ = 0;

	/**
	 * Checks for certain conditions. Eg. have i reached base ?
	 * 
	 * TODO: A better design would not keep checking every tick, but use a call back ?
	 */
	protected void tickAI(float t) {
		// time for a camera move ?
		if (cutPending) {
			if (t >= cutWhen) {
				cutNow();
			}
		}

		if (finalGlide && t > t_ + T_THINK) {
			t_ = t;
			int iP = withinGlide(nextTP.x, nextTP.y, true, 0);
			if (iP != -1)
				setPolar(iP);
			return;
		}

		// final glide
		if (nextTP.nextTP == null && !finalGlide) {
			int iP = withinGlide(nextTP.x, nextTP.y, true, 0);
			if (iP != -1) {
				//Log.i("FC GLIDERAI", "final glide!!");
				this.moveManager.setTargetPoint(new float[] { nextTP.x_, nextTP.y_, 0 }, true);
				setPolar(iP);
				finalGlide = true;
			}
		}

		// am i thermalling under a decaying cloud ?
		if (moveManager.cloud != null && moveManager.cloud.lifeCycle.isDecaying()) {
			if (Glider.filmID == myID) {
				modelViewer.cameraMan.setSubject(this, true);
			}
			makeDecision(t);
			return;
		}

		// am i at base ?
		if (this.obj.getZmax() >= xcModelViewer.xcModel.task.CLOUDBASE && t > t_ + T_LATER) {
			if (Glider.filmID == myID) {
				modelViewer.cameraMan.setSubject(this, true);
			}
			makeDecision(t);
			return;
		}

		// am i gliding and waiting
		if (tryLater) {
			if (t > t_ + T_LATER) {
				tryLater = false;
				makeDecision(t);
			}
			return;
		}

		// otherwise, review me last decision every so often - perhaps
		// we can now reach a better climb ?
		if (t > t_ + T_THINK) {
			reviewDecision(t);
		}
	}

	LiftSourceGlide searchNodes(double searchSector) {
		Node[] nodes = xcModelViewer.xcModel.task.nodeManager.nodes;
		LiftSourceGlide ls = null;
		for (int i = 0; i < nodes.length; i++) {
			ls = nodes[i].search(this, searchSector);
			if (ls != null) {
				break;
			}
		}
		return ls;
	}

	/**
	 * Searches my node for the next lift source.
	 */
	void makeDecision(float t) {

		t_ = t; // note time of search
		int iP = withinEasyGlide(nextTP.x_, nextTP.y_, true);
		if (iP != -1) {
			if (!easyGlideToTP) {
				//Log.i("FC GLIDERAI", "easyglide to TP: " + nextTP.myID);
				this.moveManager.setTargetPoint(new float[] { nextTP.x_, nextTP.y_, 0 }, true);
				easyGlideToTP = true;
			}
			setPolar(iP);
			tryLater = true;
			return;
		}
		narrowSearch = true;
		desperate = false;
		easyGlideToTP = false;
		LiftSourceGlide lsg = searchNodes(narrowSearchSector);
		if (lsg == null) {
			lsg = searchNodes(searchSector);
			narrowSearch = false;
		}
		if (lsg == null) {
			desperate = true;
			lsg = searchNodes(desperateSearchSector);
		}
		if (lsg == null) { // glide torwards next turn point
			this.moveManager.setTargetPoint(new float[] { nextTP.x_, nextTP.y_, 0 }, true);
			setPolar(bestGlide(nextTP.x_, nextTP.y, true));
			//Log.i("FC GLIDERAI", "glide to TP: " + nextTP.myID + " with IP: " + bestGlide(nextTP.x_, nextTP.y, true));
			tryLater = true;
			return;
		}
		currentLS = lsg.ls;

		// glide to lift source - a cloud or hill
		try {
			Cloud cloud = (Cloud) lsg.ls;
			if (moveManager.getCloud() != cloud) {
				setPolar(lsg.glideIndex);
				//Log.i("FC GLIDERAI", "glide to cloud with" + lsg.glideIndex + " LS:" + cloud.myID);
				this.moveManager.setCloud(cloud);
				if (filmID == myID) {
					cutPending = true;
					cutWhen = t + whenArrive(cloud.x, cloud.y) - 2;
					cutSubject = (CameraSubject) cloud;
				}
			}
			return;
		} catch (Exception e) {
			;
		}

		try {
			Hill hill = (Hill) lsg.ls;
			if (moveManager.getCircuit() != hill.getCircuit()) {
				setPolar(lsg.glideIndex);
				//Log.i("FC GLIDERAI", "glide to hill with: " + lsg.glideIndex + " LS: " + hill.myID);
				this.moveManager.setCircuit(hill.getCircuit());
			}
		} catch (Exception e) {
			;
		}
	}

	/**
	 * Is there a better lift source within reach ?
	 */
	void reviewDecision(float t) {
		LiftSourceGlide lsg = searchNodes(searchSector);

		t_ = t; // note time of search

		if (lsg == null || lsg.ls == currentLS) {
			return;
		} else if (currentLS != null) {
			float distCurrentLS = Tools3d.length(new float[] { currentLS.getP()[0] - nextTP.x_, currentLS.getP()[1] - nextTP.y_, 0 });
			float distNewLS = Tools3d.length(new float[] { lsg.ls.getP()[0] - nextTP.x_, lsg.ls.getP()[1] - nextTP.y_, 0 });
			// if not in desperate mode or found LS has weaker lift or lifts are equal but new distance from next tp is greater then ignore
			if (lsg.ls.getLift() < currentLS.getLift() || (lsg.ls.getLift() == currentLS.getLift() && distNewLS > distCurrentLS))
				return;
		}

		currentLS = lsg.ls;
		// glide to cloud if it is *stronger* or if I was in desperate mode
		try {
			Cloud cloud = (Cloud) lsg.ls;
			//Log.i("FC GLIDERAI", "review found better LS with " + lsg.glideIndex + " LS: " + cloud.myID);
			setPolar(lsg.glideIndex);
			this.moveManager.setCloud(cloud);
			if (filmID == myID) {
				cutPending = true;
				cutWhen = t + whenArrive(cloud.x, cloud.y) - 2;
				cutSubject = (CameraSubject) cloud;
			}
			return;
		} catch (Exception e) {
			;
		}

		try {
			Hill hill = (Hill) lsg.ls;
			setPolar(lsg.glideIndex);
			//Log.i("FC GLIDERAI", "glide to hill with: " + lsg.glideIndex + " LS: " + hill.myID);
			this.moveManager.setCircuit(hill.getCircuit());
		} catch (Exception e) {
			;
		}
	}

	public void tick(float t, float dt) {
		if (!landed) {
			nextTurn = moveManager.nextMove();
			tickAI(t);
		}
		super.tick(t, dt);
	}

	private boolean finalGlide = false; // set to true when final gliding
	static final float T_THINK = 1.0f; // don't think too hard (CPU)

	protected void reachedTurnPoint() {
		super.reachedTurnPoint();
		makeDecision(xcModelViewer.clock.getTime());
	}

	/**
	 * Returns true if (x, y) is within glide. Adding wind makes this a bit fiddly. The glide angle will be reduced by any head wind etc.
	 */
	public int withinGlide(float x, float y, boolean grounded, float reserveHeight) {
		for (int j = polar.size() - 1; j >= 0; j--) {
			float[] u = this.onTrack();
			float s = this.getSpeed(j);
			u[0] *= s;
			u[1] *= s;
			if (grounded) {
				u[0] += air[0];
				u[1] += air[1];
			}
			float s_ = (float) Math.sqrt(u[0] * u[0] + u[1] * u[1]);
			float glideAngle_ = s_ / -this.getSink(j);

			float[] r = new float[3];
			Tools3d.subtract(new float[] { x, y, p[2] }, p, r);
			float d = Tools3d.length(r);
			if (d * 1.05f <= (p[2] - reserveHeight) * glideAngle_) // 1.05 - allow some margin
				return j;
		}
		return -1;
	}

	public int withinEasyGlide(float x, float y, boolean grounded) {
		for (int j = polar.size() - 1; j >= 0; j--) {
			float[] u = this.onTrack();
			float s = this.getSpeed(j);
			u[0] *= s;
			u[1] *= s;
			if (grounded) {
				u[0] += air[0];
				u[1] += air[1];
			}
			float s_ = (float) Math.sqrt(u[0] * u[0] + u[1] * u[1]);
			float glideAngle_ = s_ / -this.getSink(j);

			float[] r = new float[3];
			Tools3d.subtract(new float[] { x, y, p[2] }, p, r);
			float d = Tools3d.length(r);
			if (d * 2 <= p[2] * glideAngle_ && p[2] > xcModelViewer.xcModel.task.CLOUDBASE / (1.75 + typeID / 2f))
				return j;
		}
		return -1;
	}

	public int bestGlide(float x, float y, boolean grounded) {
		float bestD = 0;
		int bestIP = 0;
		for (int j = polar.size() - 1; j >= 0; j--) {
			float[] u = this.onTrack();
			float s = this.getSpeed(j);
			u[0] *= s;
			u[1] *= s;
			if (grounded) {
				u[0] += air[0];
				u[1] += air[1];
			}
			float s_ = (float) Math.sqrt(u[0] * u[0] + u[1] * u[1]);
			float glideAngle_ = s_ / -this.getSink(j);

			if (p[2] * glideAngle_ > bestD) {
				bestD = p[2] * glideAngle_;
				bestIP = j;
			}
		}
		return bestIP;

	}

	/**
	 * How long to get to a cloud. Note we may ignore the wind as both the cloud and the glider experience the same drift.
	 */
	float whenArrive(float x, float y) {
		float d = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y);
		d = (float) Math.sqrt(d);
		return d / this.getSpeed();
	}

	/**
	 * Makes a camera cut. This is a bit fiddly - we wait until the glider is close to the hill/thermal so the camera does not get ahead of its subject.
	 */
	private void cutNow() {
		if (Glider.filmID == myID) {
			modelViewer.cameraMan.setSubject(cutSubject, true);
		}

		cutPending = false;
		cutSubject = null;
	}
}
