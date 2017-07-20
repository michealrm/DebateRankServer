package io.micheal.debaterank.util;

import org.apache.commons.dbcp2.BasicDataSource;

public class DataSource {

	private static final String DRIVER_CLASS_NAME = "com.mysql.jdbc.Driver";

	private BasicDataSource bds = new BasicDataSource();

	public DataSource(String dbUrl, String dbUser, String dbPassword, int poolSize) {
		bds.setDriverClassName(DRIVER_CLASS_NAME);
		bds.setUrl(dbUrl);
		bds.setUsername(dbUser);
		bds.setPassword(dbPassword);
		bds.setInitialSize(poolSize);
	}

	public BasicDataSource getBds() {
		return bds;
	}

	public void setBds(BasicDataSource bds) {
		this.bds = bds;
	}
}
