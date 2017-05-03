package io.micheal.debaterank.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.jsoup.nodes.Document;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.Team;
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
	public static int getDebaterID(SQLHelper sql, Debater debater) throws SQLException {
		if(debater.getID() != null)
			return debater.getID();
		ResultSet index = sql.executeQueryPreparedStatement("SELECT id, school FROM debaters WHERE first_clean<=>? AND middle_clean<=>? AND last_clean<=>? AND surname_clean<=>?", SQLHelper.cleanString(debater.getFirst()), SQLHelper.cleanString(debater.getMiddle()), SQLHelper.cleanString(debater.getLast()), SQLHelper.cleanString(debater.getSurname()));
		if(index.next()) {
			do {
				Debater clone = new Debater(debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), index.getString(2));
				if(debater.equals(clone)) {
					int ret = index.getInt(1);
					index.close();
					return ret;
				}
			} while(index.next());
		}
		index.close();
		return sql.executePreparedStatementArgs("INSERT INTO debaters (first, middle, last, surname, school, first_clean, middle_clean, last_clean, surname_clean, school_clean) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool(), SQLHelper.cleanString(debater.getFirst()), SQLHelper.cleanString(debater.getMiddle()), SQLHelper.cleanString(debater.getLast()), SQLHelper.cleanString(debater.getSurname()), SQLHelper.cleanString(debater.getSchool()));
	}
	
	/**
	 * Searches the SQL tables for the specified team, returning the <b>first</b> result. If no match is found, a debater will be created and returned
	 * @return
	 * @throws SQLException 
	 * @throws UnsupportedNameException 
	 */
	public static int getTeamID(SQLHelper sql, Team team, String event) throws SQLException {
		if(team.getID() != null)
			return team.getID();
		int left = getDebaterID(sql, team.getLeft());
		int right = getDebaterID(sql, team.getRight());
		ResultSet index = sql.executeQueryPreparedStatement("SELECT id FROM teams WHERE ((debater1=? AND debater2=?) OR (debater2=? AND debater1=?)) AND event<=>?", left, right, left, right, event);
		if(index.next()) {
			int ret = index.getInt(1);
			index.close();
			return ret; // Should only return one team
		}
		return sql.executePreparedStatementArgs("INSERT INTO teams (debater1, debater2, event) VALUES (?, ?, ?)", left, right, event);
	}
	
	public static void updateTeamIDs(SQLHelper sql, HashMap<String, Team> competitors, String event) throws SQLException {
		for(Map.Entry<String, Team> entry : competitors.entrySet()) {
			entry.getValue().getLeft().setID(getDebaterID(sql, entry.getValue().getLeft())); // In case IDs are not set
			entry.getValue().getRight().setID(getDebaterID(sql, entry.getValue().getRight()));
			
			entry.getValue().setID(getTeamID(sql, entry.getValue(), event));
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
