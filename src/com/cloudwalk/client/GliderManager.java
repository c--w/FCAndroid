/*
 * @(#)GliderManager.java (part of 'Flight Club')
 * 
 * This code is covered by the GNU General Public License
 * detailed at http://www.gnu.org/copyleft/gpl.html
 *	
 * Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 * Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.client;

import java.io.IOException;
import java.util.StringTokenizer;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.cloudwalk.framework3d.ClockObserver;
import com.cloudwalk.framework3d.Tools3d;

/**
 * This class manages all the gliders (user, networked and AI). Ths user's glider is the first in the list.
 */
public class GliderManager implements ClockObserver {
	XCModelViewer xcModelViewer;
	GliderAI[] gliderAIs;
	public GliderUser gliderUser = null;
	private int pNode_ = 0;
	private int n = 1; // count as we create gliders (user glider is always
						// zero)

	GliderTask[] netGliders; // array of connected users
	int numNet = 0; // how many connected
	boolean raceStarted = false;

	static final int MAX_USERS = 20; // Max number of Connected Users
	static final int NUM_TYPES = 4; // how many glider types
	static GliderType[] types = new GliderType[4]; // array of glider types (pg, hg, sp)
	static final String[] typeNames = new String[] { "paraglider", "hang-glider", "sailplane", "balloon" };

	public GliderManager(XCModelViewer xcModelViewer, int pilotType) {
		this.xcModelViewer = xcModelViewer;
		xcModelViewer.clock.addObserver(this);
		initTypes();
		if (pilotType >= 0)
			createUser(pilotType);
		if (xcModelViewer.netFlag) {
			netGliders = new GliderTask[MAX_USERS];
		}
	}

	/**
	 * Loads the glider types (reads polar data etc from a text file).
	 */
	private void initTypes() {
		try {
			types[0] = new GliderType(xcModelViewer, "paraglider", 0);
			types[1] = new GliderType(xcModelViewer, "hangglider", 1);
			types[2] = new GliderType(xcModelViewer, "sailplane", 2);
			types[3] = new GliderType(xcModelViewer, "balloon", 3);
		} catch (IOException e) {
			Log.e("FC", e.getMessage(), e);
			System.exit(1);
		}
	}

	int pilotType_ = -1;

	public void createUser(int pilotType) {
		int id = 0;
		if (gliderUser != null) {
			if (pilotType == pilotType_) {
				gliderUser.setColor();
				return;
			} else {
				// destroy old glider before creating another
				id = gliderUser.myID;
				gliderUser.destroyMe();
			}
		}
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(xcModelViewer.modelEnv.getContext());
		gliderUser = new GliderUser(xcModelViewer, types[pilotType], id, prefs.getString("playerName", "P" + getRandomLetter() + getRandomLetter()));
		pilotType_ = pilotType;
	}

	char getRandomLetter() {
		return (char) ('a' + (int) (Math.floor(Math.random() * 26)));
	}

	/**
	 * Creates the AI gliders. Only used in single player mode.
	 */
	protected void createAIs(int x, int y, int z, int t) {
		Log.w("FC", "CreateAI called");
		int[] nums = new int[] { x, y, z, t };
		gliderAIs = new GliderAI[nums[0] + nums[1] + nums[2] + nums[3]];

		int next = 0;
		for (int type = 0; type < 4; type++) {
			for (int j = 0; j < nums[type]; j++) {
				gliderAIs[next++] = new GliderAI(xcModelViewer, types[type], n++);
			}
		}
	}

	/**
	 * Sets the vertical air movement for a glider. We search the loaded nodes for lift sources (rather than searching the entire model.)
	 */
	private void setLift(Glider glider, Node[] nodes) {
		if (glider == null)
			return;
		float[] p = glider.p;
		LiftSource ls = null;
		for (int i = 0; i < nodes.length; i++) {
			ls = nodes[i].myLiftSource(p);
			if (ls != null) {
				break;
			}
		}
		float lift = 0;

		if (ls != null) {
			lift = ls.getLift(p);
		}

		// pass message to glider
		glider.airv = lift;
	}

	private int nextGaggle = 0;

	/**
	 * Returns the glider to watch in camera view #2. As this fn is called we cycle round the gaggle gliders.
	 */
	Glider gaggleGlider() {
		if (xcModelViewer.xcNet != null) {
			int n = 0;
			for (int i = 0; i < netGliders.length; i++) {
				if (netGliders[i] != null) {
					if (n++ == nextGaggle % numNet) {
						nextGaggle++;
						return netGliders[i];
					}
				}
			}
			return null;
		} else if (gliderAIs.length > 0) {
			nextGaggle++;
			nextGaggle %= gliderAIs.length;
			return gliderAIs[nextGaggle];
		} else {
			return null;
		}
	}

