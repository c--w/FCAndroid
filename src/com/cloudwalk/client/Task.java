/*
 * @(#)Task.java (part of 'Flight Club')
 * 
 * This code is covered by the GNU General Public License
 * detailed at http://www.gnu.org/copyleft/gpl.html
 *	
 * Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 * Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;

import android.util.Log;

import com.cloudwalk.flightclub.Tools;
import com.cloudwalk.framework3d.CameraSubject;
import com.cloudwalk.framework3d.FileFormatException;
import com.cloudwalk.framework3d.Tools3d;

/**
 * This class implements a task.
 */
public class Task implements CameraSubject {
	public static int TIME = 0;
	public static int DISTANCE = 1;
	public XCModelViewer xcModelViewer;
	String taskID;
	NodeManager nodeManager;
	TurnPointManager turnPointManager;
	RoadManager roadManager;
	Trigger[] triggers;
	Hill[] hills;
	float wind_x, wind_y;
	float CLOUDBASE;
	public String desc = "";
	public float NODE_SPACING;
	public int type = TIME;

	// for the default course
	static float HEXAGON; // 8;

	/**
	 * Parses the specifed file to create the task.
	 */
	public Task(XCModelViewer xcModelViewer, String taskID) throws IOException {
		this.xcModelViewer = xcModelViewer;
		this.taskID = taskID;
		if (taskID.equals("default")) {
			generateT1Task();
		} else if (taskID.equals("default5")) {
			generateT5Task();
		} else if (taskID.equals("default6")) {
			generateT6Task();
		} else {
			parseFile(taskID);
		}
		nodeManager = new NodeManager(xcModelViewer, this);
		Glider.air[0] = wind_x;
		Glider.air[1] = wind_y;

	}

