package io.micheal.debaterank.pointer;

import java.sql.SQLException;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.util.DebateHelper;
import io.micheal.debaterank.util.SQLHelper;

/* TODO: Need to update this file as I add more meta-data
 * For numbers, I should add them together
 */
public class Pointers {
	
	public static void changeDebaterID(SQLHelper sql, String table, String column, int from, int to) throws SQLException {
		sql.executePreparedStatementArgs("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?", to, from);
	}
	
	/**
	 * Changes debater IDs for columns that are for debater ids
	 * Needs to be updated when new tables/columns are added for debater ids
	 * @throws SQLException
	 */
	public static void changeDebaterIDForAllTables(SQLHelper sql, Debater to, Debater from) throws SQLException {
		changeDebaterID(sql, "ld_rounds", "debater", from.getID(sql), to.getID(sql));
		changeDebaterID(sql, "ld_rounds", "against", from.getID(sql), to.getID(sql));
		changeDebaterID(sql, "teams", "debater1", from.getID(sql), to.getID(sql));
		changeDebaterID(sql, "teams", "debater2", from.getID(sql), to.getID(sql));
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
		sql.executePreparedStatementArgs("INSERT INTO pointers (`to`, old_first, old_middle, old_last, old_surname, old_school) VALUES (?, ?, ?, ?, ?, ?)", to.getID(sql), from.getFirst(), from.getMiddle(), from.getLast(), from.getSurname(), from.getSchool());
	}
}
