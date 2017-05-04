package io.micheal.debaterank.util;

import static io.micheal.debaterank.util.SQLHelper.*;

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
		ResultSet index = sql.executeQueryPreparedStatement("SELECT id, school FROM debaters WHERE first_clean<=>? AND middle_clean<=>? AND last_clean<=>? AND surname_clean<=>?", cleanString(debater.getFirst()), cleanString(debater.getMiddle()), cleanString(debater.getLast()), cleanString(debater.getSurname()));
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
		return sql.executePreparedStatementArgs("INSERT INTO debaters (first, middle, last, surname, school, first_clean, middle_clean, last_clean, surname_clean, school_clean) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool(), cleanString(debater.getFirst()), cleanString(debater.getMiddle()), cleanString(debater.getLast()), cleanString(debater.getSurname()), cleanString(debater.getSchool()));
	}
	
	/**
	 * Searches the SQL tables for the specified team. If no match is found, a debater will be created and returned
	 * @return
	 * @throws SQLException 
	 * @throws UnsupportedNameException 
	 */
	public static int getTeamID(SQLHelper sql, Team team, String event) throws SQLException {
		if(team.getID() != null)
			return team.getID();
		ResultSet index = sql.executeQueryPreparedStatement("SELECT tm.id, d1.school, d2.school FROM teams tm JOIN debaters AS d1 ON d1.id=tm.debater1 JOIN debaters AS d2 ON d2.id=tm.debater2 WHERE ((d1.first_clean<=>? AND d1.middle_clean<=>? AND d1.last_clean<=>? AND d1.surname_clean<=>? AND d2.first_clean<=>? AND d2.middle_clean<=>? AND d2.last_clean<=>? AND d2.surname_clean<=>?) OR (d2.first_clean<=>? AND d2.middle_clean<=>? AND d2.last_clean<=>? AND d2.surname_clean<=>? AND d1.first_clean<=>? AND d1.middle_clean<=>? AND d1.last_clean<=>? AND d1.surname_clean<=>?)) AND event<=>?", cleanString(team.getLeft().getFirst()), cleanString(team.getLeft().getMiddle()), cleanString(team.getLeft().getLast()), cleanString(team.getLeft().getSurname()), cleanString(team.getRight().getFirst()), cleanString(team.getRight().getMiddle()), cleanString(team.getRight().getLast()), cleanString(team.getRight().getSurname()), cleanString(team.getLeft().getFirst()), cleanString(team.getLeft().getMiddle()), cleanString(team.getLeft().getLast()), cleanString(team.getLeft().getSurname()), cleanString(team.getRight().getFirst()), cleanString(team.getRight().getMiddle()), cleanString(team.getRight().getLast()), cleanString(team.getRight().getSurname()), event);
		if(index.next()) {
			Debater clone1 = new Debater(team.getLeft().getFirst(), team.getLeft().getMiddle(), team.getLeft().getLast(), team.getLeft().getSurname(), index.getString(2));
			Debater clone2 = new Debater(team.getLeft().getFirst(), team.getLeft().getMiddle(), team.getLeft().getLast(), team.getLeft().getSurname(), index.getString(3));
			if((team.getLeft().equals(clone1) && team.getRight().equals(clone2)) || (team.getLeft().equals(clone2) && team.getRight().equals(clone1))) {
				int ret = index.getInt(1);
				index.close();
				return ret;
			}
		}
		int left = getDebaterID(sql, team.getLeft());
		int right = getDebaterID(sql, team.getRight());
		return sql.executePreparedStatementArgs("INSERT INTO teams (debater1, debater2, event) VALUES (?, ?, ?)", left, right, event);
	}
	
	public static void updateTeamIDs(SQLHelper sql, HashMap<String, Team> competitors, String event) throws SQLException {
		for(Map.Entry<String, Team> entry : competitors.entrySet())
			entry.getValue().setID(getTeamID(sql, entry.getValue(), event));
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
	
	public static Debater getDebaterFromLastName(SQLHelper sql, String last, String school) throws SQLException {
		ResultSet index = sql.executeQueryPreparedStatement("SELECT id, first, middle, last, surname, school FROM debaters WHERE last_clean<=>?", cleanString(last));
		if(index.next()) {
			do {
				Debater debater = new Debater(index.getString(2), index.getString(3), index.getString(4), index.getString(5), school);
				Debater clone = new Debater(index.getString(2), index.getString(3), index.getString(4), index.getString(5), index.getString(6));
				if(debater.equals(clone)) {
					debater.setID(index.getInt(1));
					index.close();
					return debater;
				}
			} while(index.next());
		}
		index.close();
		return null;
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
