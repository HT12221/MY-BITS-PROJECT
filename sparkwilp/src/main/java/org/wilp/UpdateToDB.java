package org.wilp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class UpdateToDB {

	public boolean dataProcessor() {
		Connection c = null;
		try {
			Class.forName("org.postgresql.Driver");

			c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "omsairam");
			c.setAutoCommit(false);

			doProcess(c);

			c.commit();
			c.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public abstract void doProcess(Connection c) throws SQLException;

}
