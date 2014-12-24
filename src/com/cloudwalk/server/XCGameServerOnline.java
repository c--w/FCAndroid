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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;

/**
 * This class implements the game server. When a client does something her state is relayed to all the other clients. When clients join/leave the game all the
 * clients are notified. A heartbeat is sent to all the clients every N seconds. Clients are told the model time when they join the game.
 */
public class XCGameServerOnline {
	private static int SERVER_PORT = 7777;
	private static int MAX_CLIENTS = 1;
	private static ServerSocket listenSocket = null;
	public static boolean keepRunning = true;
	private static XCHandlerOnline xcClient;
	private static Thread xcClientThread;
	static XCClockOnline clock = null;
	public static Map<String, Integer> onlinePlayersId2Num;
	public static Map<Integer, String> onlinePlayersNum2Id;
	private static PrintWriter log = null;
	public boolean started = false;
	String task;
	int glider_type;
	int onlinePlayerIdCounter = 1;

	public XCGameServerOnline(int port, String task, int glider_type) {
		SERVER_PORT = port;
		this.task = task;
		this.glider_type = glider_type;
		Client.ROOM = glider_type + task;
		xcClientThread = new Thread();
		onlinePlayersId2Num = new LinkedHashMap<String, Integer>();
		onlinePlayersNum2Id = new LinkedHashMap<Integer, String>();

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
		clock = new XCClockOnline(0, this);
		clock.start();
	}

	public void serveClients() {
		Socket clientSocket = null;
		try {
			while (keepRunning) {
				clientSocket = listenSocket.accept();
				ConnectXCClient(clientSocket);
				log("New user connected");
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

	public void disconnectXCClient() {
		if (xcClient != null) {
			xcClient = null;
			xcClientThread = null;
		}
	}

	public void ConnectXCClient(Socket clientSocket) {
		if (xcClient == null) {
			xcClient = new XCHandlerOnline(clientSocket, this);
			xcClientThread = new Thread(xcClient);
			xcClientThread.start();
			return;
		}
		return;
	}

	public void checkDisconnected(List<String> ids) {
		List<String> todelete = new ArrayList<String>();
		for (Map.Entry<String, Integer> entry : onlinePlayersId2Num.entrySet()) {
			String id = entry.getKey();
			boolean exists = false;
			for (String eid : ids) {
				if(id.equals(eid)) {
					exists = true;
					break;
				}
			}
			if(!exists)
				todelete.add(id);
		}
		for (String delete : todelete) {
			Integer localId = onlinePlayersId2Num.get(delete); 
			onlinePlayersId2Num.remove(delete);
			onlinePlayersId2Num.remove(localId);
			sendToAll(localId, "UNCONNECTED");
		}
	}

	public static String getId(String message) {
		int ind = message.indexOf("_");

		if (ind == -1) {
			Log.w("FC problem with message", message);
			return null;
		}
		return message.substring(0, ind);
	}

	public void sendToAll(String message) {
		int ind = message.indexOf("_");
		String id = getId(message);
		if (id == null) {
			Log.w("FC problem with message", message);
			return;
		}
		String message2 = message.substring(ind + 1);
		Integer num = null;
		if (onlinePlayersId2Num.containsKey(id)) {
			num = onlinePlayersId2Num.get(id);
		} else {
			num = onlinePlayerIdCounter++;
			onlinePlayersId2Num.put(id, num);
			onlinePlayersNum2Id.put(num, id);
		}
		sendToAll(num, message2);
	}

	public void sendToAll(int from, String message) {
		for (int index = 0; index < MAX_CLIENTS; index++) {
			try {
				xcClient.send(from + "> " + message);
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
	}

	public void sendTime(float time) {
		String msg = "TIME: " + time;
		if (xcClient != null) {
			try {
				xcClient.send(msg);
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
	}

	public void disconnectAllXCClient() {
		disconnectXCClient();
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
	}

	void closeLog() {
		log("XC game server stopped: " + new Date());
	}
}

class XCHandlerOnline implements Runnable {
	private Socket mySocket = null;
	private PrintWriter clientSend = null;
	private BufferedReader clientReceive = null;
	private int myID = -1;
	public String lastReceived = "";
	public int gliderType = 0; // default to 1 until user takes off
	XCGameServerOnline server;

	public XCHandlerOnline(int lastXCClient) {
		myID = lastXCClient;
	}

	public XCHandlerOnline(Socket newSocket, XCGameServerOnline server) {
		mySocket = newSocket;
		myID = 0;
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
		Client.send("UNCONNECTED");
		server.disconnectXCClient();
		server.log("User disconnected");
	}

	public void run() {
		String nextLine;
		try {
			clientSend = new PrintWriter(new OutputStreamWriter(mySocket.getOutputStream()));
			clientReceive = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));

			clientSend.println("+HELLO: " + myID + "#" + server.task + ":" + server.glider_type);
			clientSend.flush();

			clientSend.println("+TIME: " + XCGameServerOnline.clock.getModelTime());
			clientSend.flush();

			Client.send("CONNECTED: " + server.glider_type);

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
					Client.send(nextLine);
				} else if (nextLine.indexOf("WARM_FRONT") == 0) { // secret code
																	// to shut
																	// down the
																	// server
					server.log("User " + myID + ": warm_front => closing server !");
					server.disconnectAllXCClient();
					server.stop();
				} else {
					Client.send(nextLine);
					server.log("User " + myID + ": " + nextLine);
				}
				lastReceived = nextLine;
			} // end while
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
class XCClockOnline implements Runnable {
	Thread ticker = null;
	float modelTime;
	float t0;
	long startTick, sleepTime;
	XCGameServerOnline server;

	static final int TICK_LEN = 10; // how many seconds between heartbeats to
									// clients
	static final int DAY_LEN = 60 * 60 * 24; // how many seconds in the loop
	static final int LOG_BEAT = 60 * 60; // every hour

	public XCClockOnline(float modelTime, XCGameServerOnline server) {
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

	long lastSendTime = 0;

	public void run() {
		while (ticker != null) {
			long currentTick = System.currentTimeMillis();

			if (currentTick - lastSendTime > TICK_LEN * 1000) {
				long modelTime = (long) (Float.parseFloat(Client.send("TIME")));
				server.sendTime(modelTime);
				lastSendTime = currentTick;
			}
			String onlineMessages = pingOnline();
			processOnlineMessages(onlineMessages);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			// ticker = null;
		}
	}

	public String pingOnline() {
		return Client.send("PING");
	}

	public void processOnlineMessages(String onlineMessages) {
		List<String> ids = new ArrayList<String>();
		String[] messages = null;
		if (onlineMessages.startsWith(";"))
			messages = onlineMessages.substring(1).split(";");
		else
			messages = onlineMessages.split(";");
		for (String message : messages) {
			server.sendToAll(message);
			ids.add(XCGameServerOnline.getId(message));
		}
		server.checkDisconnected(ids);
	}
}