	/** Returns either user or demo glider. */
	public Glider theGlider() {
		if (xcModelViewer.xcModel.mode == XCModel.DEMO) {
			if (gliderAIs != null)
				return gliderAIs[nextGaggle];
			return null;
		} else {
			return gliderUser;
		}
	}

	private float t_ = 0; // time when loadNodes
	static final float T_INTERVAL = 1.19f; // time between calls to loadNodes()

	/**
	 * Sets the lift for each glider. Also, every T, load the nodes around 'the' glider. If networked then we don't do anything until we know from the server
	 * what the model time is.
	 */
	public void tick(float t, float dt) {
		if (xcModelViewer.xcNet != null && !xcModelViewer.netTimeFlag) {
			return;
		}
		Node[] nodes = xcModelViewer.xcModel.task.nodeManager.nodes; // check all nodes, not just loaded nodes - because AIgliders on unloaded nodes never
																		// finish

		// User
		setLift(gliderUser, nodes);

		// Net gliders or AI gliders
		if (xcModelViewer.xcNet != null) {
			for (int i = 0; i < netGliders.length; i++) {
				if (netGliders[i] != null) {
					setLift(netGliders[i], nodes);
				}
			}
		} else {
			for (int i = 0; i < gliderAIs.length; i++) {
				if (gliderAIs[i] != null) {
					this.setLift(gliderAIs[i], nodes);
				}
			}
		}

		if ((t - t_) > T_INTERVAL) {
			this.loadNodes(t);
		}
	}

	/**
	 * Loads the node(s) that *the* glider is 'nearest' to.
	 * 
	 * Constraint: Load nodes in task seq. Algorithm: pNode = n changes to pNode = n + 1 when glider is nearer to node n + 1 than node n. We need this logic for
	 * turnpoints where several nodes may overlap.
	 */
	private void loadNodes(float t) {
		Glider g = theGlider();
		if (g != null) {
			NodeManager x = xcModelViewer.xcModel.task.nodeManager;
			float[] p = g.p;
			if (pNode_ == x.nodes.length - 1) {
				// last node is already loaded
				return;
			}
			Node n1 = x.nodes[pNode_];
			Node n2 = x.nodes[pNode_ + 1];
			float d1 = n1.distanceSqd(p[0], p[1]);
			float d2 = n2.distanceSqd(p[0], p[1]);
			if (d2 < d1) {
				x.loadNodes(++pNode_, t);
			}
			// old...
			// int pNode = x.nearestNodeIndex(g.p);
			// x.loadNodes(pNode, t);
		}
		t_ = t;
	}

	/**
	 * Net stuff from Artem.
	 */

	/**
	 * Adds a new network user with the specified type of glider.
	 * 
	 * @see XCGameNetConnector
	 */
	void addUser(int index, int type) {
		Log.i("FC GM", "Adding new user at: " + index);
		netGliders[index] = new GliderTask(xcModelViewer, types[type], index);
		numNet++;
	}

	void addUserIfNecessary(int index, int type) {
		if (netGliders[index] == null)
			addUser(index, type);
	}

	/**
	 * Allows net users to change thier wing type.
	 */
	void changeNetGlider(int index, int pilotType, int color) {
		if (netGliders[index] != null) {
			if (pilotType == netGliders[index].typeID && netGliders[index].color == color) {
				// do nothing
				return;
			} else {
				// destroy old glider before creating another
				netGliders[index].destroyMe();
			}
		}
		netGliders[index] = new GliderTask(xcModelViewer, types[pilotType], index);
		netGliders[index].color = color;
		netGliders[index].obj.setColor(0, color);
	}

	void launchAIs() {
		for (int i = 0; i < gliderAIs.length; i++) {
			gliderAIs[i].putOnStart();
			gliderAIs[i].launch();
		}
		pNode_ = 0;
	}

	/**
	 * Take off - puts gliders near start point and heading towards next turn point.
	 */
	void launchUser() {
		Log.i("FCGM launchUser", "numNet:" + numNet + " raceStarted:" + raceStarted + " launchedByUser:" + gliderUser.launchPending);
		if (!xcModelViewer.netFlag) {
			gliderUser.putOnStart();
			gliderUser.launch(true, false);
		} else {
			if (raceStarted)
				checkRaceOver();
			gliderUser.launch(false, true);
			if (!raceStarted && allLaunched()) {
				startRace();
			}
		}
		pNode_ = 0;
	}

