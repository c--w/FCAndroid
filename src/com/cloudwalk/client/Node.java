/*
 * @(#)Node.java (part of 'Flight Club')
 * 
 * This code is covered by the GNU General Public License
 * detailed at http://www.gnu.org/copyleft/gpl.html
 *	
 * Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 * Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.client;

import java.util.Arrays;
import java.util.Vector;

import android.graphics.Color;
import android.util.Log;

import com.cloudwalk.framework3d.CameraSubject;
import com.cloudwalk.framework3d.Obj3d;
import com.cloudwalk.framework3d.Tools3d;

/**
 * This class implements a node. The task is 'covered' using N nodes (cf mobile phone transmitters).
 */
class Node implements CameraSubject {
	float x, y;
	Trigger[] triggers;
	int next = 0;
	int mode = SLEEPING;
	float radius, radiusSqd;
	Vector liftSources;
	XCModelViewer xcModelViewer;
	TurnPoint turnPoint = null; // used in getEye - look along the task

	// unique id for each instance of this class
	int myID;

	static final int SLEEPING = 0;
	static final int AWAKE = 1;

	public Node(XCModelViewer xcModelViewer, float x, float y, float radius, int id) {
		this.xcModelViewer = xcModelViewer;
		this.x = x;
		this.y = y;
		this.radius = radius;
		radiusSqd = radius * radius;
		triggers = new Trigger[10];
		liftSources = new Vector();
		myID = id; // unique id (for debugging)
	}

	/**
	 * Registers a trigger with a node. Note that a trigger may belong to more than one node. We double the size of the array if we hit the end (cf Obj3d.ps[])
	 */
	void addTrigger(Trigger tt) {
		if (next >= triggers.length) {
			Trigger[] tmp = new Trigger[triggers.length * 2];
			System.arraycopy(triggers, 0, tmp, 0, triggers.length);
			triggers = tmp;
		}
		triggers[next++] = tt;
	}

	/**
	 * Unloads the node... - switch off all triggers - unload any ground features
	 */
	void sleep() {
		// this whole node crap just generate problems - AIgliders newer finish
		// if 'node focus' escapes them
		// if (mode == SLEEPING)
		// return;
		// Log.i("FC Node", "Sleep:" + myID);
		// float t = xcModelViewer.clock.getTime();
		// for (int i = 0; i < next; i++) {
		// triggers[i].sleep(t);
		// }
		// mode = SLEEPING;
		// renderMe();
	}

	/**
	 * Loads this node (with state at model time t). Switch on all triggers and render any ground features. If the node was already awake we still wake up the
	 * triggers. Why ? An overlapping* node may have sent one of the triggers to sleep.
	 */
	void wakeUp(float t) {
		// Log.i("FC Node", "Wakeup:" + myID);
		for (int i = 0; i < next; i++) {
			triggers[i].wakeUp(t);
		}
		if (mode == AWAKE) {
			return;
		}
		mode = AWAKE;
		renderMe();
	}

	/**
	 * Returns true if node contains the point (x, y)
	 */
	public boolean contains(float x, float y) {
		float dx = this.x - x;
		float dy = this.y - y;
		return (dx * dx + dy * dy <= radiusSqd);
	}

	/** Returns distance squared */
	public float distanceSqd(float x, float y) {
		float dx = this.x - x;
		float dy = this.y - y;
		return dx * dx + dy * dy;
	}

	public float[] getFocus() {
		return new float[] { x, y, xcModelViewer.xcModel.task.CLOUDBASE * 0.5f };
	}

	private static final float FUDGE = 0.3f;

	/**
	 * Sets eye so the task runs from right to left accross the screen.
	 */
	public float[] getEye() {
		return new float[] { x + turnPoint.dy * radius * FUDGE, y - turnPoint.dx * radius * FUDGE, xcModelViewer.xcModel.task.CLOUDBASE };
	}

	private Obj3d obj3d;
	static final int NUM_POINTS = 16;
	static final int COLOR = Color.rgb(235, 235, 235);
	boolean flagNoRender = false;

