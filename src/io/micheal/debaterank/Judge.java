package io.micheal.debaterank;

import io.micheal.debaterank.util.SQLHelper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static io.micheal.debaterank.util.DebateHelper.insertDebater;
import static io.micheal.debaterank.util.SQLHelper.cleanString;

public class Judge extends Debater {
	public Judge(String name, String school) {
		super(name, school);
	}
	public Judge(String first, String middle, String last, String surname, String school) {
		super(first, middle, last, surname, school);
	}

	public Integer getID(SQLHelper sql) throws SQLException {
		if(getRawID() != null)
			return getRawID();
		ResultSet index = sql.executeQueryPreparedStatement("SELECT id, school FROM judges WHERE first_clean<=>? AND middle_clean<=>? AND last_clean<=>? AND surname_clean<=>?", cleanString(getFirst()), cleanString(getMiddle()), cleanString(getLast()), cleanString(getSurname()));
		if(index.next()) {
			do {
				Debater clone = new Debater(getFirst(), getMiddle(), getLast(), getSurname(), index.getString(2));
				if(this.equals(clone)) {
					int ret = index.getInt(1);
					index.close();
					setID(ret);
					return ret;
				}
			} while(index.next());
		}
		index.close();
		setID(insertDebater(sql, this));
		return getRawID();
	}
}
