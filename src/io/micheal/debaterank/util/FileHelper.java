package io.micheal.debaterank.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Random;

public class FileHelper {
	public static File saveAndGetFileFromURL(URL url, String extension) throws IOException {
		URLConnection con = url.openConnection();

		Random random = new Random();
		String filename = "";
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
		char[] charArr = chars.toCharArray();
		for(int i = 0;i<10;i++)
			filename += charArr[random.nextInt(charArr.length)];
		File download = new File(System.getProperty("java.io.tmpdir"), filename + "." + extension);

		ReadableByteChannel rbc = Channels.newChannel(con.getInputStream());
		FileOutputStream fos = new FileOutputStream(download);
		try {
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
		} finally {
			fos.close();
		}
		return download;
	}
}
