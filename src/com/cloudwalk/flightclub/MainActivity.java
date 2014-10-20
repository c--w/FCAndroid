package com.cloudwalk.flightclub;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.StringTokenizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
	Menu menu;
	SharedPreferences prefs;

	private class AsyncDiscover extends AsyncTask<Void, Void, String> implements DialogInterface.OnCancelListener {
		private ProgressDialog dlg;

		@Override
		protected void onPreExecute() {
			dlg = ProgressDialog.show(MainActivity.this, null, "Looking for local game server....", true, true, this);
		}

		@Override
		protected String doInBackground(Void... params) {
			String serverAddress = null;
			for (int i = 0; i < 3; i++) {
				serverAddress = discoverServer();
				if (serverAddress != null)
					break;
			}
			if (serverAddress != null)
				return serverAddress;
			return null;
		}

		@Override
		protected void onPostExecute(String serverAddress) {
			try {
				dlg.dismiss();
				Intent intent = null;
				if (serverAddress != null) {
					intent = new Intent(getApplicationContext(), StartFlightClub.class);
					intent.putExtra("net", true);
					StringTokenizer st = new StringTokenizer(serverAddress, ":");
					String address = st.nextToken();
					String glider = st.nextToken();
					String task = st.nextToken();
					intent.putExtra("server", address);
					intent.putExtra("glider", Integer.parseInt(glider));
					intent.putExtra("task", task);
					Toast.makeText(getApplicationContext(), "Found Game Server!", Toast.LENGTH_SHORT).show();
					Log.i("FC Main", "Found server: " + address + glider + task);
					startActivity(intent);
				} else {

					AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);

					alert.setTitle("Game server not found");
					alert.setMessage("Enter server address and tap 'Connect'\nor tap 'Start' to start your own server");

					// Set an EditText view to get user input
					final EditText input = new EditText(MainActivity.this);
					input.setText(Tools.getSubnet(getApplicationContext()));
					input.setInputType(EditorInfo.TYPE_CLASS_PHONE);
					alert.setView(input);
					alert.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							String server = input.getText().toString();
							Intent intent = new Intent(getApplicationContext(), StartFlightClub.class);
							intent.putExtra("net", true);
							intent.putExtra("server", server);
							startActivity(intent);

						}
					});

					alert.setNegativeButton("Start", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							Intent intent = new Intent(getApplicationContext(), ChooseActivity.class);
							intent.putExtra("net", true);
							Toast.makeText(getApplicationContext(), "Starting game server after task and glider is set...", Toast.LENGTH_LONG).show();
							startActivity(intent);
						}
					});

					alert.show();
					input.setSelection(input.getText().length());
				}
				// finish();

			} catch (Exception e) {
				// ignore
			}

		}

		@Override
		public void onCancel(DialogInterface dialog) {
			// TODO Auto-generated method stub

		}

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getInt("glider_color", 7) == 7) {
			int color = Color.rgb((int) (Math.random() * 200) + 50, (int) (Math.random() * 200) + 50, (int) (Math.random() * 200) + 50);
			prefs.edit().putInt("glider_color", color).commit();
		}
		findViewById(R.id.startNetwork).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

				if (mWifi.isConnected()) {
					new AsyncDiscover().execute((Void) null);
				} else {
					Toast.makeText(getApplicationContext(), "Connect to WIFI network for a multiplayer game.", Toast.LENGTH_SHORT).show();
				}
			}
		});
		findViewById(R.id.startNetworkOnline).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = null;
				intent = new Intent(getApplicationContext(), StartFlightClub.class);
				intent.putExtra("net", true);
				InetAddress add;
				// add = InetAddress.getByName(new
				// URL("xcserver.herokuapp.com").getHost());
				String address = "54.243.160.109:80";
				String glider = "0";
				String task = "default";
				intent.putExtra("server", address);
				intent.putExtra("glider", Integer.parseInt(glider));
				intent.putExtra("task", task);
				startActivity(intent);

			}
		});
		findViewById(R.id.startSolo).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				Intent intent = new Intent(getApplicationContext(), ChooseActivity.class);
				startActivity(intent);

				// finish();
			}
		});
	}

	private String discoverServer() {
		String serverAddress = null;
		// Find the server using UDP broadcast
		try {
			// Open a random port to send the package
			DatagramSocket c = new DatagramSocket();
			c.setSoTimeout(2000);
			c.setBroadcast(true);

			byte[] sendData = "DISCOVER_FC_REQUEST".getBytes();

			// Try the 255.255.255.255 first
			try {
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), Tools.BROADCAST_PORT);
				c.send(sendPacket);
				Log.i("FC", getClass().getName() + ">>> Request packet sent to: 255.255.255.255 (DEFAULT)");
			} catch (Exception e) {
			}

			// Broadcast the message over all the network interfaces
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();

				if (networkInterface.isLoopback() || !networkInterface.isUp()) {
					continue; // Don't want to broadcast to the loopback
								// interface
				}

				for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
					InetAddress broadcast = interfaceAddress.getBroadcast();
					if (broadcast == null) {
						continue;
					}

					// Send the broadcast package!
					try {
						DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcast, 17128);
						c.send(sendPacket);
					} catch (Exception e) {
					}

					Log.i("FC",
							getClass().getName() + ">>> Request packet sent to: " + broadcast.getHostAddress() + "; Interface: "
									+ networkInterface.getDisplayName());
				}
			}

			Log.i("FC", getClass().getName() + ">>> Done looping over all network interfaces. Now waiting for a reply!");

			// Wait for a response
			byte[] recvBuf = new byte[15000];
			DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
			c.receive(receivePacket);

			// We have a response
			Log.i("FC", getClass().getName() + ">>> Broadcast response from server: " + receivePacket.getAddress().getHostAddress());

			// Check if the message is correct
			String message = new String(receivePacket.getData()).trim();
			if (message.startsWith("DISCOVER_FC_RESPONSE")) {
				// DO SOMETHING WITH THE SERVER'S IP (for example, store it in
				// your controller)
				serverAddress = receivePacket.getAddress().getHostAddress();
				serverAddress += message.substring("DISCOVER_FC_RESPONSE".length());
			}

			// Close the port!
			c.close();
		} catch (IOException ex) {
			Log.e("FC", ex.toString());
		}
		return serverAddress;
	}

	public boolean onCreateOptionsMenu(android.view.Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		this.menu = menu;
		return true;
	};

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		try {
			if (item.getItemId() == R.id.settings) {
				Intent launchPreferencesIntent = new Intent().setClass(this, Preferences.class);
				startActivity(launchPreferencesIntent);
				return true;
			} else if (item.getItemId() == R.id.leaderboards) {
				Intent launchScoresIntent = new Intent().setClass(this, ScoreActivity.class);
				launchScoresIntent.putExtra("show_all", true);
				startActivity(launchScoresIntent);
				return true;
			}
		} catch (Exception e) {
			Log.e("FC", e.toString(), e);
		}
		return true;
	}
}
