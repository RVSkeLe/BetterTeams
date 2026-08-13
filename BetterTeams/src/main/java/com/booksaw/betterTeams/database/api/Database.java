package com.booksaw.betterTeams.database.api;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.database.TableName;

import java.io.File;
import java.sql.*;

/**
 * API Class to managing databases
 *
 * @author booksaw
 */
public class Database {

	private File databaseFile;

	Connection connection;

	/**
	 * Used to setup the SQLite connection.
	 */
	public void setupConnectionFromConfiguration() {
		String configuredFile = Main.plugin.getConfig().getString("database.file", "storage.db");
		databaseFile = new File(Main.plugin.getDataFolder(), configuredFile);

		setupConnection();

	}

	/**
	 * Used to setup a connection from the provided data
	 */
	public void setupConnection() {

		Main.plugin.getLogger().info("Attempting to connect to SQLite database");

		try {
			File parent = databaseFile.getParentFile();
			if (parent != null && !parent.exists() && !parent.mkdirs()) {
				throw new SQLException("Could not create SQLite database directory: " + parent);
			}
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
			setupSqliteConnection();
			testDataSource();

		} catch (Exception e) {
			for (int i = 0; i < 3; i++) {
				Main.plugin.getLogger().severe("");
			}
			Main.plugin.getLogger()
					.severe("Connection to the SQLite database could not be established, disabling plugin");

			for (int i = 0; i < 3; i++) {
				Main.plugin.getLogger().severe("");
			}

			e.printStackTrace();

			Main.plugin.getServer().getPluginManager().disablePlugin(Main.plugin);
			return;
		}
		Main.plugin.getLogger().info("Connection with the SQLite database established");

	}

	/*
	 * This is a bit of a hacky fix - you should look into connection pooling as a
	 * more permanent solution. A popular library is HikariCP
	 * (https://github.com/brettwooldridge/HikariCP)
	 */
	private void resetConnection() {
		if (connection == null) {
			throw new IllegalStateException("No SQL connection has been established");
		}

		try {
			connection.close();
			connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
			setupSqliteConnection();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void setupSqliteConnection() throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys = ON");
		}
	}

	/**
	 * Used to test if the connection is valid
	 *
	 * @throws SQLException If a connection cannot be established, this error will
	 *                      be thrown
	 */
	private void testDataSource() throws Exception {
		if (connection == null || connection.isClosed()) {
			throw new Exception("SQL connection is not setup correctly");
		}
	}

	/**
	 * Used to close the connection to the database
	 */
	public void closeConnection() {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
			}
		} catch (Exception e) {
			Main.plugin.getLogger().severe("There was an error closing the database connection");
			e.printStackTrace();
		}
	}

	/**
	 * Checks if a column exists in the specified table.
	 *
	 * @param table  The table to check.
	 * @param column The column to check for.
	 * @return True if the column exists, false otherwise.
	 */
	public boolean hasColumn(TableName table, String column) {
		String query = "PRAGMA table_info(" + table + ")";
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					if (column.equalsIgnoreCase(rs.getString("name"))) {
						return true;
					}
				}
			}
		} catch (SQLException e) {
			Main.plugin.getLogger().severe("Error checking if column exists: " + table + "." + column);
			e.printStackTrace();
		}
		return false;
	}

	public String getColumnType(TableName table, String column) {
		String query = "PRAGMA table_info(" + table + ")";
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					if (column.equalsIgnoreCase(rs.getString("name"))) {
						return rs.getString("type");
					}
				}
			}
		} catch (SQLException e) {
			Main.plugin.getLogger().severe("Error checking column type: " + table + "." + column);
			e.printStackTrace();
		}
		return "";
	}

	protected void addColumn(TableName table, String newColumnName, String columnDefinition, String referenceColumn,
	                         boolean after) {
		String update = "ALTER TABLE " + table.toString() + " ADD " + newColumnName + " " + columnDefinition;

		executeStatement(update);
	}

	protected void addColumn(TableName table, String newColumnName, String columnDefinition) {
		addColumn(table, newColumnName, columnDefinition, null, false);
	}

	/**
	 * Used to execute an SQL statement
	 *
	 * @param statement    The SQL statement to execute
	 * @param placeholders The placeholders for the statement
	 */
	public void executeStatement(String statement, Object... placeholders) {
		try {
			executeStatementOrThrow(statement, placeholders);
		} catch (SQLException e) {
			Main.plugin.getLogger().severe("Something went wrong while executing SQL");
			Main.plugin.getLogger().severe("SQL: " + statement);
			e.printStackTrace();
		}
	}

	public void executeStatementOrThrow(String statement, Object... placeholders) throws SQLException {
		try {
			if (!connection.isValid(2)) {
				resetConnection();
			}
		} catch (SQLException e) {
			// this error is never thrown as the timeout is > 0
		}

		try (PreparedStatement ps = connection.prepareStatement(statement)) {
			for (int i = 0; i < placeholders.length; i++) {
				if (placeholders[i] instanceof byte[] bytes) {
					ps.setBytes(i + 1, bytes);
				} else {
					ps.setObject(i + 1, placeholders[i]);
				}
			}
			Main.plugin.getLogger().fine("EXECUTING " + ps);
			ps.executeUpdate();
		}
	}

	/**
	 * Used to execute an sql query
	 *
	 * @param query The query to execute
	 * @return The results of the query
	 */
	public PreparedStatement executeQuery(String query) {

		try {
			if (!connection.isValid(2)) {
				resetConnection();
			}
			return connection.prepareStatement(query);
		} catch (SQLException e) {
			Main.plugin.getLogger().severe("Something went wrong while executing SQL");
			Main.plugin.getLogger().severe("SQL: " + query);
			e.printStackTrace();
		}
		return null;

	}

	/**
	 * Used to create a table if the table does not currently exist
	 *
	 * @param tableName The name of the table
	 * @param tableInfo The column information about the table
	 */
	public void createTableIfNotExists(TableName tableName, String tableInfo) {
		executeStatement("CREATE TABLE IF NOT EXISTS " + tableName + "(" + tableInfo + ");");
	}
}
