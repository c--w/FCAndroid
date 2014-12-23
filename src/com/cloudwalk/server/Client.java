package com.cloudwalk.server;

import java.net.URLEncoder;
import java.util.Random;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.util.Log;

public class Client {
	final static String TAG = "FC Client";
	final static boolean DEV = false;
	static String BASE_URL = "http://hpgf.org/fc/fc.php";
	static DefaultHttpClient client;
	static int MY_ID;
	static String ROOM;
	static {
		getHttpClient();
		MY_ID = new Random(System.currentTimeMillis()).nextInt(Integer.MAX_VALUE);
	}

	public static String send(String message) {
		try {
			Log.i(TAG, "OUT:" + message);
			String response = null;
			HttpClient client = getHttpClient();
			response = Http.get(BASE_URL + "?id=" + MY_ID + "&r=" + ROOM + "&m=" + URLEncoder.encode(message, "UTF-8")).use(client)
					.header("User-Agent", "HttpClient Wrapper").charset("UTF-8").asString();
			Log.i(TAG, "IN:" + response);
			return response;
		} catch (Exception e) {
			reset();
			Log.e(TAG, e.toString(), e);
			return "Error: Network problem, please retry...";
		}
	}

	public static HttpClient getHttpClient() {
		if (client != null)
			return client;
		try {
			// HostnameVerifier hostnameVerifier =
			// org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
			HttpParams httpParameters = new BasicHttpParams();
			// Set the timeout in milliseconds until a connection is
			// established.
			int timeoutConnection = 20000;
			HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
			// Set the default socket timeout (SO_TIMEOUT)
			// in milliseconds which is the timeout for waiting for data.
			int timeoutSocket = 15000;
			HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
			HttpProtocolParams.setContentCharset(httpParameters, "utf-8");

			SchemeRegistry registry = new SchemeRegistry();
			registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			// https scheme
			ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(httpParameters, registry);
			// HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
			client = new DefaultHttpClient(manager, httpParameters);
			client.getParams().setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, 6);
		} catch (Exception e) {
			client = new DefaultHttpClient();
		}
		return client;
	}

	private static void reset() {
		try {
			client.getConnectionManager().shutdown();
			client = null;
		} catch (Exception e) {
			Log.e(TAG, e.toString(), e);
		}
	}
}
