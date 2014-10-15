/*
  XCModelViewer.java (part of 'Flight Club')
	
  This code is covered by the GNU General Public License
  detailed at http://www.gnu.org/copyleft/gpl.html
	
  Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
  Dan Burton , Nov 2001 
 */
package com.cloudwalk.client;

import java.io.IOException;

import android.os.SystemClock;
import android.util.Log;

import com.cloudwalk.framework3d.ModelView;
import com.cloudwalk.framework3d.ModelViewer;

/**
 * This class simply overrides a few factory methods to create the model viewer
 * for the xc model.
 */
public class XCModelViewer extends ModelViewer {
	public XCModelViewer(ModelView mv) {
		super(mv);
	}

	public XCModel xcModel;
	public boolean netFlag = false; // true; // flag - net game or single player
	public boolean netTimeFlag = false; // flag set to true when we first recieve the
									// model time from the server
	XCNet xcNet = null; // connection to server with a send method

	/** Connects to game server. */
	void connectToServer() {
		try {
			xcNet = new XCNet(this);
			xcNet.start();
		} catch (IOException e) {
			Log.e("FC", "Error connecting to game server ", e);
			// degrade to single player mode
			// todo: display server offline msg
		}
	}

	// Type chaining - ha !
	protected void createModel() {
		model = xcModel = new XCModel(this);
	}

	protected void createCameraMan() {
		cameraMan = new XCCameraMan(this);
		cameraMan.init();
	}

	/**
	 * Overrides the start method to include loading the task. Also connect to
	 * the game server if running in networked game mode.
	 * 
	 * Design ? create glider manager first, then xcNet then task ? xcNet must
	 * be after gliders.
	 */
	public void start() {
		if (clock != null) {
			setNetFlag();
			xcModel.loadTask(this.modelEnv.getTask(), this.modelEnv.getPilotType(), this.modelEnv.getTypeNums());
			if (netFlag) {
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						Log.w("FC", "Connect to server");
						connectToServer();
						Log.w("FC", "Connected");
						if (netFlag && xcNet == null) { // unable to connect !
							int[] typeNums = modelEnv.getTypeNums();
							xcModel.gliderManager.createAIs(typeNums[0], typeNums[1], typeNums[2], typeNums[3]);
						}
						clock.start();
						xcModel.startPlay();
					}
				}).start();
			} else {
				//xcModel.gliderManager.createAIs(3, 3, 3, 3);
				clock.start();
				xcModel.startPlay();
			}
			

		} else {
			pendingStart = true;
		}
	}

	/**
	 * Flag - connect to game server only if a host and port have been
	 * specified.
	 */
	private void setNetFlag() {
		netFlag = (modelEnv.getHostPort() != null);
	}

	/**
	 * No controls (zoom button etc.) at bottom of the screen. They were just
	 * for use with the test harnesses.
	 */
	protected void createControls() {
	}

	public void stop() {
		super.stop();
		if (xcNet != null) {
			xcNet.destroyMe(); // close socket
		}
	}
	
}
