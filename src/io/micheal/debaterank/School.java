package io.micheal.debaterank;

import io.micheal.debaterank.util.SQLHelper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static io.micheal.debaterank.util.DebateHelper.insertDebater;
import static io.micheal.debaterank.util.DebateHelper.insertSchool;
import static io.micheal.debaterank.util.SQLHelper.cleanString;

public class School implements IDClass {
	public String name, clean, link, address, state; // clean is not necessarily SQL clean, it's NSDA clean
	private Integer id;

	public void setID(Integer id) {
		this.id = id;
	}

	public Integer getID(SQLHelper sql) throws SQLException {
		if(id != null)
			return id;
		else if(name == null)
			return null;
		ResultSet index = sql.executeQueryPreparedStatement("SELECT id FROM schools WHERE clean=?", cleanString(name)); // clean may be null
		if(index.next()) {
			int ret = index.getInt(1);
			index.close();
			id = ret;
			return ret;
		}
		index.close();
		id = insertSchool(sql, this);
		return id;
	}

	public Integer getRawID() {
		return id;
	}
}
