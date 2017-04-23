package io.micheal.debatescout.helpers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;

import io.micheal.debatescout.Debater;
import io.micheal.debatescout.UnsupportedNameException;

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
					return index.getInt(1);
				}
			} while(index.next());
		}
		return sql.executePreparedStatementArgs("INSERT INTO debaters (first, middle, last, surname, school, first_clean, middle_clean, last_clean, surname_clean, school_clean) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", debater.getFirst(), debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool(), SQLHelper.cleanString(debater.getFirst()), SQLHelper.cleanString(debater.getMiddle()), SQLHelper.cleanString(debater.getLast()), SQLHelper.cleanString(debater.getSurname()), SQLHelper.cleanString(debater.getSchool()));
	}
	
	public static void updateDebaterIDs(SQLHelper sql, HashMap<String, Debater> competitors) throws SQLException {
		for(Map.Entry<String, Debater> entry : competitors.entrySet()) {
			try {
				entry.getValue().setID(DebateHelper.getDebaterID(sql, entry.getValue()));
			} catch (UnsupportedNameException une) {}
		}
	}
	
	/**
	 * @return All debaters within the database
	 * @throws SQLException
	 */
	public static ArrayList<Debater> getDebaters(SQLHelper sql) throws SQLException {
		ResultSet debatersSet = sql.executeQuery("SELECT first, last, school FROM debaters");
		ArrayList<Debater> debaters = new ArrayList<Debater>();
		while(debatersSet.next()) {
			try {
				Debater debater = new Debater(debatersSet.getString(1) + " " + debatersSet.getString(2), debatersSet.getString(3));
				debater.setID(DebateHelper.getDebaterID(sql, debater));
				debaters.add(debater);
			} catch (UnsupportedNameException | SQLException e) {}
		}
		return debaters;
	}
	
}
