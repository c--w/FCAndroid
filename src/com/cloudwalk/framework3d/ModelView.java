/*
  @(#)ModelCanvas.java (part of 'Flight Club')
	
  This code is covered by the GNU General Public License
  detailed at http://www.gnu.org/copyleft/gpl.html
	
  Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
  Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.framework3d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

import com.cloudwalk.client.XCModel;

/**
 * This class is responsible for displaying a 3d model on the screen. Dragging on the canvas rotates the camera.
 * 
 * @see ModelViewer
 * @see CameraMan
 */
public class ModelView extends GLSurfaceView {


	public ModelViewer modelViewer;
	protected int backColor = Color.WHITE;
	Paint textPaint = new Paint();
	public boolean dragging = false;
	private int width, height;
	private int x0 = 0, y0 = 0;
	private int dx = 0, dy = 0;
	private float rotationStep = 0;
	public String status1 = "", status2 = "", status3 = "";
	private StringBuffer infoBuffer = new StringBuffer();
	Matrix matrix = new Matrix();

	/**
	 * The amount mouse must be dragged in order to trigger any camera movement.
	 */
	protected static int DRAG_MIN = 20;

	public ModelView(Context context) {
		super(context);
	}

	public ModelView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public void init() {
		// 4 seconds to rotate 90 degrees (at 25hz) - slooow !
		setKeepScreenOn(true);
		rotationStep = (float) Math.PI / (25 * 4);
		width = getWidth();
		height = getHeight();
		DRAG_MIN = height / 15;

		textPaint.setColor(Color.DKGRAY);
		textPaint.setTextSize(30);
		textPaint.setTypeface(Typeface.DEFAULT);
		textPaint.setStyle(Style.FILL);

	}

	void tick() {
		/*
		 * Change camera angle if dragging mouse and moused has moved more than the minimum amount
		 */
		float dtheta = 0, dz = 0;
		float zStep;

		if (!dragging)
			return;

		/*
		 * Q. How much do we change camera height by ? A. Depends how far camera is from the focus. Take 3 seconds to move up or down by the same distance that
		 * the camera is from the focus.
		 */
		zStep = modelViewer.cameraMan.getDistance() / (25 * 3);

		if (dx > DRAG_MIN)
			dtheta = -rotationStep;
		if (dx < -DRAG_MIN)
			dtheta = +rotationStep;

		if (dy > DRAG_MIN)
			dz = -zStep;
		if (dy < -DRAG_MIN)
			dz = +zStep;

		if (dtheta != 0 || dz != 0) {
			modelViewer.cameraMan.rotate(dtheta, dz);
		}
	}


	/** Displays some text at the bottom of the screen. */
	public void setText(String s, int line) {
		switch (line) {
		case 0:
			status1 = s;
			break;
		case 1:
			status2 = s;
			break;
		case 2:
			status3 = s;
			break;

		default:
			break;
		}
		;
	}

	public String getInfoText() {
		infoBuffer.setLength(0);
		infoBuffer.append(status1).append("<br/>").append(status2).append("<br/>").append(status3);
		return infoBuffer.toString();
	}

	public void handleTouch(View v, MotionEvent event) {
		//og.w("FC",""+event.getActionMasked());
		/*
		 * float x = event.getX(); if (x < v.getWidth() / 4 || x > v.getWidth() / 4 * 3) { return; }
		 */
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			x0 = (int) event.getX();
			y0 = (int) event.getY();
			dragging = true;
		} else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
			dx = 0;
			dy = 0;
			dragging = false;
		} else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
			dx = (int) (event.getX() - x0);
			dy = (int) (event.getY() - y0);
		} else {
			Log.w("FC", "" + x0 + y0);
		}
		return;
	}

}
