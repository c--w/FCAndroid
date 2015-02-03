/*
  Glider.java (part of 'Flight Club')
	
  This code is covered by the GNU General Public License
  detailed at http://www.gnu.org/copyleft/gpl.html
	
  Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
  Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.client;

import java.util.List;

import android.util.Log;

import com.cloudwalk.framework3d.Obj3dDir;
import com.cloudwalk.framework3d.Tools3d;

/**
 * This class implements a glider. The class is abstract because any actual glider must have a controller. The possible controllers are AI, Network and User.
 */
public class Glider extends MovingBody {
	static float[] air = new float[] { 0, 0 }; // air movement - common to all gliders
	public float airv = 0; // vertical air movement - specific to glider
	XCModelViewer xcModelViewer;
	private String typeName;
	protected int typeID; // 0 - para, 1 - hang, 2 -sail
	public List<float[]> polar;
	private int iP; // the current point on the polar
	private float ground = 0; // ground level
	protected float timeFlying = 0;
	protected float maxSink = 0;
	public int color;
	public int color2;
	public boolean racing = false;
	boolean landed = false;
	boolean onGround = true;
	boolean launchPending = false;
	public float last_tic = 0;
	public float[] valuesFromNet;
	public float distanceFlown;
	public boolean finished = false;
	public float timeFinished = 0;

	LiftSource currentLS;

	// unique id for each instance of this class
	int myID;

	static int filmID = -1; // id of the glider that is being filmed

	/**
	 * Set this flag to true and glider will maintain a constant height.
	 */
	private boolean drone = false;

	static final int SPEED = 0;
	static final int SINK = 1;

	/**
	 * Creates a glider using the spec given in the GliderType object. We create our own copy of the 3d object because it will have changing *state*. The other
	 * data is static so we may simply assign references.
	 */
	public Glider(XCModelViewer xcModelViewer, GliderType gliderType, int id) {
		super(xcModelViewer, new Obj3dDir(gliderType.obj, true));
		Log.w("FC Glider", "myID:" + id);
		this.xcModelViewer = xcModelViewer;
		typeName = gliderType.typeName;
		typeID = gliderType.typeID;
		turnRadius = gliderType.turnRadius;
		polar = gliderType.polar;
		iP = getBestGlideIndex();
		setPolar();
		this.myID = id; // my unique id
		/**
		 * TODO: 1. Make wind an observable that may change thru the day. 2. Introduce wind shear - task designer may divide air vertically into *two* layers.
		 */
		calcMaxSInk();
		putOnStart();
	}

	public void goFaster() {
		if (iP < polar.size() - 1)
			iP++;
		setPolar();
	}

	public void goSlower() {
		if (iP > 0)
			iP--;
		setPolar();
	}

	public void setPolar(int iP) {
		if (this.iP != iP) {
			// Log.i("FC Glider", "setIP:" + iP);
			this.iP = iP;
			setPolar();
		}
	}

	/**
	 * Takes off starting at point p and heading in the direction given by the v[0] and v[1]. Note that v[2], the vertical component of v, is not used. The
	 * glide angle and speed are determined by the first point on the polar curve.
	 */
	public void takeOff() {
		onGround = false;
		racing = true;
		launchPending = false;
		landed = false;
		this.p[2] = xcModelViewer.xcModel.task.CLOUDBASE * 0.75f;
		setPolar();
		if (this.tail != null)
			this.tail.reset();
		nextTurn = 0;
		timeFlying = 0;
		distanceFlown = 0;
	}

	private static final float TO_DIST = 0.4f; // seperate gliders at launch

	/**
	 * Start flying the task. We use the glider's unique id to ensure that gliders do not start on top of each other. The dummy flag enables a glider to
	 * positioned on the ground ready for take off.
	 */

	public void putOnStart() {
		Log.w("FC putOnStart", "myID:" + myID);
		TurnPoint tp = xcModelViewer.xcModel.task.turnPointManager.turnPoints[0];
		float[] v = new float[] { tp.dx, tp.dy, 0 };

		// choose dx and dy so gliders do not start on top of each
		// other. Rather, they spread out orthogonally to the course
		// line.
		float dx = (myID - 5) * TO_DIST * tp.dy;
		float dy = -(myID - 5) * TO_DIST * tp.dx;
		float[] p = new float[] { tp.x + dx, tp.y + dy, xcModelViewer.xcModel.task.CLOUDBASE * 0.75f };

		this.p[0] = p[0];
		this.p[1] = p[1];
		this.p[2] = 0; // on the ground
		this.v[0] = v[0];
		this.v[1] = v[1];
		if (this.tail != null)
			this.tail.reset();
		putOnGround();
	}

	public void launch(boolean takeOff) {
		finished = false;
		if (takeOff)
			takeOff();
		else {
			launchPending = true;
			putOnStart();
		}
	}

	/**
	 * Sets speed and v[2] to new values when we move to a new point on the polar curve. Things get a bit fiddly because v must remain a unit vector. So we
	 * scale the x and y components of v accordingly.
	 * 
	 * <pre>
	 * 
	 *                  a
	 *          .-------------.
	 *            ----        |v[2]
	 * 	|v|=1  ----    |    
	 * 	           ----.
	 * </pre>
	 */
	protected void setPolar() {
		if (iP >= polar.size())
			iP = polar.size() - 1;
		if (iP < 0)
			iP = 0;
		if (polar.get(iP)[SINK] >= polar.get(iP)[SPEED] || polar.get(iP)[SINK] <= -polar.get(iP)[SPEED]) {
			Log.i("FC", "Invalid point on polar curve ! Sink must be less than speed. " + iP);
			return;
		}
		speed = polar.get(iP)[SPEED];
		v[2] = (!drone) ? polar.get(iP)[SINK] : 0;
		v[2] /= speed; // v is a unit vector
		scaleVxy();
		// Log.w("FC", "L/D: " + glideAngle());
	}

