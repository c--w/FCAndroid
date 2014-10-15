package com.cloudwalk.flightclub;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;

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

}
