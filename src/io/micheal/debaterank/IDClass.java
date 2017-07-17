package io.micheal.debaterank;

import io.micheal.debaterank.util.SQLHelper;

import java.sql.SQLException;

public interface IDClass {

	public void setID(Integer id);
	public Integer getID(SQLHelper sql) throws SQLException;
	public Integer getRawID();

}
