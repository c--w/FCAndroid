package com.cloudwalk.client;

import android.graphics.Color;
import android.util.Log;

public class Bird extends GliderAI {

	public Bird(XCModelViewer xcModelViewer, GliderType gliderType, int id) {
		super(xcModelViewer, gliderType, id);
		this.color = Color.BLACK;
		this.obj.setColor(0, this.color);
		this.obj.setColor(1, Color.RED);
	}

	public void launch() {
		Log.w("FC takeOff", "Bird takeoff" + myID);
		finished = false;
		onGround = false;
		launchPending = false;
		landed = false;
		nextTurn = 0;
		setPolar();
		makeDecision(xcModelViewer.clock.getTime());
	}

	protected void tickAI(float t) {

		// am i thermalling under a decaying cloud ?
		if (moveManager.cloud != null && moveManager.cloud.lifeCycle.isDecaying() && moveManager.cloud.getLift(p) < Cloud.LIFT_UNIT) {
			makeDecision(t);
			return;
		}

		// am i at base ?
		if (moveManager.cloud != null && this.p[2] >= moveManager.cloud.h) {
			makeDecision(t);
			return;
		}

		if (moveManager.cloud != null && this.p[2] < moveManager.cloud.h / 10) {
			setPolar(1);
		} else if (moveManager.cloud != null && this.p[2] >= moveManager.cloud.h / 4) {
			setPolar(0);
		}

	}

	LiftSource searchNodes() {
		LiftSource ls = null;
		Node node = xcModelViewer.xcModel.task.nodeManager.nearestNode(p);
		ls = node.getRandomLS(this);
		return ls;
	}

	/**
	 * Searches my node for the next lift source.
	 */
	void makeDecision(float t) {

		LiftSource ls = searchNodes();
		if (ls == null)
			return;
		currentLS = ls;

		// glide to lift source - a cloud or hill
		try {
			Cloud cloud = (Cloud) ls;
			if (moveManager.getCloud() != cloud) {
				// Log.i("FC GLIDERAI", "glide to cloud with" + lsg.glideIndex + " LS:" + cloud.myID);
				this.moveManager.setCloud(cloud);
			}
			return;
		} catch (Exception e) {
			Log.e("FC Bird", e.getMessage(), e);
		}

	}

}
