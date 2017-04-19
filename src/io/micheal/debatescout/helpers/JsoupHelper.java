package io.micheal.debatescout.helpers;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class JsoupHelper {

	public static Document retryIfTimeout(String url, int times) throws IOException {
		Document doc = null;
		for(int i = 0;i<times-1;i++) {
			try {
				doc = Jsoup.connect(url).get();
				break;
			}
			catch(SocketTimeoutException ste) {}
		}
		doc = Jsoup.connect(url).get();
		return doc;
	}
	
}
