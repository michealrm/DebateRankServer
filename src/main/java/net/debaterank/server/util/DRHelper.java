package net.debaterank.server.util;

import net.debaterank.server.models.Debater;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

public class DRHelper {

	public static final int MAX_RETRY = 5;

	public static boolean isSameName(String str1, String str2) {
		return (str1 == null && str2 == null) ||
				(str1 != null && cleanString(str1).equals(cleanString(str2)));
	}

	public static String cleanString(String str) {
		return str == null ? null : str.toLowerCase();
	}

	public static Debater findDebater(ArrayList<Debater> list, Debater debater) {
		for(Debater d : list) {
			if (debater.equals(d))
				return d;
		}
		return null;
	}

	/**
	 * Replaces null attributes in both o1 and o2. o1 and o2 must be the same type
	 * https://stackoverflow.com/questions/1301697/helper-in-order-to-copy-non-null-properties-from-object-to-another-java
	 */
	public static void replaceNull(final Object o1, final Object o2) {
		if(o1 == null || o2 == null || !o1.getClass().equals(o2.getClass())) return;
		try {
			PropertyUtils.describe(o1).entrySet().stream()
					.filter(e -> e.getValue() == null)
					.filter(e -> ! e.getKey().equals("class"))
					.forEach(e -> {
						try {
							PropertyUtils.setProperty(o1, e.getKey(), PropertyUtils.getProperty(o2, e.getKey()));
						} catch (Exception ex) {}
					});
			PropertyUtils.describe(o2).entrySet().stream()
					.filter(e -> e.getValue() == null)
					.filter(e -> ! e.getKey().equals("class"))
					.forEach(e -> {
						try {
							PropertyUtils.setProperty(o2, e.getKey(), PropertyUtils.getProperty(o1, e.getKey()));
						} catch (Exception ex) {}
					});
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {}
	}

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

	private static String getTextFromInputStream(InputStream is) throws IOException {
		BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
		return readAll(rd);
	}

	public static JSONObject readJsonObjectFromInputStream(InputStream is) throws IOException, JSONException {
		return new JSONObject(getTextFromInputStream(is));
	}

	public static JSONArray readJsonArrayFromInputStream(InputStream is) throws IOException, JSONException {
		return new JSONArray(getTextFromInputStream(is));
	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}
}
