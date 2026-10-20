package com.booksaw.betterTeams.database;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.Utils;
import com.booksaw.betterTeams.database.api.Database;
import org.bukkit.configuration.InvalidConfigurationException;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class BetterTeamsDatabase extends Database {

	private static final String TEAM_TABLE_SCHEMA =
			"teamID VARCHAR(50) NOT NULL PRIMARY KEY, name VARCHAR(50) NOT NULL, description VARCHAR(300), open BOOLEAN DEFAULT 0, score INT DEFAULT 0, money DOUBLE DEFAULT 0, home VARCHAR(200), color CHAR(1) DEFAULT '6', echest BLOB, level INT DEFAULT 1, tag VARCHAR(50), pvp BOOLEAN DEFAULT 0";
	private static final String TEAM_TABLE_SCHEMA_WITH_ANCHOR =
			TEAM_TABLE_SCHEMA + ", anchor BOOLEAN DEFAULT 0";

	public void setupTables() {
		createTableIfNotExists(TableName.TEAM, TEAM_TABLE_SCHEMA);

		createTableIfNotExists(TableName.PLAYERS,
				"playerUUID VARCHAR(50) NOT NULL PRIMARY KEY, teamID VARCHAR(50) NOT NULL, playerRank INT NOT NULL, title VARCHAR(100), FOREIGN KEY (teamID) REFERENCES "
						+ TableName.TEAM + "(teamID) ON DELETE CASCADE");

		createTableIfNotExists(TableName.ALLYREQUESTS,
				"requestingTeamID VARCHAR(50) NOT NULL, receivingTeamID VARCHAR(50) NOT NULL, PRIMARY KEY(requestingTeamID, receivingTeamID), FOREIGN KEY (requestingTeamID) REFERENCES "
						+ TableName.TEAM
						+ "(teamID) ON DELETE CASCADE, FOREIGN KEY (receivingTeamID) REFERENCES "
						+ TableName.TEAM + "(teamID) ON DELETE CASCADE");

		createTableIfNotExists(TableName.WARPS,
				"TeamID VARCHAR(50) NOT NULL, warpInfo VARCHAR(200) NOT NULL, PRIMARY KEY(TeamID, warpInfo), FOREIGN KEY (TeamID) REFERENCES "
						+ TableName.TEAM + "(teamID) ON DELETE CASCADE");

		createTableIfNotExists(TableName.CHESTCLAIMS,
				"TeamID VARCHAR(50) NOT NULL, chestLoc VARCHAR(50) NOT NULL, PRIMARY KEY(TeamID, chestLoc), FOREIGN KEY (TeamID) REFERENCES "
						+ TableName.TEAM + "(teamID) ON DELETE CASCADE");

		createTableIfNotExists(TableName.BANS,
				"PlayerUUID VARCHAR(50) NOT NULL, TeamID VARCHAR(50) NOT NULL, PRIMARY KEY(PlayerUUID, TeamID), FOREIGN KEY (teamID) REFERENCES "
						+ TableName.TEAM + "(teamID) ON DELETE CASCADE");

		createTableIfNotExists(TableName.ALLIES,
				"team1ID VARCHAR(50) NOT NULL, team2ID VARCHAR(50) NOT NULL, PRIMARY KEY(team1ID, team2ID), FOREIGN KEY (team1ID) REFERENCES "
						+ TableName.TEAM + "(teamID) ON DELETE CASCADE, FOREIGN KEY (team2ID) REFERENCES "
						+ TableName.TEAM + "(teamID) ON DELETE CASCADE");
		createTableIfNotExists(TableName.TEAM_META,
				"teamID VARCHAR(50) NOT NULL, metaKey VARCHAR(255) NOT NULL, metaValue TEXT, PRIMARY KEY(teamID, metaKey), FOREIGN KEY (teamID) REFERENCES "
						+ TableName.TEAM + "(teamID) ON DELETE CASCADE");
		createTableIfNotExists(TableName.PLUGIN_DATA,
				"dataKey VARCHAR(100) NOT NULL PRIMARY KEY, dataValue TEXT");

		// Add anchor columns if they don't exist
		if (!hasColumn(TableName.TEAM, "anchor")) {
			addColumn(TableName.TEAM, "anchor", "BOOLEAN DEFAULT 0", "pvp", true);
		}

		if (!hasColumn(TableName.PLAYERS, "anchor")) {
			addColumn(TableName.PLAYERS, "anchor", "BOOLEAN DEFAULT 0", "title", true);
		}

		migrateEchestColumnToBlobIfNeeded();
	}

	private void migrateEchestColumnToBlobIfNeeded() {
		if ("BLOB".equalsIgnoreCase(getColumnType(TableName.TEAM, "echest"))) {
			return;
		}

		Map<String, byte[]> migratedEchests = new LinkedHashMap<>();
		try (PreparedStatement statement = selectWhere("teamID, echest", TableName.TEAM,
				"echest IS NOT NULL AND echest != ''")) {
			ResultSet resultSet = statement.executeQuery();
			while (resultSet.next()) {
				String legacyEchest = resultSet.getString("echest");
				try {
					byte[] serialized = Utils.serializeLegacyInventoryAsBytes(legacyEchest, 27);
					if (serialized != null) {
						migratedEchests.put(resultSet.getString("teamID"), serialized);
					}
				} catch (InvalidConfigurationException | RuntimeException e) {
					Main.plugin.getLogger().warning("Could not migrate legacy enderchest contents for team "
							+ resultSet.getString("teamID"));
					if (legacyEchest != null) {
						migratedEchests.put(resultSet.getString("teamID"), legacyEchest.getBytes(StandardCharsets.UTF_8));
					}
					e.printStackTrace();
				}
			}
		} catch (SQLException e) {
			Main.plugin.getLogger().warning("Could not read legacy enderchest contents before SQLite BLOB migration");
			e.printStackTrace();
		}

		executeStatement("PRAGMA foreign_keys = OFF");
		executeStatement("DROP TABLE IF EXISTS " + TableName.TEAM + "_new;");
		executeStatement("CREATE TABLE IF NOT EXISTS " + TableName.TEAM + "_new(" + TEAM_TABLE_SCHEMA_WITH_ANCHOR + ");");
		executeStatement("INSERT OR IGNORE INTO " + TableName.TEAM + "_new (teamID, name, description, open, score, money, home, color, level, tag, pvp, anchor) "
				+ "SELECT teamID, name, description, open, score, money, home, color, level, tag, pvp, anchor FROM " + TableName.TEAM + ";");
		executeStatement("DROP TABLE " + TableName.TEAM + ";");
		executeStatement("ALTER TABLE " + TableName.TEAM + "_new RENAME TO " + TableName.TEAM + ";");
		executeStatement("PRAGMA foreign_keys = ON");

		for (Map.Entry<String, byte[]> entry : migratedEchests.entrySet()) {
			executeStatement("UPDATE %s SET echest = ? WHERE teamID = ?".formatted(TableName.TEAM),
					entry.getValue(), entry.getKey());
		}
	}

	public PreparedStatement select(String select, TableName from) {
		return executeQuery("SELECT %s FROM %s".formatted(select, from.toString()));
	}

	/**
	 * @param select the element to select
	 * @param from   the table which the data is from
	 * @param where  the condition required for the data to be included
	 * @return the resultant resultSet
	 */
	public PreparedStatement selectWhere(String select, TableName from, String where, Object... placeholders) {
		return executeQuery("SELECT %s FROM %s WHERE %s;".formatted(select, from.toString(), where), placeholders);
	}

	/**
	 * @param select  the element to select
	 * @param from    the table which the data is from
	 * @param orderBy what to order the data by
	 * @return The resultant resultset
	 */
	public PreparedStatement selectOrder(String select, TableName from, String orderBy) {
		return executeQuery("SELECT %s FROM %s ORDER BY %s;".formatted(select, from.toString(), orderBy));
	}

	public PreparedStatement selectInnerJoinGroupByOrder(String select, TableName table, TableName joinTable,
														 String columToJoin, String groupBy, String orderBy) {
		return executeQuery("SELECT %s FROM %s INNER JOIN %s on (%s) GROUP BY %s ORDER BY %s;".formatted(select, table.toString(),
				joinTable.toString(), columToJoin, groupBy, orderBy));
	}

	/**
	 * Used to check if an SQL query has a result
	 *
	 * @param from  the table which the data is from
	 * @param where the condition required for the data to be included
	 * @return if the query has a result
	 */
	public boolean hasResult(TableName from, String where, Object... placeholders) {

		try (PreparedStatement ps = selectWhere("*", from, where, placeholders)) {
			ResultSet result = ps.executeQuery();
			if (result == null) {
				return false;
			}
			return result.next();
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}

	}

	/**
	 * Used to a specific column value from the
	 *
	 * @param column The column name
	 * @param from   the table
	 * @param where  the condition
	 * @return the first returned result, the specified column. Will return "" if an
	 * error occurs
	 */
	public String getResult(String column, TableName from, String where, Object... placeholders) {

		try (PreparedStatement pr = selectWhere(column, from, where, placeholders)) {
			ResultSet results = pr.executeQuery();
			if (results.next()) {
				return results.getString(column);
			}
		} catch (SQLException e) {
		}
		return "";

	}

	/**
	 * Used to delete a record from the specified table
	 *
	 * @param table     The table to update
	 * @param condition The condition for the update
	 */
	public void deleteRecord(TableName table, String condition) {
		executeStatement("DELETE FROM %s WHERE %s;".formatted(table.toString(), condition));

	}

	/**
	 * Used to modify a record in the database
	 *
	 * @param table     the table the record is in
	 * @param update    the values to update (ie "col = exp1, col2 = exp2")
	 * @param condition The condition for which records should be updated
	 */
	public void updateRecordWhere(TableName table, String field, Object update, String condition) {
		executeStatement("UPDATE %s SET %s = ? WHERE %s;".formatted(table.toString(), field, condition), update);
	}

	/**
	 * Used to modify all records in a database
	 *
	 * @param table  the table to modify
	 * @param update the value to update (ie "col = exp1, col2 = exp2")
	 */
	public void updateRecord(TableName table, String update) {
		executeStatement("UPDATE %s SET %s".formatted(table.toString(), update));
	}

	/**
	 * Used to add a record into a table
	 *
	 * @param table   The table to insert the record into
	 * @param columns The columns that data is being provided for (ie "col1, col2,
	 *                col3)
	 * @param values  the values to insert (ie "val1, val2, val3")
	 */
	public void insertRecord(TableName table, String columns, String values) {
		executeStatement("INSERT INTO %s (%s) VALUES (%s);".formatted(table.toString(), columns, values));
	}

	public void insertRecordIfNotExists(TableName table, String columns, String values) {
		executeStatement("INSERT OR IGNORE INTO %s (%s) VALUES (%s);".formatted(table.toString(), columns, values));
	}

}
