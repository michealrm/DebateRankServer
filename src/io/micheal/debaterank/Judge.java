package io.micheal.debaterank;

import io.micheal.debaterank.util.SQLHelper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static io.micheal.debaterank.util.DebateHelper.insertDebater;
import static io.micheal.debaterank.util.DebateHelper.insertJudge;
import static io.micheal.debaterank.util.SQLHelper.cleanString;

public class Judge extends Debater {
	public Judge(String name, String school) {
		super(name, school);
	}
	public Judge(String first, String middle, String last, String surname, String school) {
		super(first, middle, last, surname, school);
	}
	public Judge(String first, String middle, String last, String surname, School school) {
		super(first, middle, last, surname, school);
	}

	@Override
	public Integer getID(SQLHelper sql) throws SQLException {
		if(getRawID() != null)
			return getRawID();
		ResultSet index = sql.executeQueryPreparedStatement("SELECT debaters.id, s.name FROM debaters JOIN schools AS s ON debaters.school=s.id WHERE first_clean<=>? AND middle_clean<=>? AND last_clean<=>? AND surname_clean<=>?", cleanString(getFirst()), cleanString(getMiddle()), cleanString(getLast()), cleanString(getSurname()));
		if(index.next()) {
			do {
				Judge clone = new Judge(getFirst(), getMiddle(), getLast(), getSurname(), index.getString(2));
				if(this.equals(clone)) {
					int ret = index.getInt(1);
					index.close();
					setID(ret);
					return ret;
				}
			} while(index.next());
		}
		index.close();
		setID(insertJudge(sql, this));
		return getRawID();
	}
}
