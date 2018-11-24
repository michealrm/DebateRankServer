package net.debaterank.server.util;

import org.jsoup.nodes.Document;

public class DRHelper {

	public static String getBracketRound(Document doc, int col) {
		int sel = doc.select("table[cellspacing=0] > tbody > tr > td.botr:eq(" + col + "), table[cellspacing=0] > tbody > tr > td.topr:eq(" + col + "), table[cellspacing=0] > tbody > tr > td.top:eq(" + col + "), table[cellspacing=0] > tbody > tr > td.btm:eq(" + col + ")").size();
		if(sel % 2 == 0 || sel == 1) {
			switch(sel) {
				case 1:
				case 2:
					return "F";
				case 4:
					return "S";
				case 8:
					return "Q";
				case 16:
					return "O";
				case 32:
					return "DO";
			}
		}
		return null;
	}

	public static boolean isSameName(String str1, String str2) {
		return (str1 == null && str2 == null) || (str1 != null && cleanString(str1).equals(cleanString(str2)));
	}

	public static String cleanString(String str) {
		return str.toLowerCase();
	}

}
