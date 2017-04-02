package io.micheal.debatescout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import org.apache.commons.logging.Log;

public class SQLHelper {

	private Connection sql;
	private Statement st;
	private Log log;
	
	public SQLHelper(Log log, String host, Integer port, String name, String user, String pass) throws ClassNotFoundException, SQLException {
		this.log = log;
		Class.forName("com.mysql.cj.jdbc.Driver");
		sql = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + name + "?user=" + user + "&password=" + pass);
		st = sql.createStatement();
	}
	
	public ResultSet executeQuery(String query) throws SQLException {
		log.debug("Executing query --> " + query);
		return st.executeQuery(query);
	}
	
	public  ResultSet executeQueryPreparedStatement(String query, Object... values) throws SQLException {
		PreparedStatement ps = sql.prepareStatement(query);
		for(int i = 0;i<values.length;i++) {
			if(values[i] instanceof String)
				ps.setString(i+1, (String)values[i]);
			else if(values[i] instanceof Integer)
				ps.setInt(i+1, (Integer)values[i]);
			else if(values[i] instanceof Double)
				ps.setDouble(i+1, (Double)values[i]);
			else if(values[i] instanceof Character)
				ps.setString(i+1, ((Character)values[i]).toString());
			else if(values[i] == null)
				ps.setNull(i+1, Types.NULL);
		}
		log.debug("Executing query --> " + ps);
		return ps.executeQuery();
	}
	
	public int executePreparedStatementArgs(String query, String... values) throws SQLException {
		return executePreparedStatement(query, values);
	}
	
	public int executePreparedStatement(String query, Object[] values) throws SQLException {
		PreparedStatement ps = sql.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
		for(int i = 0;i<values.length;i++) {
			if(values[i] instanceof String)
				ps.setString(i+1, (String)values[i]);
			else if(values[i] instanceof Integer)
				ps.setInt(i+1, (Integer)values[i]);
			else if(values[i] instanceof Double)
				ps.setDouble(i+1, (Double)values[i]);
			else if(values[i] instanceof Character)
				ps.setString(i+1, ((Character)values[i]).toString());
			else if(values[i] == null)
				ps.setNull(i+1, Types.NULL);
		}
		log.debug("Executing --> " + ps);
		ps.executeUpdate();
		ResultSet rs = ps.getGeneratedKeys();
        if(rs.next())
            return rs.getInt(1);
		return -1;
	}
	
	public static String cleanString(String s) {
		return s.toLowerCase().replaceAll(".|,|'|\"", "");
	}
	
}
