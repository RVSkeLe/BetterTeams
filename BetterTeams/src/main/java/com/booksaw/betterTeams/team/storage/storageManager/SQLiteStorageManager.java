package com.booksaw.betterTeams.team.storage.storageManager;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import com.booksaw.betterTeams.database.BetterTeamsDatabase;
import com.booksaw.betterTeams.database.TableName;
import com.booksaw.betterTeams.team.LocationSetComponent;
import com.booksaw.betterTeams.team.TeamManager;
import com.booksaw.betterTeams.team.storage.migration.LegacyStorageMigrationException;
import com.booksaw.betterTeams.team.storage.migration.LegacyStorageMigrator;
import com.booksaw.betterTeams.team.storage.team.SQLiteTeamStorage;
import com.booksaw.betterTeams.team.storage.team.TeamStorage;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import static com.booksaw.betterTeams.util.StringUtil.EMPTY_STRING_ARRAY;

public class SQLiteStorageManager extends TeamManager implements Listener {

	@Getter
	private BetterTeamsDatabase database;

	public SQLiteStorageManager() {
		setupDatabaseConnection();

		Main.plugin.getServer().getPluginManager().registerEvents(this, Main.plugin);
	}

	private void setupDatabaseConnection() {
		database = new BetterTeamsDatabase();
		database.setupConnectionFromConfiguration();
		database.setupTables();
		try {
			new LegacyStorageMigrator(database).migrateIfNeeded();
		} catch (LegacyStorageMigrationException e) {
			Main.plugin.getLogger().severe(e.getMessage());
			e.printStackTrace();
			Main.plugin.getServer().getPluginManager().disablePlugin(Main.plugin);
		}
	}

	@Override
	public void disable() {
		database.closeConnection();
	}

	private HashMap<String, UUID> claims;

	@Override
	public UUID getClaimingTeamUUID(Location location) {
		loadClaims();
		return claims.get(LocationSetComponent.getString(location));
	}

