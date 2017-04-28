package io.micheal.debaterank.util;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class JsoupHelper {

	public static Document retryIfTimeout(String url, int times) throws IOException {
		for(int i = 0;i<times-1;i++) {
			try {
				return Jsoup.connect(url).get();
			}
			catch(SocketTimeoutException ste) {}
		}
		return Jsoup.connect(url).get();
	}
	
}