	public float getMaxSink() {
		return maxSink;
	}

	private void calcMaxSInk() {
		if (polar != null) {
			for (float[] fs : polar) {
				if (fs[SINK] < maxSink)
					maxSink = fs[SINK];
			}
		}
	}

	int getBestGlideIndex() {
		int pos = 0;
		float bestGlide = 0;
		if (polar != null) {
			int i = 0;
			for (float[] fs : polar) {
				if (-fs[SPEED] / fs[SINK] > bestGlide) {
					bestGlide = -fs[SPEED] / fs[SINK];
					pos = i;
				}
				i++;
			}
		}
		return pos;
	}

	void hitTheSpuds() {
		landed = true;
		putOnGround();
	}

	void putOnGround() {
		speed = 0;
		nextTurn = 0;
		v[2] = 0;
		p[2] = 0;
		scaleVxy();
		onGround = true;
		if (this.tail != null)
			this.tail.reset();
	}

	/**
	 * This utility fn is used by setPolar and hitTheSpuds. Without changing the direction of v in the xy plane change its length so that v has unit length.
	 * Note if v[2] equals 1 then there is no horizontal component to the motion. This will cause things to blow up pretty soon. The moral is keep sink < speed
	 * in all gliderType polar curves.
	 */
	private void scaleVxy() {
		float _length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1]);
		float length = (float) Math.sqrt(1 - v[2] * v[2]);
		float fudge = length / _length;
		v[0] *= fudge;
		v[1] *= fudge;
	}

	float _t = 0;

	boolean hitGround() {
		if (p[2] <= ground) {
			return true;
		}
		if (xcModelViewer.xcModel.task.hills == null)
			return false;
		for (Hill hill : xcModelViewer.xcModel.task.hills) {
			if (hill.contains(p) && hill.getHeight(p[0], p[1]) > p[2])
				return true;
		}
		return false;
	}

	public void tick(float t, float modeldt) {
		if (_t == 0)
			_t = t - modeldt;
		// float dt = t - _t;
		float dt = modeldt;
		if (!onGround) {
			// motion due to air (wind and lift/sink)
			p[0] += air[0] * dt;
			p[1] += air[1] * dt;
			if (!drone) {
				p[2] += airv * dt;
			}

			// hit the spuds ?
			if (hitGround()) {
				hitTheSpuds();
			}

			timeFlying += dt;

		} else {
			// nothing - will roll level ?
		}

		// motion due to velocity (and roll)
		super.tick(t, dt);

		if (valuesFromNet != null) {
			// Log.i("FC Glider", "t-tn: " + (t - valuesFromNet[7]) + " dt:" +
			// dt);
			setPolar(Math.round(valuesFromNet[5]));
			nextTurn = Math.round(valuesFromNet[6]);
			Log.i("FC Glider", "corrected");
			float xd = valuesFromNet[0] - p[0];
			float yd = valuesFromNet[1] - p[1];
			if (xd * xd + yd * yd > 0.1f) {
				p[0] = valuesFromNet[0];
				p[1] = valuesFromNet[1];
				p[2] = valuesFromNet[2];
			} else {
				p[0] = valuesFromNet[0] * .1f + p[0] * 0.9f;
				p[1] = valuesFromNet[1] * .1f + p[1] * 0.9f;
				p[2] = valuesFromNet[2] * .1f + p[2] * 0.9f;
			}

			v[0] = valuesFromNet[3];
			v[1] = valuesFromNet[4];
			valuesFromNet = null;
		}
		_t = t;
	}

	float interpolate(float x0, float xn, float t0, float tn, float t) {
		return x0 + (xn - x0) * (t - t0) / (tn - t0);
	}

	public final void setGround(float g) {
		ground = g;
	}

	public String getTypeName() {
		return typeName;
	}

	/**
	 * Returns the sink rate for a given point on the polar, or the current point.
	 */
	public float getSink(int i) {
		return polar.get(i)[SINK];
	}

	public float getSink() {
		return polar.get(iP)[SINK];
	}

	public float getActualSink() {
		return getSink() + airv;
	}

	/**
	 * Returns the speed for a given point on the polar, or the current point.
	 */
	public float getSpeed(int i) {
		return polar.get(i)[SPEED];
	}

	public float getSpeed() {
		return polar.get(iP)[SPEED];
	}

	/**
	 * Returns a text message giving current state of glider. See GliderAI for user friendly stuff. This is for debug.
	 */
	public String getStatusMsg() {
		if (!onGround) {
			return "P: " + Tools3d.asString(this.p);
		} else {
			return "On ground";
		}
	}

	public boolean getLanded() {
		return landed;
	}

	public boolean getOnGround() {
		return onGround;
	}

	public float[] getFocus() {
		return new float[] { p[0], p[1], p[2] };
	}

	public float[] getEye() {
		return new float[] { p[0], p[1] - 3, p[2] + 0.3f };
	}

	/**
	 * Sets the drone flag so this glider maintains a contstant height. Handy for testing ?
	 */
	void makeDrone() {
		drone = true;
	}

	float glideAngle() {
		return polar.get(iP)[SPEED] / -polar.get(iP)[SINK];
	}

	float glideAngle(int iP) {
		return polar.get(iP)[SPEED] / -polar.get(iP)[SINK];
	}

	// for debugging
	protected void finalize() {
		Log.w("FC", "Goodbye from glider(" + myID + ")");
	}

	public int getiP() {
		return iP;
	}

}