	void launchNetUser(int index) {
		Log.i("FCGM launchNetUser", "numNet:" + numNet + " raceStarted:" + raceStarted + " launchedByUser:" + gliderUser.launchPending);
		if (netGliders[index].racing)
			return;
		if (raceStarted)
			checkRaceOver();
		netGliders[index].launch(false);
		if (!raceStarted && allLaunched()) {
			startRace();
		}
		Log.i("FCGM launchNetUser", "numNet:" + numNet + " raceStarted:" + raceStarted + " launchedByUser:" + gliderUser.launchPending);
	}

	// todo - not needed ?
	void landNetUser(int index) {
		Log.i("FCGM landNetUser", "numNet:" + numNet + " raceStarted:" + raceStarted + " launchedByUser:" + gliderUser.launchPending);
		netGliders[index].hitTheSpuds();
		checkRaceOver();
		Log.i("FCGM landNetUser", "numNet:" + numNet + " raceStarted:" + raceStarted + " launchedByUser:" + gliderUser.launchPending);
	}

	void nameNetUser(int index, String playerName) {
		if (!playerName.equals(netGliders[index].getPlayerName())) {
			netGliders[index].setPlayerName(playerName);
			xcModelViewer.modelEnv.sendMessage("Player: " + playerName + " joined the game.");
		}
		// Log.i("FCGM nameNetUser", "numNet:" + numNet + " raceStarted:" + raceStarted + " launchedByUser:" + gliderUser.launchPending);
	}

	void startRace() {
		Log.i("FCGM startRace", "numNet:" + numNet + " raceStarted:" + raceStarted + " launchedByUser:" + gliderUser.launchPending);
		for (int i = 0; i < MAX_USERS; i++) {
			if (netGliders[i] != null)
				netGliders[i].launch(true);
		}
		gliderUser.launch(true, false);
		raceStarted = true;
		Log.i("FCGM startRace", "numNet:" + numNet + " raceStarted:" + raceStarted + " launchedByUser:" + gliderUser.launchPending);
	}

	public boolean checkRaceOver() {
		if (raceStarted && allLanded()) {
			raceStarted = false;
			raceOver();
			return true;
		}
		return false;
	}

	/**
	 * Sets value of myID to value assigned by the server for user's glider.
	 */
	void setMyID(int index) {
		netGliders[index] = null;
		gliderUser.myID = index;
		// now we can get the correct starting position
		gliderUser.putOnStart();
	}

	void changeUser(int index, String line) { // Change position of User at
												// index
		if (!raceStarted && !netGliders[index].launchPending) {
			Log.e("FC", "Does this ever happen?");
			launchNetUser(index);
		}

		StringTokenizer tokens = new StringTokenizer(line, ":");
		float[] values = new float[8];
		int i = 0;
		while (tokens.hasMoreTokens()) {
			String token = tokens.nextToken();
			values[i] = Tools3d.parseFloat(token);
			i++;
		}
		netGliders[index].valuesFromNet = values;
	}

	/**
	 * Deletes a connected user when user has become disconnected.
	 */
	void removeUser(int index) {
		if (index >= 0 && netGliders[index] != null) {
			xcModelViewer.modelEnv.sendMessage("Player: " + netGliders[index].getPlayerName() + " left the game.");
			netGliders[index].destroyMe();
			netGliders[index] = null;
			numNet--;
		}
	}

	boolean allLaunched() {
		int numLaunched = 0;
		for (int i = 0; i < netGliders.length; i++) {
			if (netGliders[i] == null)
				continue;
			if (netGliders[i].launchPending)
				numLaunched++;
		}
		if (numLaunched == numNet && numNet > 0 && gliderUser.launchPending)
			return true;
		return false;
	}

	boolean allLanded() { // all that were racing landed
		int numRacing = 0;
		int numLanded = 0;
		for (int i = 0; i < netGliders.length; i++) {
			if (netGliders[i] == null)
				continue;
			if (netGliders[i].racing) {
				numRacing++;
				if (netGliders[i].landed)
					numLanded++;
			}
		}
		if (gliderUser.racing) {
			numRacing++;
			if (gliderUser.landed)
				numLanded++;
		}

		if (numLanded == numRacing) {
			Log.i("FC GM", "allLanded");
			return true;
		}
		return false;
	}

	void raceOver() {
		Log.i("FC GM", "raceOver");
		Glider g;
		for (int i = 0; i < netGliders.length; i++) {
			g = netGliders[i];
			if (g == null)
				continue;
			if (g.racing) {
				g.racing = false;
				g.landed = false;
			}
		}
		gliderUser.racing = false;
		gliderUser.landed = false;
	}
}
