package com.cloudwalk.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.util.Log;

/**
 * This class implements the game server. When a client does something her state
 * is relayed to all the other clients. When clients join/leave the game all the
 * clients are notified. A heartbeat is sent to all the clients every N seconds.
 * Clients are told the model time when they join the game.
 */
public class XCGameServerOnline extends XCGameServer {
	private static int SERVER_PORT = 7777;
	private static int MAX_CLIENTS = 10;
	private static ServerSocket listenSocket = null;
	public static boolean keepRunning = true;
	private static XCHandler[] XCClient;
	private static Thread[] XCClientThreads;
	private static int lastXCClient;
	static XCClock clock = null;
	private static PrintWriter log = null;
	public boolean started = false;

	public XCGameServerOnline(int port, String task, int glider_type) {
		super(port, task, glider_type);
	}

	public void sendToAll(int from, String Message) {
		for (int index = 0; index < MAX_CLIENTS; index++) {
			if (XCClient[index] != null && index != from) {
				XCClient[index].send(from + "> " + Message);
			}
		}
	}

	public void sendTime(float time) {
		String msg = "Time: " + time;
		for (int index = 0; index < MAX_CLIENTS; index++) {
			if (XCClient[index] != null) {
				XCClient[index].send(msg);
			}
		}
	}

	public void disconnectAllXCClient() {
		for (int index = 0; index < MAX_CLIENTS; index++) {
			disconnectXCClient(index);
		}
	}

	public void sendWelcomeMessage(int from) {
		for (int index = 0; index < MAX_CLIENTS; index++) {
			if (XCClient[index] != null && index != from) {
				XCClient[from].send("+" + index + "> CONNECTED: " + XCClient[index].gliderType);

				if (XCClient[index].lastReceived.length() > 0) {
					XCClient[from].send(index + "> " + XCClient[index].lastReceived);
				}
			}
		}
	}

	public void stop() {
		try {
			listenSocket.close();
		} catch (Exception e) {
		}
	}

	public  boolean getKeepRunning() {
		return keepRunning;
	}

	/**
	 * Writes to log file and prints to console (whilst developing ?).
	 */
	 void log(String msg) {
		Log.i("FC", msg);
		if (log != null) {
			log.println(new Date() + "-> " + msg);
			log.flush();
		}
	}

	 void closeLog() {
		log("XC game server stopped: " + new Date());
		if (log != null) {
			log.close();
		}
	}
}

class XCHandlerOnline extends XCHandler {
	private int myID = -1;
	public int gliderType = 0; // default to 1 until user takes off
	public List<String> outMessages;
	public List<String> inMessages;
	boolean running = true;
		
	public XCHandlerOnline(int lastXCClient) {
		super(lastXCClient);
		outMessages = new ArrayList<String>();
		inMessages = new ArrayList<String>();
	}

	public void send(String message) {
		synchronized (outMessages) {
			outMessages.add(message);
		}
	}

	public void run() { // nothing to do really
	}

}

/**
 * A clock which defines model time.
 */
class XCClockOnline extends XCClock implements Runnable {

	public XCClockOnline(float modelTime, XCGameServer server) {
		super(modelTime, server);
	}

	public void start() {
		if (ticker == null) {
			ticker = new Thread(this);
			ticker.setPriority(Thread.MIN_PRIORITY);
		}
		startTick = System.currentTimeMillis();
		ticker.start();
	}

	public void stop() {
		if (ticker != null) {
			// ticker.destroy(); no such method ? anything here
			ticker = null;
		}
	}

	private float lastLog = 0;

	/**
	 * Called when a glider connects to server. We round to 1/10th second.
	 */
	public float getModelTime() {
		long currentTick = System.currentTimeMillis();
		float t = (currentTick - startTick) / 1000f + t0;
		t = t % DAY_LEN;
		t = Math.round(t * 10) / 10;

		// log
		if (t - lastLog > LOG_BEAT || t < lastLog) {
			server.log("t = " + t);
			lastLog = t;
		}
		return t;
	}

	public void run() {
		while (ticker != null) {
			long currentTick = System.currentTimeMillis();

			server.sendTime(getModelTime());

			long now = System.currentTimeMillis();
			long timeLeft = TICK_LEN * 1000 + currentTick - now;

			// idle for a bit
			if (timeLeft > 0) {
				try {
					Thread.sleep(timeLeft);
				} catch (InterruptedException e) {
				}
			}
		}
		ticker = null;
	}
}
