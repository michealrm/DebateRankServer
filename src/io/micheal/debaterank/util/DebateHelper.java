package io.micheal.debaterank.util;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.Judge;
import io.micheal.debaterank.Team;
import org.apache.logging.log4j.Level;
import org.jsoup.nodes.Document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import static io.micheal.debaterank.util.SQLHelper.cleanString;

public class DebateHelper {

	public static final Level JOT = Level.forName("JOT", 400);
	public static final Level TABROOM = Level.forName("TABROOM", 400);
	public static final Level NSDA = Level.forName("NSDA", 400);
	
	/**
	 * Searches the SQL tables for the specified team. If no match is found, a debater will be created and returned
	 * @return
	 * @throws SQLException
	 */
	public static int getTeamID(SQLHelper sql, Team team, String event) throws SQLException {
		if(team.getID() != null)
			return team.getID();
		ResultSet index = sql.executeQueryPreparedStatement("SELECT tm.id, d1.school, d2.school FROM teams tm JOIN debaters AS d1 ON d1.id=tm.debater1 JOIN debaters AS d2 ON d2.id=tm.debater2 WHERE ((d1.first_clean<=>? AND d1.middle_clean<=>? AND d1.last_clean<=>? AND d1.surname_clean<=>? AND d2.first_clean<=>? AND d2.middle_clean<=>? AND d2.last_clean<=>? AND d2.surname_clean<=>?) OR (d2.first_clean<=>? AND d2.middle_clean<=>? AND d2.last_clean<=>? AND d2.surname_clean<=>? AND d1.first_clean<=>? AND d1.middle_clean<=>? AND d1.last_clean<=>? AND d1.surname_clean<=>?)) AND event<=>?", cleanString(team.getLeft().getFirst()), cleanString(team.getLeft().getMiddle()), cleanString(team.getLeft().getLast()), cleanString(team.getLeft().getSurname()), cleanString(team.getRight().getFirst()), cleanString(team.getRight().getMiddle()), cleanString(team.getRight().getLast()), cleanString(team.getRight().getSurname()), cleanString(team.getLeft().getFirst()), cleanString(team.getLeft().getMiddle()), cleanString(team.getLeft().getLast()), cleanString(team.getLeft().getSurname()), cleanString(team.getRight().getFirst()), cleanString(team.getRight().getMiddle()), cleanString(team.getRight().getLast()), cleanString(team.getRight().getSurname()), event);
		if(index.next()) {
			Debater clone1 = new Debater(team.getLeft().getFirst(), team.getLeft().getMiddle(), team.getLeft().getLast(), team.getLeft().getSurname(), index.getString(2));
			Debater clone2 = new Debater(team.getRight().getFirst(), team.getRight().getMiddle(), team.getRight().getLast(), team.getRight().getSurname(), index.getString(3));
			if((team.getLeft().equals(clone1) && team.getRight().equals(clone2)) || (team.getLeft().equals(clone2) && team.getRight().equals(clone1))) {
				int ret = index.getInt(1);
				index.close();
				return ret;
			}
		}
		int left = team.getLeft().getID(sql);
		int right = team.getRight().getID(sql);
		return sql.executePreparedStatementArgs("INSERT INTO teams (debater1, debater2, event) VALUES (?, ?, ?)", left, right, event);
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
	
	public static ArrayList<Team> getTeams(SQLHelper sql) throws SQLException {
		ResultSet teamsSet = sql.executeQuery("SELECT d1.id, d1.first, d1.middle, d1.last, d1.surname, d1.school, d2.id, d2.first, d2.middle, d2.last, d2.surname, d2.school, tm.id FROM teams tm JOIN debaters AS d1 ON tm.debater1=d1.id JOIN debaters AS d2 ON tm.debater2=d2.id");
		ArrayList<Team> teams = new ArrayList<Team>();
		while(teamsSet.next()) {
			try {
				Debater d1 = new Debater(teamsSet.getString(2), teamsSet.getString(3), teamsSet.getString(4), teamsSet.getString(5), teamsSet.getString(6));
				d1.setID(teamsSet.getInt(1));
				Debater d2 = new Debater(teamsSet.getString(8), teamsSet.getString(9), teamsSet.getString(10), teamsSet.getString(11), teamsSet.getString(12));
				d2.setID(teamsSet.getInt(7));
				Team team = new Team(d1, d2);
				team.setID(teamsSet.getInt(13));
				teams.add(team);
			}
			catch(SQLException sqle) {}
		}
		teamsSet.close();
		return teams;
	}
	public static Team getTeamFromLastName(SQLHelper sql, String last1, String last2, String school) throws SQLException {
		ResultSet index = sql.executeQueryPreparedStatement("SELECT team.id, o.id, o.first, o.middle, o.last, o.surname, o.school, t.id, t.first, t.middle, t.last, t.surname, t.school FROM teams team JOIN debaters AS o ON o.id=team.debater1 JOIN debaters AS t ON t.id=team.debater2 WHERE (o.last_clean<=>? OR t.last_clean<=>?) AND (o.last_clean<=>? OR t.last_clean<=>?)", cleanString(last1), cleanString(last2), cleanString(last1), cleanString(last2));
		if(index.next()) {
			do {
				Debater debater = new Debater(index.getString(3), index.getString(4), index.getString(5), index.getString(6), school);
				Debater clone = new Debater(index.getString(3), index.getString(4), index.getString(5), index.getString(6), index.getString(7));
				Debater debater2 = new Debater(index.getString(9), index.getString(10), index.getString(11), index.getString(12), school);
				Debater clone2 = new Debater(index.getString(9), index.getString(10), index.getString(11), index.getString(12), index.getString(13));
				if(debater.equals(clone) && debater2.equals(clone2)) {
					debater.setID(index.getInt(1));
					debater2.setID(index.getInt(8));
					Team team = new Team(debater, debater2);
					team.setID(index.getInt(1));
					index.close();
					return team;
				}
			} while(index.next());
		}
		index.close();
		return null;
	}

	/**
	 * Inserts a debater into the database
	 * @return
	 * @throws SQLException
	 */
	public static int insertDebater(SQLHelper sql, Debater debater) throws SQLException {
		return sql.executePreparedStatementArgs("INSERT INTO debaters (first, middle, last, surname, school, first_clean, middle_clean, last_clean, surname_clean, school_clean, state, year) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool(), cleanString(debater.getFirst()), cleanString(debater.getMiddle()), cleanString(debater.getLast()), cleanString(debater.getSurname()), cleanString(debater.getSchool()), debater.getState(), debater.getYear());
	}

	/**
	 * Inserts a judge into the database
	 * @return
	 * @throws SQLException
	 */
	public static int insertJudge(SQLHelper sql, Judge judge) throws SQLException {
		return sql.executePreparedStatementArgs("INSERT INTO judges (first, middle, last, surname, school, first_clean, middle_clean, last_clean, surname_clean, school_clean) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", judge.getFirst(), judge.getMiddle(), judge.getLast(), judge.getSurname(), judge.getSchool(), cleanString(judge.getFirst()), cleanString(judge.getMiddle()), cleanString(judge.getLast()), cleanString(judge.getSurname()), cleanString(judge.getSchool()));
	}
	
	public static int deleteDebater(SQLHelper sql, Debater debater) throws SQLException {
		sql.executeStatement("SET FOREIGN_KEY_CHECKS=0");
		int id = sql.executePreparedStatementArgs("DELETE FROM debaters WHERE id=?", debater.getID(sql));
		sql.executeStatement("SET FOREIGN_KEY_CHECKS=1");
		return id;
	}
	/**
	 * Updates debater objects in pairs, along with the team id for the <b>first</b> result from the last name and school.
	 */
	public static void updateTeamWithLastNames(SQLHelper sql, Team team, String event) throws SQLException {
		ResultSet index = sql.executeQueryPreparedStatement("SELECT tm.id, d1.id, d1.first, d1.middle, d1.last, d1.surname, d1.school, d2.id, d2.first, d2.middle, d2.last, d2.surname, d2.school FROM teams tm JOIN debaters AS d1 ON d1.id=tm.debater1 JOIN debaters AS d2 ON d2.id=tm.debater2 WHERE ((d1.last_clean=? AND d2.last_clean=?) OR (d2.last_clean=? AND d1.last_clean=?)) AND ((d1.school_clean=? AND d2.school_clean=?) OR (d2.school_clean=? AND d1.school_clean=?)) AND event<=>?", cleanString(team.getLeft().getLast()), cleanString(team.getRight().getLast()), cleanString(team.getLeft().getLast()), cleanString(team.getRight().getLast()), cleanString(team.getLeft().getSchool()), cleanString(team.getRight().getSchool()), cleanString(team.getLeft().getSchool()), cleanString(team.getRight().getSchool()), event);
		if(index.next()) {
			team.newPair(new Debater(index.getString(3), index.getString(4), index.getString(5), index.getString(6), index.getString(7)), new Debater(index.getString(9), index.getString(10), index.getString(11), index.getString(12), index.getString(13)));
			team.getLeft().setID(index.getInt(2));
			team.getRight().setID(index.getInt(8));
			team.setID(index.getInt(1));
			index.close();
		}
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
	
	public static boolean tournamentExists(String absUrl, int rounds, SQLHelper sql, String table) throws SQLException {
		ResultSet tournamentExists = sql.executeQueryPreparedStatement("SELECT id FROM " + table + " WHERE absUrl=?", absUrl);
		return tournamentExists.last() && tournamentExists.getRow() == rounds;
	}
	
	/**
	 * @return debater object by last e.g. "Tillerson III" would return -> Tillerson III
	 */
	public static Debater getDebaterObjectByLast(String debater, String school) {
		if(debater.indexOf(", ") != -1)
			return new Debater(null, null, debater.substring(0, debater.indexOf(", ")), debater.substring(debater.indexOf(", ")+2), school);
		else if(debater.lastIndexOf(" ") != -1)
			return new Debater(null, null, debater.substring(0, debater.lastIndexOf(" ")), debater.substring(debater.lastIndexOf(" ")+1), school);
		else
			return new Debater(null, null, debater, null, school);
	}
	
}
