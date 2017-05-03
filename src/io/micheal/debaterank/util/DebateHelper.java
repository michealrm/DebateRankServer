package io.micheal.debaterank.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.jsoup.nodes.Document;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.UnsupportedNameException;

public class DebateHelper {

	public static final Level JOT = Level.forName("JOT", 400);
	public static final Level NSDA = Level.forName("NSDA", 400);
	
	/**
	 * Searches the SQL tables for the specified name. If no match is found, a debater will be created and returned
	 * @return
	 * @throws SQLException 
	 * @throws UnsupportedNameException 
	 */
	public static int getDebaterID(SQLHelper sql, Debater debater) throws SQLException, UnsupportedNameException {
		if(debater.getID() != null)
			return debater.getID();
		ResultSet index = sql.executeQueryPreparedStatement("SELECT id, first, last, school FROM debaters WHERE first_clean<=>? AND last_clean<=>?", SQLHelper.cleanString(debater.getFirst()), SQLHelper.cleanString(debater.getLast()));
		if(index.next()) {
			do {
				Debater d = new Debater(index.getString(2) + " " + index.getString(3), index.getString(4));
				if(debater.equals(d)) {
					int ret = index.getInt(1);
					index.close();
					return ret;
				}
			} while(index.next());
		}
		index.close();
		return sql.executePreparedStatementArgs("INSERT INTO debaters (first, middle, last, surname, school, first_clean, middle_clean, last_clean, surname_clean, school_clean) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool(), SQLHelper.cleanString(debater.getFirst()), SQLHelper.cleanString(debater.getMiddle()), SQLHelper.cleanString(debater.getLast()), SQLHelper.cleanString(debater.getSurname()), SQLHelper.cleanString(debater.getSchool()));
	}
	
	public static void updateDebaterIDs(SQLHelper sql, HashMap<String, Debater> competitors) throws SQLException {
		for(Map.Entry<String, Debater> entry : competitors.entrySet()) {
			try {
				entry.getValue().setID(DebateHelper.getDebaterID(sql, entry.getValue()));
			} catch (UnsupportedNameException une) {}
		}
	}
	
	public static void updateTeamIDs(SQLHelper sql, HashMap<String, Pair<Debater, Debater>> competitors) throws SQLException {
		for(Map.Entry<String, Pair<Debater, Debater>> entry : competitors.entrySet()) {
			try {
				entry.getValue().getLeft().setID(getDebaterID(sql, entry.getValue().getLeft()));
				entry.getValue().getRight().setID(getDebaterID(sql, entry.getValue().getRight()));
			} catch (UnsupportedNameException une) {}
		}
	}
	
	/**
	 * @return All debaters within the database
	 * @throws SQLException
	 */
	public static ArrayList<Debater> getDebaters(SQLHelper sql) throws SQLException {
		ResultSet debatersSet = sql.executeQuery("SELECT id, first, middle, last, surname, school FROM debaters");
		ArrayList<Debater> debaters = new ArrayList<Debater>();
		while(debatersSet.next()) {
			try {
				Debater debater = new Debater(debatersSet.getString(2), debatersSet.getString(3), debatersSet.getString(4), debatersSet.getString(5), debatersSet.getString(6));
				debater.setID(debatersSet.getInt(1));
				debaters.add(debater);
			} catch (SQLException e) {}
		}
		debatersSet.close();
		return debaters;
	}
	
	public static Round getBracketRound(Document doc, int col) {
		int sel = doc.select("table[cellspacing=0] > tbody > tr > td.botr:eq(" + col + "), table[cellspacing=0] > tbody > tr > td.topr:eq(" + col + "), table[cellspacing=0] > tbody > tr > td.top:eq(" + col + "), table[cellspacing=0] > tbody > tr > td.btm:eq(" + col + ")").size();
		if(sel % 2 == 0 || sel == 1) {
			switch(sel) {
				case 1:
					return Round.FINALS;
				case 2:
					return Round.FINALS;
				case 4:
					return Round.SEMIS;
				case 8:
					return Round.QUARTERS;
				case 16:
					return Round.OCTOS;
				case 32:
					return Round.DOUBLE_OCTOS;
			}
		}
		return null;
	}
	
	public static boolean tournamentExists(String absUrl, int rounds, SQLHelper sql) throws SQLException {
		ResultSet tournamentExists = sql.executeQueryPreparedStatement("SELECT id FROM ld_rounds WHERE absUrl=?", absUrl);
		return tournamentExists.last() && tournamentExists.getRow() == rounds;
	}
	
}