	private void loadClaims() {
		if (claims == null) {
			claims = new HashMap<>();
			try (PreparedStatement preparedStatement = database.select("*", TableName.CHESTCLAIMS)) {
				ResultSet results = preparedStatement.executeQuery();
				while (results.next()) {
					claims.put(results.getString(2), UUID.fromString(results.getString(1)));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	@Contract(pure = true, value = "null -> false")
	public boolean isTeam(@Nullable UUID uuid) {
		return uuid != null && database.hasResult(TableName.TEAM, "teamID LIKE '" + uuid.toString() + "'");
	}

	@Override
	@Contract(pure = true, value = "null -> false")
	public boolean isTeam(@Nullable String name) {
		return name != null && database.hasResult(TableName.TEAM, "UPPER(name) LIKE '" + name.toUpperCase() + "'");
	}

	@Override
	public boolean isInTeam(OfflinePlayer player) {
		return database.hasResult(TableName.PLAYERS, "playerUUID LIKE '" + player.getUniqueId() + "'");
	}

	@Override
	public UUID getTeamUUID(OfflinePlayer player) {
		return UUID.fromString(
				database.getResult("teamID", TableName.PLAYERS, "playerUUID LIKE '" + player.getUniqueId() + "'"));
	}

	@Override
	public UUID getTeamUUID(String name) {
		return UUID.fromString(
				database.getResult("teamID", TableName.TEAM, "UPPER(name) LIKE '" + name.toUpperCase() + "'"));
	}

	@Override
	public void loadTeams() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (isInTeam(player)) {
				loadTeam(getTeamUUID(player));
			}
		}
	}

	public void loadTeam(UUID uuid) {
		if (uuid == null || isLoaded(uuid)) {
			return;
		}

		Team team;

		try {
			team = new Team(uuid);
		} catch (IllegalArgumentException e) {
			return;
		}

		loadedTeams.put(uuid, team);

	}

	public void unloadTeam(UUID uuid) {
		Team team = Team.getTeam(uuid);
		if (team != null) {
			team.saveEchest();
			if (team.getScoreboardTeamOrNull() != null) {
				team.getScoreboardTeamOrNull().unregister();
			}
		}
		loadedTeams.remove(uuid);
	}

	@Override
	protected void registerNewTeam(Team team, Player player) {
		// no lookup tables so this method is not required
	}

	@Override
	protected void deleteTeamStorage(Team team) {
		database.deleteRecord(TableName.TEAM, "teamID LIKE '" + team.getID() + "'");
	}

	@Override
	public void teamNameChange(Team team, String newName) {
		database.updateRecordWhere(TableName.TEAM, "name", newName, "teamID LIKE '" + team.getID() + "'");
	}

	@Override
	public void playerJoinTeam(Team team, TeamPlayer player) {
		database.insertRecord(TableName.PLAYERS, "playerUUID, teamID, playerRank",
				"'" + player.getPlayer().getUniqueId() + "', '" + team.getID() + "', " + player.getRank().value);
	}

	@Override
	public void playerLeaveTeam(Team team, TeamPlayer player) {
		database.deleteRecord(TableName.PLAYERS, "playerUUID LIKE '" + player.getPlayer().getUniqueId() + "'");
	}

	@Override
	public TeamStorage createTeamStorage(Team team) {
		return new SQLiteTeamStorage(this, team);
	}

	@Override
	public TeamStorage createNewTeamStorage(Team team) {
		database.insertRecord(TableName.TEAM, "teamID, name", "'" + team.getID() + "', '" + team.getName() + "'");
		return new SQLiteTeamStorage(this, team);
	}

	@Override
	public String[] sortTeamsByScore() {

		PreparedStatement ps = database.selectOrder("name", TableName.TEAM, "SCORE DESC");

		return getTeamsFromResultSet(ps);
	}

	@Override
	public String[] sortTeamsByBalance() {
		PreparedStatement ps = database.selectOrder("name", TableName.TEAM, "money DESC");

		return getTeamsFromResultSet(ps);
	}

	@Override
	public String[] sortTeamsByMembers() {
		PreparedStatement ps = database.selectInnerJoinGroupByOrder("name, COUNT(" + TableName.PLAYERS + ".playerUUID)",
				TableName.TEAM, TableName.PLAYERS, TableName.TEAM + ".teamID = " + TableName.PLAYERS + ".teamID",
				"name", "COUNT(" + TableName.PLAYERS + ".playerUUID) DESC");

		return getTeamsFromResultSet(ps);
	}

	/**
	 * convert a result set, supplied by a prepared statement into a list of teams
	 * for sort methods
	 *
	 * @param ps the statement
	 * @return the ordered team list or an empty array in the event of an error
	 */
	private String[] getTeamsFromResultSet(PreparedStatement ps) {

		try {
			ResultSet results = ps.executeQuery();
			List<String> toReturn = new ArrayList<>();

			if (results.next()) {
				toReturn.add(results.getString("name"));
			} else {
				// called when no teams have been created
				return EMPTY_STRING_ARRAY;
			}

			while (results.next()) {
				toReturn.add(results.getString("name"));
			}
			ps.close();
			return toReturn.toArray(EMPTY_STRING_ARRAY);

		} catch (Exception e) {
			Main.plugin.getLogger().severe("Could not sort teams for results, report the following error:");
			e.printStackTrace();
			return EMPTY_STRING_ARRAY;
		}

	}

	@Override
	public void purgeTeamScore() {
		// reseting the loaded teams so the results are immediate
		for (Entry<UUID, Team> team : loadedTeams.entrySet()) {
			team.getValue().setScore(0);
		}

		database.updateRecord(TableName.TEAM, "score = 0");

	}

	@Override
	public void purgeTeamMoney() {
		// reseting the loaded teams so the results are immediate
		for (Entry<UUID, Team> team : loadedTeams.entrySet()) {
			team.getValue().setMoney(0);
		}

		database.updateRecord(TableName.TEAM, "money = 0");

	}

	private static final String HOLOPATH = "holos";

	@Override
	public List<String> getHoloDetails() {
		String serialized = database.getResult("dataValue", TableName.PLUGIN_DATA, "dataKey = '" + HOLOPATH + "'");
		if (serialized.isEmpty()) {
			return new ArrayList<>();
		}
		return new ArrayList<>(Arrays.asList(serialized.split("\\R")));
	}

	@Override
	public void setHoloDetails(List<String> details) {
		database.executeStatement("INSERT INTO %s (dataKey, dataValue) VALUES (?, ?) ON CONFLICT(dataKey) DO UPDATE SET dataValue = excluded.dataValue"
				.formatted(TableName.PLUGIN_DATA), HOLOPATH, String.join("\n", details));
	}

	@Override
	public void addChestClaim(Team team, Location loc) {
		loadClaims();
		claims.put(LocationSetComponent.getString(loc), team.getID());
		database.insertRecord(TableName.CHESTCLAIMS, "teamID, chestLoc",
				"'" + team.getID() + "', '" + LocationSetComponent.getString(loc) + "'");
	}

	@Override
	public void removeChestclaim(Location loc) {
		loadClaims();
		claims.remove(LocationSetComponent.getString(loc));
		database.deleteRecord(TableName.CHESTCLAIMS, "chestLoc LIKE '" + LocationSetComponent.getString(loc) + "'");
	}

	public int getTeamCount() {
		try (PreparedStatement preparedStatement = database.select("COUNT(*) AS teamCount", TableName.TEAM)) {
			ResultSet results = preparedStatement.executeQuery();
			if (results.next()) {
				return results.getInt("teamCount");
			}
		} catch (SQLException e) {
			Main.plugin.getLogger().warning("Could not count teams for metrics");
			e.printStackTrace();
		}
		return 0;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		if (!isInTeam(e.getPlayer())) {
			return;
		}

		loadTeam(getTeamUUID(e.getPlayer()));
	}

	@EventHandler
	public void onLeave(PlayerQuitEvent e) {
		if (!isInTeam(e.getPlayer())) {
			return;
		}
		UUID teamUUID = getTeamUUID(e.getPlayer());

		Team team = getTeam(teamUUID);

		if (team.getOnlineMembers().size() > 1) {
			team.getTeamPlayer(e.getPlayer()).setTeamChat(false);
			return;
		}

		unloadTeam(teamUUID);

	}

}
