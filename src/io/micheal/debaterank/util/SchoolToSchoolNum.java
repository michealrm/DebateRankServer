package io.micheal.debaterank.util;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.Main;
import io.micheal.debaterank.School;

import java.sql.SQLException;
import java.util.ArrayList;

public class SchoolToSchoolNum {

	/**
	 * Needs columns school, school_clean, and school_num in the debaters table. ** THIS IS ONLY FOR DEPRECATED DEBATE RANK DB VERSIONS **
	 */
	public static void schoolToSchoolNum(SQLHelper sql) throws SQLException {
		////////////////////////////
		// START DB SCHOOL ID FIX //
		////////////////////////////

		ArrayList<School> schoolsFix = Main.getSchools(sql);
		ArrayList<Debater> debatersFix = Main.getDebaters(sql);
		StringBuilder fixQuery = new StringBuilder("INSERT INTO debaters (id, school_num) VALUES ");
		ArrayList<Object> objects = new ArrayList<Object>();
		upper:
		for(Debater debater : debatersFix) {
			for(School school : schoolsFix) {
				if(debater.getSchool() != null && debater.getSchool().clean != null && debater.getSchool().clean.equals(school.clean)) {
					fixQuery.append("(?, ?), ");
					objects.add(debater.getID(sql));
					objects.add(school.getID(sql));
					continue upper;
				}
			}
			fixQuery.append("(?, ?), ");
			objects.add(debater.getID(sql));
			objects.add(debater.getSchool().getID(sql)); // Will insert the school as well
			schoolsFix.add(debater.getSchool());
		}
		fixQuery = new StringBuilder(fixQuery.substring(0, fixQuery.length() - 2));
		fixQuery.append(" ON DUPLICATE KEY UPDATE id=VALUES(id), school_num=VALUES(school_num)");
		System.out.println(fixQuery);
		System.out.println(objects);
		sql.executePreparedStatement(fixQuery.toString(), objects.toArray());
		System.exit(0);

		//////////////////////////
		// END SB SCHOOL ID FIX //
		//////////////////////////
	}

}
