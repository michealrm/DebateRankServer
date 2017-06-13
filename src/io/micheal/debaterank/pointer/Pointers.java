package io.micheal.debaterank.pointer;

import java.sql.SQLException;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.util.DebateHelper;
import io.micheal.debaterank.util.SQLHelper;

public class Pointers {
	
	public static void changeDebaterID(SQLHelper sql, String table, String column, int from, int to) throws SQLException {
		sql.executePreparedStatementArgs("UPDATE ? SET ? = ? WHERE ? = ?", table, column, to, column, from);
	}
	
	/**
	 * Changes debater IDs for columns that are for debater ids
	 * Needs to be updated when new tables/columns are added for debater ids
	 * @throws SQLException
	 */
	public static void changeDebaterIDForAllTables(SQLHelper sql, Debater to, Debater from) throws SQLException {
		to.setID(DebateHelper.getDebaterID(sql, to));
		from.setID(DebateHelper.getDebaterID(sql, from));
		changeDebaterID(sql, "ld_rounds", "debater", from.getID(), to.getID());
		changeDebaterID(sql, "ld_rounds", "against", from.getID(), to.getID());
		changeDebaterID(sql, "teams", "debater1", from.getID(), to.getID());
		changeDebaterID(sql, "teams", "debater2", from.getID(), to.getID());
	}
	
	/**
	 * Adds a new debater into the database and points the old debater to the new debater
	 * @throws SQLException
	 */
	public static void pointToNewDebater(SQLHelper sql, Debater newDebater, Debater oldDebater) throws SQLException {
		int newDebaterID = DebateHelper.insertDebater(sql, newDebater);
		newDebater.setID(newDebaterID);
		pointToExistingDebater(sql, newDebater, oldDebater);
	}
	
	/**
	 * Points the old debater to the existing debater
	 * @throws SQLException
	 */
	public static void pointToExistingDebater(SQLHelper sql, Debater existingDebater, Debater oldDebater) throws SQLException {
		changeDebaterIDForAllTables(sql, existingDebater, oldDebater);
		DebateHelper.deleteDebater(sql, oldDebater);
		newPointer(sql, existingDebater, oldDebater);
	}
	
	/**
	 * Adds a new pointer
	 * Note: {@code to} should have already been inserted into the database and {@code from} should have already been deleted from the database
	 * @throws SQLException 
	 */
	private static void newPointer(SQLHelper sql, Debater to, Debater from) throws SQLException {
		to.setID(DebateHelper.getDebaterID(sql, to));
		sql.executePreparedStatementArgs("INSERT INTO pointers (to, old_first, old_middle, old_last, old_surname, old_school) VALUES ", to.getID(), from.getFirst(), from.getMiddle(), from.getLast(), from.getSurname(), from.getSchool());
	}
}
