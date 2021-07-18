package net.debaterank.server.util;

import org.apache.commons.codec.binary.Base64;
import net.debaterank.server.models.Tournament;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class EntryInfo <T extends Serializable> implements Serializable {

	private static Logger log = LogManager.getLogger(EntryInfo.class);

	public static class JOTEventLinks implements Serializable {
		public String prelims, doubleOctas, bracket;

		public JOTEventLinks(String p, String d, String b) {
			prelims = p;
			doubleOctas = d;
			bracket = b;
		}

		public String toString() {
			return prelims + " | " + doubleOctas + " | " + bracket;
		}
	}

	public static class TabroomEventInfo implements Serializable {
		public int tourn_id, event_id;
		public String endpoint;

		public TabroomEventInfo(int t, int e, String ep) {
			tourn_id = t;
			event_id = e;
			endpoint = ep;
		}

		public String toString() {
			return tourn_id + " | " + event_id + " | " + endpoint;
		}
	}

	private Tournament tournament;
	private ArrayList<T> ldEventRows;
	private ArrayList<T> pfEventRows;
	private ArrayList<T> cxEventRows;

	public Tournament getTournament() {
		return tournament;
	}

	public void setTournament(Tournament tournament) {
		this.tournament = tournament;
	}

	public void addLdEventRow(T e) {
		ldEventRows.add(e);
	}

	public void addPfEventRow(T e) {
		pfEventRows.add(e);
	}

	public void addCxEventRow(T e) {
		cxEventRows.add(e);
	}

	public ArrayList<T> getLdEventRows() {
		return ldEventRows;
	}

	public ArrayList<T> getPfEventRows() {
		return pfEventRows;
	}

	public ArrayList<T> getCxEventRows() {
		return cxEventRows;
	}

	/**
	 * Returns the specified event rows
	 * @param event LD, PF, or CX
	 * @return the specified event rows
	 */
	public ArrayList<T> getEventRows(String event) {
		if(event.equals("LD")) return ldEventRows;
		else if(event.equals("PF")) return pfEventRows;
		else if (event.equals("CX")) return cxEventRows;
		else return null;
	}

	public EntryInfo(Tournament tournament) {
		this.tournament = tournament;
		this.ldEventRows = new ArrayList<>();
		this.pfEventRows = new ArrayList<>();
		this.cxEventRows = new ArrayList<>();
	}

	public static boolean entryInfoDataExists(String dir, Tournament t) {
		return new File(getFileName(dir, t)).exists();
	}

	public static EntryInfo getFromFile(String dir, Tournament t) {
		FileInputStream fis = null;
		ObjectInputStream ois = null;
		String fileName = getFileName(dir, t);
		try {
			fis = new FileInputStream(fileName);
			ois = new ObjectInputStream(fis);
			Object o = ois.readObject();
			if(o instanceof EntryInfo) {
				EntryInfo entryInfo = (EntryInfo) o;
				t.setLdScraped(entryInfo.getTournament().isLdScraped() || t.isLdScraped());
				t.setPfScraped(entryInfo.getTournament().isPfScraped() || t.isPfScraped());
				t.setCxScraped(entryInfo.getTournament().isCxScraped() || t.isCxScraped());
				entryInfo.setTournament(t);
				log.info(t.getName() + " entry data retrieved from file");
				return entryInfo;
			}
		} catch(IOException | ClassNotFoundException e) {}
		finally {
			try {
				fis.close();
				ois.close();
			} catch(Exception e) {}
		}
		log.info("File retrieval failed for " + t.getName());
		return null;
	}

	public static void writeToFile(String dir, EntryInfo entryInfo) throws IOException {
		File dirFile = new File(dir);
		if(!dirFile.exists())
			dirFile.mkdirs();
		String fileName = getFileName(dir, entryInfo.getTournament());
		FileOutputStream fos = new FileOutputStream(fileName);
		ObjectOutputStream oos = new ObjectOutputStream(fos);

		oos.writeObject(entryInfo);
		oos.close();
		fos.close();
		log.info(entryInfo.getTournament().getName() + " entry info written to file");
	}

	public static String getFileName(String dir, Tournament t) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch(NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
		byte[] hash = md.digest(Base64.encodeBase64(t.getLink().getBytes()));
		StringBuilder sb = new StringBuilder();
		for(byte b : hash)
			sb.append(String.format("%02x", b));
		return dir + sb.toString() + ".dat";
	}

}
