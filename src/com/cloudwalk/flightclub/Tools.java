package com.cloudwalk.flightclub;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

public class Tools {
	public static int BROADCAST_PORT = 17128;
	public static int SERVER_PORT = 21037;

	public Tools() {
		// TODO Auto-generated constructor stub
	}

	public static float[] coordinates2LinePoint(int ptsx[], int ptsy[]) {
		float points[] = new float[ptsx.length * 2];
		for (int i = 0; i < ptsx.length; i++) {
			points[i * 2] = ptsx[i];
			points[i * 2 + 1] = ptsy[i];
		}
		return points;
	}

	public static String getIPAddress(boolean useIPv4) {
		try {
			List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface intf : interfaces) {
				List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
				for (InetAddress addr : addrs) {
					if (!addr.isLoopbackAddress()) {
						String sAddr = addr.getHostAddress().toUpperCase();
						boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
						if (useIPv4) {
							if (isIPv4)
								return sAddr;
						} else {
							if (!isIPv4) {
								int delim = sAddr.indexOf('%'); // drop ip6 port
																// suffix
								return delim < 0 ? sAddr : sAddr.substring(0, delim);
							}
						}
					}
				}
			}
		} catch (Exception ex) {
		} // for now eat exceptions
		return "";
	}

	public static void showInfoDialog(final String title, final String message, final Context context) {
		((Activity) context).runOnUiThread(new Runnable() {

			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(context);

				builder.setTitle(title);
				builder.setMessage(message);

				builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}

				}).show();
			}
		});

	}

	public static String getSubnet(Context context) {
		try {
			WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			DhcpInfo info = wifi.getDhcpInfo();
			int network = info.netmask & info.ipAddress;
			String subnet = Formatter.formatIpAddress(network); 
			return subnet.substring(0, subnet.lastIndexOf('.')+1);
		} catch (Exception e) {
			Log.e("FC TOOLS", e.getMessage(), e);
		}
		return "";
	}

}