	/**
	 * Adds a visual representation of this node to the model. We draw a big circle on the ground showing the coverage of the node.
	 */
	private void renderMe() {
		if (flagNoRender) {
			return;
		}
		if (mode == AWAKE) {
			obj3d = new Obj3d(xcModelViewer, 0, true);
			obj3d.setNumPolywires(1);
			float[][] ps = Tools3d.circleXY(NUM_POINTS, radius, new float[] { x, y, 0 });
			obj3d.addPolywireClosed(ps, COLOR);
		} else {
			obj3d.destroyMe();
			obj3d = null;
		}
	}

	/** Prints debug info. */
	void asString() {
		Log.i("FC", "Node(" + myID + "): x=" + Tools3d.round(x) + ", y=" + Tools3d.round(y) + ", mode=" + mode + ", nTriggers=" + next);
	}

	/**
	 * Returns the 'next' lift source along the task route. We look for the lift source which is closest whilst being approx on track. In the case where we find
	 * nothing the glider will continue flying on track. Soon it will enter the next node.
	 * 
	 * We may look for the nearest climb, the climb that is closest to being on track or the fastest climb.
	 * 
	 * <pre>
	 * = theta
	 *                                glider
	 * 			         >
	 * 				 |*\
	 *                                 r|   \onTrack              .
	 * 	                         .     \                 
	 * 	                       lift      \
	 * 	                                   \
	 * 					     .
	 * 					     nextTP
	 * </pre>
	 */
	LiftSourceGlide search(GliderAI glider, double searchSector) {
		float[] p = glider.p;
		int found = -1;
		float[] r = new float[3];
		// only consider lift within the glider's search sector
		float cosSector = (float) Math.cos(searchSector);
		float[] onTrack = glider.onTrack();
		TurnPoint nextTP = glider.nextTP;
		float t = 0; // how long to glide to the cloud
		float liftMax = 0;
		float cosMax = 0;
		float dmin = 10000;
		float lift;

		// loop thru' lift sources to find the one that is closest to being on track
		LiftSource bestLS = null;
		int bestIP = 0;
		synchronized (liftSources) {
			for (int i = 0; i < liftSources.size(); i++) {
				LiftSource ls = (LiftSource) liftSources.elementAt(i);
				if (ls.isActive()) { // ignore dying clouds
					Tools3d.subtract(ls.getP(), p, r);
					r[2] = 0; // work in a horizontal plane
					float d = Tools3d.length(r);
					boolean grounded = (ls instanceof Hill ? true : false);
					float reserveHeight = 0;
					if (grounded) {
						reserveHeight = ((Hill) ls).h0 * (0.5f + glider.typeID * 0.25f);
					}
					int iP = glider.withinGlide(ls.getP()[0], ls.getP()[1], grounded, reserveHeight);
					if (iP != -1) { // within glide
						float speed = glider.getSpeed(iP);
						Tools3d.makeUnit(r);
						if (Tools3d.dot(r, onTrack) >= cosSector) { // && d <= dmin) {
							t = d / speed;
							if (ls.isActive(t)) { // still active when we get there
								if ((lift = ls.getLift()) > liftMax) {
									bestLS = ls;
									liftMax = lift;
									bestIP = iP;
									// other searches...
									// dmin = d;
									// cosMax = cos;
								}
							}
						}
					}
				}
			}
		}
		if (bestLS != null) {
			return new LiftSourceGlide(bestLS, bestIP);
		} else {
			return null;
		}
	}

	LiftSource myLiftSource(float[] p) {
		for (int i = 0; i < liftSources.size(); i++) {
			LiftSource ls = (LiftSource) liftSources.elementAt(i);
			if (ls.contains(p))
				return ls;
		}
		return null;
	}

	public void add(LiftSource ls) {
		liftSources.addElement(ls);
	}

	public void remove(LiftSource ls) {
		liftSources.removeElement(ls);
	}

}
