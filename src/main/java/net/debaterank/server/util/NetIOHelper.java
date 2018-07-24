package net.debaterank.server.util;

import com.oracle.javafx.jmx.json.JSONException;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Optional;

public class NetIOHelper {

	public static final int MAX_RETRY = 5;

	public static BufferedInputStream getInputStream(String endpoint, Logger log) throws IOException { // TODO: Fix mark
		BufferedInputStream iStream = null;
		for (int i = 0; i < MAX_RETRY; i++) {
			URL url = new URL(endpoint);
			HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
			if (urlConnection.getResponseCode() != 200) {
				log.warn("Bad response code: " + urlConnection.getResponseCode());
			}
			int l = Integer.parseInt(Optional.ofNullable(urlConnection.getHeaderField("Content-length")).orElse("65535"));
			if (l > 0 && urlConnection.getResponseCode() == 200) {
				iStream = new BufferedInputStream(urlConnection.getInputStream()) {
					@Override
					public void close() throws IOException {
						super.close();
					}
				};
				iStream.mark(l + 1);
				break;
			}
			log.warn("Received empty response from server. Retry in 1 sec");
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (iStream == null) {
			throw new RuntimeException("Cannot load json from server");
		}
		return iStream;
	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	public static JSONObject readJsonFromInputStream(InputStream is) throws IOException, JSONException {
		BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
		String jsonText = readAll(rd);
		return new JSONObject(jsonText);
	}
}