	private void generateT1Task() {
		desc = "Simple closed circuit 50km task with 2 turnpoints and GOAL = START.\nFirst point to N. \nWind SW. \nCloudbase at 1500m.";
		CLOUDBASE = 3;
		NODE_SPACING = CLOUDBASE * 12f;
		HEXAGON = CLOUDBASE * 7;
		// turn points
		float x = CLOUDBASE * 10;
		// x /= 5; // tmp - small course for testing gliding around the turn
		// points
		float[] xs = { x, x, 2 * x, x };
		float[] ys = { x, 2 * x, 1.5f * x, x };
		turnPointManager = new TurnPointManager(xcModelViewer, xs, ys);

		// wind
		wind_x = 0.1f;
		wind_y = 0.1f;

		// triggers
		triggers = new Trigger[4 * 4 * 6];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				flatLand(i * HEXAGON, j * HEXAGON, 1);
			}
		}

		// roads - specify start and end points
		float[][] r1 = new float[][] { { 0, 0, 0 }, { 0.8f * x, x, 0 }, { x, 2 * x, 0 }, { 2 * x, 4 * x, 0 } };
		float[][] r2 = new float[][] { { 0, x, 0 }, { x, 1.4f * x, 0 }, { 2 * x, 1.2f * x, 0 }, { 3 * x, 2 * x, 0 }, { 4 * x, 2 * x, 0 } };
		roadManager = new RoadManager(xcModelViewer, new float[][][] { r1, r2 });

		// hills = new Hill[1];
		// Hill hill = new Hill(this, x, 1.5f * x);
		// hills[0] = hill;
	}

	private void generateT5Task() {
		desc = "Massive 150km task with lots of clouds and 6TP.\nWind: weak SW.\nCloudbase at 1500m.";
		CLOUDBASE = 3;
		NODE_SPACING = CLOUDBASE * 12f;
		HEXAGON = CLOUDBASE * 7;
		// turn points
		float x = CLOUDBASE * 10;
		// x /= 5; // tmp - small course for testing gliding around the turn
		// points
		float[] xs = { x, x * 2, x * 3, x * 4, x * 5, x * 6, x * 7, x * 7.5f };
		float[] ys = { x * 0.5f, x * 0.2f, x * 1.5f, x * 0.3f, x * 1.4f, 0, x * 1.3f, x * 0.5f };
		turnPointManager = new TurnPointManager(xcModelViewer, xs, ys);

		// wind
		wind_x = 0.05f;
		wind_y = 0.05f;

		// triggers
		triggers = new Trigger[9 * 2 * 6];
		for (int i = 1; i < 10; i++) {
			for (int j = 0; j < 2; j++) {
				flatLand(i * HEXAGON, j * HEXAGON, 2);
			}
		}

		// roads - specify start and end points
		float[][] r1 = new float[][] { { 0, 0, 0 }, { 0.8f * x, 0.9f * x, 0 }, { 4 * x, x * 1.1f, 0 }, { 8 * x, 0.5f * x, 0 } };
		roadManager = new RoadManager(xcModelViewer, new float[][][] { r1 });

		// hills = new Hill[1];
		// Hill hill = new Hill(this, x, 1.5f * x);
		// hills[0] = hill;
	}

	private void generateT6Task() {
		desc = "Free distance task.\nWind: weak W.\nCloudbase at 1500m.";
		type = DISTANCE;
		CLOUDBASE = 3;
		NODE_SPACING = CLOUDBASE * 120f;
		HEXAGON = CLOUDBASE * 7;

		// wind
		wind_x = 0.05f;
		wind_y = 0.00f;

		// triggers
		triggers = new Trigger[19 * 1 * 7];
		float[][] r1 = new float[19][3];
		float x_ = 0, y_ = 0;
		for (int i = 1; i < 20; i++) {
			HEXAGON = (float) (CLOUDBASE * (7 + Math.pow(i, 1.46f)));
			flatLand2(x_ += HEXAGON * 0.833f, y_, 3);
			r1[i - 1] = new float[] { x_ + HEXAGON / 2 + (Tools.get01Value4(x_, y_) - 0.5f) * HEXAGON / 2,
					y_ + HEXAGON / 2 + (Tools.get01Value4(x_, y_) - 0.5f) * HEXAGON / 2, 0 };
		}

		float[] xs = { CLOUDBASE * 7, x_ + HEXAGON };
		float[] ys = { CLOUDBASE * 7 / 4, y_ + HEXAGON * .7f };
		turnPointManager = new TurnPointManager(xcModelViewer, xs, ys);

		// roads - specify start and end points
		roadManager = new RoadManager(xcModelViewer, new float[][][] { r1 });

		// hills = new Hill[1];
		// Hill hill = new Hill(this, x, 1.5f * x);
		// hills[0] = hill;
	}

	private void parseFile(String taskID) throws IOException {
		InputStream is = xcModelViewer.modelEnv.openFile(taskID + ".task");
		StreamTokenizer st = new StreamTokenizer(new BufferedReader(new InputStreamReader(is)));
		st.eolIsSignificant(true);
		st.commentChar('#');
		st.wordChars(':', ':');
		st.wordChars('_', '_'); // TODO

		// gobble EOL's due to comments
		while (st.nextToken() == StreamTokenizer.TT_EOL) {
			;
		}

		// Description
		if ("DESC:".equals(st.sval)) {
			st.nextToken();
			desc = st.sval.replace('_', ' ');
			desc = desc.replaceAll("\\. ", ".\n");
			st.nextToken(); // new line
			st.nextToken(); // new line
		}
		Log.i("FC Task", "Desc:" + desc);

		// Cloudbase
		if ("CLOUDBASE:".equals(st.sval)) {
			st.nextToken();
			CLOUDBASE = (float) st.nval;
			st.nextToken(); // new line
			st.nextToken(); // new line
		}
		NODE_SPACING = CLOUDBASE * 12f;
		Log.i("FC Task", "Cloudbase:" + CLOUDBASE);
		if (st.ttype == StreamTokenizer.TT_EOL)
			st.nextToken();
		if (!"WIND:".equals(st.sval)) {
			throw new FileFormatException("Unable to read wind: " + st.sval);
		}
		st.nextToken();
		wind_x = (float) st.nval;
		st.nextToken();
		wind_y = (float) st.nval;
		st.nextToken(); // new line
		Log.i("FC Task", "Wind:" + wind_x + " " + wind_y);

		// turn points
		turnPointManager = new TurnPointManager(xcModelViewer, st);

		// triggers
		st.nextToken();
		if (!"NUM_TRIGGERS:".equals(st.sval))
			throw new FileFormatException("Unable to read number of triggers: " + st.sval);
		st.nextToken();
		int numTriggers = (int) st.nval;
		Log.i("FC Task", "Triggers:" + numTriggers);

		triggers = new Trigger[numTriggers];
		st.nextToken();

		for (int i = 0; i < triggers.length; i++) {
			triggers[i] = new Trigger(xcModelViewer, st);
		}

		// roads
		roadManager = new RoadManager(xcModelViewer, st);
		st.nextToken();
		if ("NUM_HILLS:".equals(st.sval)) {
			st.nextToken();
			int numHills = (int) st.nval;
			Log.i("FC Task", "Hills:" + numHills);

			hills = new Hill[numHills];
			st.nextToken();
			for (int i = 0; i < hills.length; i++) {
				hills[i] = new Hill(this, st);
			}
		}
		// gobble any trailing EOL's
		while (st.nextToken() == StreamTokenizer.TT_EOL) {
			;
		}
		Log.i("FC Task", "Parse end");

		// should be at end of file
		is.close();
		if (st.ttype != StreamTokenizer.TT_EOF)
			throw new FileFormatException(st.toString());
	}

	/**
	 * Returns the total distance of this task.
	 */
	float getTotalDistance() {
		return turnPointManager.getTotalDistance();
	}

	/** Prints debug info. */
	void asString() {
		Log.i("FC", "Task: " + taskID);
		turnPointManager.asString();
		roadManager.asString();
		nodeManager.asString();
	}

	/**
	 * Returns the mid point of the bounding box.
	 */
	public float[] getFocus() {
		float[] a = new float[3];
		float[] b = new float[3];
		float[] p = new float[3];
		turnPointManager.boundingBox(a, b);
		Tools3d.add(a, b, p);
		p[0] /= 2;
		p[1] /= 2;
		return p;
	}

	/**
	 * Returns the 'near' corner of the bounding box but with the z component set to either the width or the height of the box, whichever is greater.
	 * 
	 * Changed to mid point. ?
	 */
	public float[] getEye() {
		float[] a = new float[3];
		float[] b = new float[3];
		float[] p = new float[3];
		turnPointManager.boundingBox(a, b);
		Tools3d.subtract(b, a, p);

		// aspect ratio
		if (p[0] > p[1]) {
			// x maps to screen height
			a[2] = p[0] * 0.7f;
		} else {
			// y maps to screen width
			a[2] = p[1] * 1.4f;
		}

		a[0] += p[0] * 0.5f;
		a[1] += p[1] * 0.4f; // look along the y axis
		return a;
	}

	private int next = 0;

	/**
	 * Produces a hexagon structure of thermal triggers.
	 * 
	 * <pre>
	 * 
	 *        y
	 *        |
	 *        |
	 *        .--->x
	 *                 .
	 *             .       .
	 * 
	 *             .       .
	 *                 .
	 * </pre>
	 */
	void flatLand(float x0, float y0, int version) {
		Trigger trigger;
		float y1, x1;

		y1 = y0 + HEXAGON;
		x1 = x0 + HEXAGON;
		float dh = HEXAGON / 6;
		// Log.i("FC TASK", "X:" + x1 + " Y:" + y1 + " dH:" + dh);
		trigger = new Trigger(xcModelViewer, x0 + HEXAGON / 2, y0 + dh, version);
		triggers[next++] = trigger;

		trigger = new Trigger(xcModelViewer, x0 + HEXAGON / 2, y1 - dh, version);
		triggers[next++] = trigger;

		trigger = new Trigger(xcModelViewer, x0 + dh, y0 + 2 * dh, version);
		triggers[next++] = trigger;

		trigger = new Trigger(xcModelViewer, x0 + dh, y1 - 2 * dh, version);
		triggers[next++] = trigger;

		trigger = new Trigger(xcModelViewer, x1 - dh, y0 + 2 * dh, version);
		triggers[next++] = trigger;

		trigger = new Trigger(xcModelViewer, x1 - dh, y1 - 2 * dh, version);
		triggers[next++] = trigger;
	}

	void flatLand2(float x0, float y0, int version) {
		Trigger trigger;
		float y1, x1;
		y1 = y0 + HEXAGON;
		x1 = x0 + HEXAGON;
		float dh = HEXAGON / 6;
		float x, y, xr, yr;
		x = x0 + HEXAGON / 2;
		y = y0 + HEXAGON / 2;
		xr = x + (Tools.get01Value4(x, y) - 0.5f) * HEXAGON / 10f;
		yr = y + (Tools.get01Value4(y, x) - 0.5f) * HEXAGON / 10f;
		trigger = new Trigger(xcModelViewer, xr, yr, version);
		triggers[next++] = trigger;

		x = x0 + HEXAGON / 2;
		y = y0 + dh;
		xr = x + (Tools.get01Value4(x, y) - 0.5f) * HEXAGON / 10f;
		yr = y + (Tools.get01Value4(y, x) - 0.5f) * HEXAGON / 10f;
		trigger = new Trigger(xcModelViewer, xr, yr, version);
		triggers[next++] = trigger;

		x = x0 + HEXAGON / 2;
		y = y1 - dh;
		xr = x + (Tools.get01Value4(x, y) - 0.5f) * HEXAGON / 10f;
		yr = y + (Tools.get01Value4(y, x) - 0.5f) * HEXAGON / 10f;
		trigger = new Trigger(xcModelViewer, xr, yr, version);
		triggers[next++] = trigger;

		x = x0 + dh;
		y = y0 + 2 * dh;
		xr = x + (Tools.get01Value4(x, y) - 0.5f) * HEXAGON / 10f;
		yr = y + (Tools.get01Value4(y, x) - 0.5f) * HEXAGON / 10f;
		trigger = new Trigger(xcModelViewer, xr, yr, version);
		triggers[next++] = trigger;

		x = x0 + dh;
		y = y1 - 2 * dh;
		xr = x + (Tools.get01Value4(x, y) - 0.5f) * HEXAGON / 10f;
		yr = y + (Tools.get01Value4(y, x) - 0.5f) * HEXAGON / 10f;
		trigger = new Trigger(xcModelViewer, xr, yr, version);
		triggers[next++] = trigger;

		x = x1 - dh;
		y = y0 + 2 * dh;
		xr = x + (Tools.get01Value4(x, y) - 0.5f) * HEXAGON / 10f;
		yr = y + (Tools.get01Value4(y, x) - 0.5f) * HEXAGON / 10f;
		trigger = new Trigger(xcModelViewer, xr, yr, version);
		triggers[next++] = trigger;

		x = x1 - dh;
		y = y1 - 2 * dh;
		xr = x + (Tools.get01Value4(x, y) - 0.5f) * HEXAGON / 10f;
		yr = y + (Tools.get01Value4(y, x) - 0.5f) * HEXAGON / 10f;
		trigger = new Trigger(xcModelViewer, xr, yr, version);
		triggers[next++] = trigger;
	}
}
