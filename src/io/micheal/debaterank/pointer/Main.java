package io.micheal.debaterank.pointer;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Scanner;

import io.micheal.debaterank.util.DataSource;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.util.DebateHelper;
import io.micheal.debaterank.util.SQLHelper;

public class Main {

	public static void main(String[] args) {
		DataSource ds = null;
		Configurations configs = new Configurations();
		try
		{
			Configuration config = configs.properties(new File("config.properties"));
		    String host = config.getString("db.host");
		    String name = config.getString("db.name");
		    String user = config.getString("db.username");
		    String pass = config.getString("db.password");
			int port = config.getInt("db.port");
			int pool = config.getInt("pool");

			ds = new DataSource("jdbc:mysql://" + host + ":" + port + "/" + name, user, pass, pool);
		} catch (Exception e) {
			System.exit(1);
		}
		SQLHelper sql = null;
		try {
			sql = new SQLHelper(ds.getBds().getConnection());
		} catch(Exception e) {
			e.printStackTrace();
			return;
		}
		Scanner in = new Scanner(System.in);
		System.out.print("Enter debater id to pointer to: ");
		int to = in.nextInt();
		System.out.print("Enter debater id to pointer from: ");
		int from = in.nextInt();
		System.out.println("-----------------");
		ArrayList<Debater> debaters = null;
		try {
			debaters = DebateHelper.getDebaters(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		Debater toDebater = null;
		Debater fromDebater = null;
		for(Debater debater : debaters) {
			try {
				if (debater.getID(sql).intValue() == to)
					toDebater = debater;
				if (debater.getID(sql).intValue() == from)
					fromDebater = debater;
			} catch(SQLException sqle) {
				continue;
			}
		}
		System.out.println("To: " + toDebater);
		System.out.println("From: " + fromDebater);
		System.out.println("Confirm (y/n)");
		in.nextLine();
		String line = in.nextLine();
		if(!line.toLowerCase().equals("y"))
			System.exit(0);
		try {
			Pointers.pointToExistingDebater(sql, toDebater, fromDebater);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		System.out.println("Done");
		try {
			sql.close();
		} catch(SQLException e) {
			e.printStackTrace();
		}
		in.close();
	}
	
}
