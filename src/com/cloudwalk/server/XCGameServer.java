package com.cloudwalk.server;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

import android.util.Log;

/**
 * This class implements the game server. When a client does something her state
 * is relayed to all the other clients. When clients join/leave the game all the
 * clients are notified. A heartbeat is sent to all the clients every N seconds.
 * Clients are told the model time when they join the game.
 */
public class XCGameServer {
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
	String task;
	int glider_type;

	public XCGameServer(int port, String task, int glider_type) {
		SERVER_PORT = port;
		this.task = task;
		this.glider_type = glider_type;
		lastXCClient = 0;
		XCClient = new XCHandler[MAX_CLIENTS];
		XCClientThreads = new Thread[MAX_CLIENTS];

		try {
			listenSocket = new ServerSocket(SERVER_PORT, MAX_CLIENTS);
			started = true;
			log("XC game server started: " + new Date());
			log("On port: " + listenSocket.getLocalPort());
		} catch (IOException excpt) {
			log("Unable to listen on port " + SERVER_PORT + ": " + excpt);
			System.exit(1);
		}

		// a clock which sends a heartbeat to the clients
		clock = new XCClock(0, this);
		clock.start();
	}

	public void serveClients() {
		Socket clientSocket = null;
		try {
			while (keepRunning) {
				clientSocket = listenSocket.accept();
				int i = ConnectXCClient(clientSocket);
				log("New user " + i + " connected");
			}
		} catch (IOException except) {
			log("Failed I/O: " + except);
		} finally {
			try {
				listenSocket.close();
				clock.stop();
			} catch (Exception e) {
			}
			closeLog();
			Log.i("FC", "Goodbye from XC server !");
			System.exit(0);
		}
	}

	public void disconnectXCClient(int myID) {
		if (XCClient[myID] != null) {
			XCClient[myID] = null;
			XCClientThreads[myID] = null;
		}
	}

	public int ConnectXCClient(Socket clientSocket) {
		for (int index = 0; index < MAX_CLIENTS; index++) {
			if (XCClient[index] == null) {
				XCClient[index] = new XCHandler(clientSocket, index, this);
				XCClientThreads[index] = new Thread(XCClient[index]);
				XCClientThreads[index].start();
				return index;
			}
		}
		return -1;
	}

	public void sendToAll(int from, String Message) {
		for (int index = 0; index < MAX_CLIENTS; index++) {
			if (XCClient[index] != null && index != from) {
				XCClient[index].send(from + "> " + Message);
			}
		}
	}

	public void sendTime(float time) {
		String msg = "TIME: " + time;
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

	public boolean getKeepRunning() {
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

class XCHandler implements Runnable {
	private Socket mySocket = null;
	private PrintWriter clientSend = null;
	private BufferedReader clientReceive = null;
	private int myID = -1;
	public String lastReceived = "";
	public int gliderType = 0; // default to 1 until user takes off
	XCGameServer server;
	
	public XCHandler(int lastXCClient) {
		myID = lastXCClient;
	}

	public XCHandler(Socket newSocket, int lastXCClient, XCGameServer server) {
		mySocket = newSocket;
		myID = lastXCClient;
		this.server = server;
	}

	public void send(String Message) {
		clientSend.println(Message);
		clientSend.flush();
		if (clientSend.checkError()) {
			cleanUpOnDisconnected();
			try {
				if (clientSend != null)
					clientSend.close();
				if (clientReceive != null)
					clientReceive.close();
				if (mySocket != null)
					mySocket.close();
			} catch (IOException excpt) {
				server.log("Failed I/O: " + excpt);
			}
		}
	}

	private void cleanUpOnDisconnected() {
		server.sendToAll(myID, "UNCONNECTED");
		server.disconnectXCClient(myID);
		server.log("User " + myID + " disconnected");
	}

	public void run() {
		String nextLine;
		try {
			clientSend = new PrintWriter(new OutputStreamWriter(mySocket.getOutputStream()));
			clientReceive = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));

			clientSend.println("+HELLO: " + myID + "#" + server.task + ":" + server.glider_type);
			clientSend.flush();

			clientSend.println("+TIME: " + XCGameServer.clock.getModelTime());
			clientSend.flush();

			server.sendWelcomeMessage(myID);
			server.sendToAll(myID, "CONNECTED: " + server.glider_type);

			while ((nextLine = clientReceive.readLine()) != null) {
				nextLine = nextLine.toUpperCase();

				if (!server.getKeepRunning()) {
					break;
				} else if (nextLine.indexOf("QUIT") == 0) {
					break;
				} else if (nextLine.indexOf("TEST") == 0) {
					clientSend.println("+OK ");
					clientSend.flush();
				} else if (nextLine.indexOf("KILLALL") == 0) {
					server.disconnectAllXCClient();
				} else if (nextLine.indexOf("LAUNCH") == 0) {
					String tmp = nextLine.substring(nextLine.indexOf(":") + 2, nextLine.indexOf(":") + 3);
					gliderType = parseInt(tmp);
					server.sendToAll(myID, nextLine);
				} else if (nextLine.indexOf("WARM_FRONT") == 0) { // secret code
																	// to shut
																	// down the
																	// server
					server.log("User " + myID + ": warm_front => closing server !");
					server.disconnectAllXCClient();
					server.stop();
				} else {
					server.sendToAll(myID, nextLine);
					server.log("User " + myID + ": "+nextLine);
				}
				lastReceived = nextLine;
			}
			clientSend.println("+BYE");
			clientSend.flush();

			cleanUpOnDisconnected();
		} catch (IOException excpt) {
			if (excpt.getMessage().indexOf("Connection reset by peer:") == 0) {
				cleanUpOnDisconnected();
			} else {
				server.log("Failed I/O: " + excpt);
			}
		} finally {
			try {
				if (clientSend != null)
					clientSend.close();
				if (clientReceive != null)
					clientReceive.close();
				if (mySocket != null)
					mySocket.close();
			} catch (IOException excpt) {
				server.log("Failed I/O: " + excpt);
			}
		}
	}

	public final static int parseInt(String s) {
		return Integer.valueOf(s).intValue();
	}

}

/**
 * A clock which defines model time.
 */
class XCClock implements Runnable {
	Thread ticker = null;
	float modelTime;
	float t0;
	long startTick, sleepTime;
	XCGameServer server;

	static final int TICK_LEN = 10; // how many seconds between heartbeats to
									// clients
	static final int DAY_LEN = 60 * 60 * 24; // how many seconds in the loop
	static final int LOG_BEAT = 60 * 60; // every hour

	public XCClock(float modelTime, XCGameServer server) {
		this.t0 = modelTime;
		this.server = server;
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
