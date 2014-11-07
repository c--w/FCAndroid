package com.cloudwalk.client;

import java.util.Arrays;

import android.util.Log;

import com.cloudwalk.framework3d.CameraMan;
import com.cloudwalk.framework3d.CameraSubject;
import com.cloudwalk.framework3d.CameraSubjectSimple;

/**
 * This class adds some xc fns to the cameraman (eg. camera modes).
 */
public class XCCameraMan extends CameraMan {

	static final String[] descriptions = new String[] { "Me", "Other", "Plan", "Far out", "Task map", "Eagle eyes", "Stay there" };

	public XCCameraMan(XCModelViewer xcModelViewer) {
		super(xcModelViewer);
	}

	/**
	 * Sets the camera up to view the first node *now*.
	 */
	void gotoTaskStart() {
		// this.setSubjectNow(((XCModelViewer)
		// modelViewer).xcModel.task.nodeManager.nodes[0], false);
		XCModel xcModel = ((XCModelViewer) modelViewer).xcModel;
		// this.depthOfVision = xcModel.task.nodeManager.nodeSpacing * 1.5f;
		Node node = xcModel.task.nodeManager.nodes[0];
		float[] focus = node.getFocus();
		float[] eye = new float[] { focus[0], focus[1] - 1, 0 };
		eye[2] = xcModel.task.nodeManager.nodeSpacing;
		setSubjectNow(new CameraSubjectSimple(eye, focus), false);
		mode = PLAN;
	}

	/**
	 * Switches between camera modes... 1. Follow user 2. Follow gaggle 3. Plan 4. The current node (from far away) 5. The entire task 6. Pilot's view 7. Stay
	 * there
	 */

	XCModel xcModel = null;
	int gliderID = -1;

	public void setMode(int inMode) {
		if (xcModel == null) {
			xcModel = ((XCModelViewer) modelViewer).xcModel;
		}

		// if *leaving* TASK view then switch off load all
		if (mode == TASK && inMode != TASK) {
			xcModel.task.nodeManager.loadAllNodes(false);
		}

		if (inMode == USER) {
			Glider x = xcModel.gliderManager.gliderUser;
			((GliderUser) x).setCameraMode(USER);
			setSubject(x, true);
			Glider.filmID = gliderID = x.myID;
		} else if (inMode == PILOT) {
			Glider x = xcModel.gliderManager.gliderUser;
			((GliderUser) x).setCameraMode(PILOT);
			setSubject(x, true);
			Glider.filmID = gliderID = x.myID;
		} else if (inMode == GAGGLE) {
			Glider x = xcModel.gliderManager.gaggleGlider();
			if (x != null) {
				setSubject(x, true);
				Glider.filmID = gliderID = x.myID;
			}
		} else if (inMode == PLAN) {
			Node node = xcModel.task.nodeManager.currentNode();
			float[] focus = node.getFocus();
			float[] eye = new float[] { focus[0], focus[1] - 1, 0 };
			eye[2] = xcModel.task.nodeManager.nodeSpacing / 2;
			setSubject(new CameraSubjectSimple(eye, focus), false);
			Glider.filmID = -1;
		} else if (inMode == NODE) {
			// setSubject((CameraSubject)
			// xcModel.task.nodeManager.currentNode(), false);
			GliderTask g = (GliderTask) xcModel.gliderManager.theGlider();
			if (g == null)
				return;
			float[] focus = g.p;
			TurnPoint tp = g.nextTP.prevTP;
			float d1 = xcModel.task.nodeManager.nodeSpacing * 0.5f;
			float d2 = -d1 / 5;
			float[] eye = new float[3];
			eye[0] = focus[0] + tp.dy * d1 + tp.dx * d2;
			eye[1] = focus[1] - tp.dx * d1 + tp.dy * d2;
			eye[2] = xcModel.task.CLOUDBASE - 0.5f;
			setSubject(new CameraSubjectSimple(eye, focus), false);
			Glider.filmID = -1;
		}

		if (inMode == TASK) {
			xcModel.task.nodeManager.loadAllNodes(true);
			setSubject((CameraSubject) xcModel.task, false);
			Glider.filmID = -1;
			setDepthOfVision(2000f);
		} else
			setDepthOfVision(100f);

		this.stayThere = (inMode == STAY_THERE);
		mode = inMode;
	}

	/**
	 * Returns text for user feedback. We append the pilot id of glider that is currently being filmed.
	 */
	String getStatusMsg() {
		String s = "Camera: " + (mode + 1) + "> " + descriptions[mode];
		if (mode == USER || mode == GAGGLE) {
			s += " (pilot #" + (gliderID + 1) + ")";
		}
		return s;
	}
}
