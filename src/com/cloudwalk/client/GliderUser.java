/*
 * @(#)GliderUser.java (part of 'Flight Club')
 * 
 * This code is covered by the GNU General Public License
 * detailed at http://www.gnu.org/copyleft/gpl.html
 *	
 * Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 * Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.client;

import java.util.Arrays;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.SensorEvent;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * This class implements a glider that is controlled by *the* user.
 */
public class GliderUser extends GliderTask {
	Variometer vario;
	int cameraMode = XCCameraMan.USER;

	public GliderUser(XCModelViewer xcModelViewer, GliderType gliderType, int id, String playerName) {
		super(xcModelViewer, gliderType, id);
		this.playerName = playerName;
		setColor();
		vario = new Variometer(xcModelViewer, this);
	}

	public void setColor() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences((Context) xcModelViewer.modelEnv);
		this.color = prefs.getInt("glider_color", Color.BLUE);
		this.color2 = prefs.getInt("pilot_color", Color.YELLOW);
		this.obj.setColor(0, this.color);
		this.obj.setColor(1, this.color2);
	}

	protected void createTail() {
		tail = new Tail(modelViewer, this, Color.rgb(0, 0, 0), Tail.NUM_WIRES * 2, Tail.WIRE_EVERY);
		tail.init();
	}

	public void destroyMe() {
		super.destroyMe();
	}

	public void tick(float t, float dt) {
		super.tick(t, dt);
		currentGlideSpeed();
		groundGlideRatio = ((int) (groundGlideRatio * 10)) / 10f;

		if (!landed) {
			vario.tick(t);
			netSend(t);
		}
	}

	private void setMove(int move) {
		nextTurn = move;
	}

	private void incMove(int move) {
		nextTurn += move;
		if (nextTurn > 1)
			nextTurn = 1;
		if (nextTurn < -1)
			nextTurn = -1;
	}

	private int _nextTurn = 0;
	private int _iP = 0;

	/**
	 * Send to server the values of p, vx, vy, iP and the next move. We only do this if either nextTurn has changed or iP has changed since this fn was last
	 * called.
	 */
	float lastSend = 0;

	private void netSend(float t) {
		if (nextTurn != _nextTurn || getiP() != _iP || xcModelViewer.clock.getTime() - lastSend > 0.5) {
			if (xcModelViewer.xcNet != null) {
				lastSend = xcModelViewer.clock.getTime();
				xcModelViewer.xcNet.send("#" + round(this.p[0]) + ":" + round(this.p[1]) + ":" + round(this.p[2]) + ":" + round(this.v[0]) + ":"
						+ round(this.v[1]) + ":" + getiP() + ":" + (int) nextTurn + ":" + t);
				_nextTurn = (int) nextTurn;
				_iP = getiP();
			}
		}
	}

	/** Round to floats to n decimal places. */
	private float round(float x) {
		// return x;
		return Math.round(x * 10000f) / 10000f;
	}

	void hitTheSpuds() {
		super.hitTheSpuds();
		if (xcModelViewer.xcNet != null) {
			xcModelViewer.xcNet.send("LANDED");
		}
	}

	public void takeOff(boolean really, boolean send) {
		Log.w("FC takeOff", "gliderUser takeoff" + myID);
		super.takeOff(really);
		if (xcModelViewer.xcNet != null && send) {
			xcModelViewer.xcNet.send("LAUNCHED: " + this.typeID + ":" + this.color + ":" + this.playerName);
		}
	}

	public float[] getFocus() {
		if (cameraMode == XCCameraMan.USER) {
			return super.getFocus();
		} else {
			TurnPoint tp = nextTP.prevTP;
			return new float[] { p[0] + v[0] * EYE_D, p[1] + v[1] * EYE_D, p[2] + v[2] * EYE_D };
		}
	}

	private final float EYE_DD = 0.1f;

	public float[] getEye() {
		if (cameraMode == XCCameraMan.USER) {
			return super.getEye();
		} else {
			TurnPoint tp = nextTP.prevTP;
			return new float[] { p[0] + v[0] * EYE_DD, p[1] + v[1] * EYE_DD, p[2] + v[2] * EYE_DD };
		}
	}

	/**
	 * Allows camera man to either follow the glider from behind or look from the point of view of the pilot.
	 */
	void setCameraMode(int mode) {
		cameraMode = mode;
	}

	public void handleTouch(View v, MotionEvent event) {
		if (landed) {
			return;
		}
		if (event.getActionMasked() == MotionEvent.ACTION_UP) {
			setMove(0);
			// modelViewer.cameraMan.setSubject(this, true);
			return;
		}
		float x = event.getX();
		if (x < v.getWidth() * 2 / 7) {
			setMove(-1);
			if (((XCCameraMan) modelViewer.cameraMan).mode != XCCameraMan.USER)
				modelViewer.cameraMan.setSubject(this, true);
		} else if (x > v.getWidth() * 5 / 7) {
			setMove(1);
			if (((XCCameraMan) modelViewer.cameraMan).mode != XCCameraMan.USER)
				modelViewer.cameraMan.setSubject(this, true);
		} else {
			setMove(0); // onwards
			if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
				float y = event.getY();
				if (y < v.getHeight() * 2 / 7) {
					goFaster();
				} else if (y > v.getHeight() * 5 / 7) {
					goSlower();
				} else {
					// modelViewer.cameraMan.setSubject(this, false);
				}
			}
		}
		return;
	}

	public void handleGravity(SensorEvent event) {
		if (landed) {
			return;
		}
		float x = event.values[0];
		float y = event.values[1];
		if (Math.abs(y) < 2) {
			setMove(0);
		} else if (y < -2) {
			setMove(-1);
			if (((XCCameraMan) modelViewer.cameraMan).mode != XCCameraMan.USER)
				modelViewer.cameraMan.setSubject(this, true);
		} else if (y > 2) {
			setMove(1);
			if (((XCCameraMan) modelViewer.cameraMan).mode != XCCameraMan.USER)
				modelViewer.cameraMan.setSubject(this, true);
		}
		if (x < 6) {
			setPolar(polar.size() - 1);
		} else if (x < 7.2f) {
			setPolar(polar.size() - 2);
		} else if (x < 8.4) {
			setPolar(polar.size() - 3);
		} else {
			setPolar(polar.size() - 4);
		}
		return;
	}
}
