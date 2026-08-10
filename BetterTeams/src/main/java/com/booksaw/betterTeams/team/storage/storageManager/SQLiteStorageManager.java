package com.booksaw.betterTeams.team.storage.storageManager;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import com.booksaw.betterTeams.database.BetterTeamsDatabase;
import com.booksaw.betterTeams.database.TableName;
import com.booksaw.betterTeams.team.TeamData;
import com.booksaw.betterTeams.team.TeamManager;
import com.booksaw.betterTeams.team.storage.migration.LegacyStorageMigrationException;
import com.booksaw.betterTeams.team.storage.migration.LegacyStorageMigrator;
import com.booksaw.betterTeams.team.storage.team.SQLiteTeamStorage;
import com.booksaw.betterTeams.team.storage.team.SQLiteTeamStorage.StoredPlayer;
import com.booksaw.betterTeams.team.storage.team.SQLiteTeamStorage.TeamSnapshot;
import com.booksaw.betterTeams.team.storage.team.TeamStorage;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SQLiteStorageManager extends TeamManager {

	private static final int DEFAULT_SAVE_INTERVAL_SECONDS = 300;
	private static final String HOLOPATH = "holos";

	@Getter
	private BetterTeamsDatabase database;

	private final Map<UUID, SQLiteTeamStorage> storageCache = new ConcurrentHashMap<>();
	private final Map<String, UUID> teamNameIndex = new ConcurrentHashMap<>();
	private final Map<String, UUID> claims = new ConcurrentHashMap<>();
	private final Set<UUID> deletedTeams = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean flushing = new AtomicBoolean(false);
	private int saveIntervalSeconds;

	public SQLiteStorageManager() {
		setupDatabaseConnection();
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
		flushDirtyTeams(true);
		database.closeConnection();
	}

	@Override
	public UUID getClaimingTeamUUID(Location location) {
		return claims.get(TeamData.locationToString(location));
	}

	@Override
	@Contract(pure = true, value = "null -> false")
	public boolean isTeam(@Nullable UUID uuid) {
		return uuid != null && loadedTeams.containsKey(uuid) && !deletedTeams.contains(uuid);
	}

	@Override
	@Contract(pure = true, value = "null -> false")
	public boolean isTeam(@Nullable String name) {
		return name != null && teamNameIndex.containsKey(normalizeName(name));
	}

	@Override
	public UUID getTeamUUID(String name) {
		return name == null ? null : teamNameIndex.get(normalizeName(name));
	}

	@Override
	public void loadTeams() {
		List<UUID> teamIds = new ArrayList<>();
		try (PreparedStatement preparedStatement = database.select("teamID", TableName.TEAM)) {
			ResultSet results = preparedStatement.executeQuery();
			while (results.next()) {
				teamIds.add(UUID.fromString(results.getString("teamID")));
			}
		} catch (SQLException e) {
			Main.plugin.getLogger().severe("Could not load teams from SQLite");
			e.printStackTrace();
		}

		for (UUID teamId : teamIds) {
			loadTeam(teamId);
		}

		startPeriodicFlush();
		Main.plugin.getLogger().info("Loaded " + loadedTeams.size() + " teams from SQLite into memory");
	}

	public void loadTeam(UUID uuid) {
		if (uuid == null || isLoaded(uuid)) {
			return;
		}

		try {
			Team team = new Team(uuid);
			loadedTeams.put(uuid, team);
			indexTeam(team);
		} catch (IllegalArgumentException e) {
			Main.plugin.getLogger().warning("Skipping invalid team " + uuid);
		}
	}

	public void unloadTeam(UUID uuid) {
		Team team = Team.getTeam(uuid);
		if (team != null) {
			team.saveEchest();
			if (team.getScoreboardTeamOrNull() != null) {
				team.getScoreboardTeamOrNull().unregister();
			}
		}
	}

	@Override
	protected void registerNewTeam(Team team, Player player) {
		indexTeam(team);
	}

	@Override
	protected void deleteTeamStorage(Team team) {
		deletedTeams.add(team.getID());
		loadedTeams.remove(team.getID());
		SQLiteTeamStorage storage = storageCache.remove(team.getID());
		teamNameIndex.remove(normalizeName(team.getName()));
		unindexTeamPlayers(team);
		if (storage != null) {
			storage.getClaimedChests().forEach(claims::remove);
		}
	}

	@Override
	public void teamNameChange(Team team, String newName) {
		teamNameIndex.remove(normalizeName(team.getName()));
		teamNameIndex.put(normalizeName(newName), team.getID());
	}

	@Override
	public void playerJoinTeam(Team team, TeamPlayer player) {
		indexPlayer(team, player);
		getCachedStorage(team).updatePlayer(player);
	}

	@Override
	public void playerLeaveTeam(Team team, TeamPlayer player) {
		unindexPlayer(team, player);
		getCachedStorage(team).removePlayer(player);
	}

	@Override
	public TeamStorage createTeamStorage(Team team) {
		return storageCache.computeIfAbsent(team.getID(), ignored -> new SQLiteTeamStorage(this, team, false));
	}

	@Override
	public TeamStorage createNewTeamStorage(Team team) {
		SQLiteTeamStorage storage = new SQLiteTeamStorage(this, team, true);
		storageCache.put(team.getID(), storage);
		deletedTeams.remove(team.getID());
		return storage;
	}

	@Override
	public String[] sortTeamsByScore() {
		return loadedTeams.values().stream()
				.sorted(Comparator.comparingInt(Team::getScore).reversed())
				.map(Team::getName)
				.toArray(String[]::new);
	}

	@Override
	public String[] sortTeamsByBalance() {
		return loadedTeams.values().stream()
				.sorted(Comparator.comparingDouble(Team::getMoney).reversed())
				.map(Team::getName)
				.toArray(String[]::new);
	}

	@Override
	public String[] sortTeamsByMembers() {
		return loadedTeams.values().stream()
				.sorted(Comparator.comparingInt((Team team) -> team.getMembers().size()).reversed())
				.map(Team::getName)
				.toArray(String[]::new);
	}

	@Override
	public void purgeTeamScore() {
		for (Team team : loadedTeams.values()) {
			team.setScore(0);
		}
	}

	@Override
	public void purgeTeamMoney() {
		for (Team team : loadedTeams.values()) {
			team.setMoney(0);
		}
	}

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
		String location = TeamData.locationToString(loc);
		claims.put(location, team.getID());
		getCachedStorage(team).addClaim(location);
	}

	@Override
	public void removeChestclaim(Location loc) {
		String location = TeamData.locationToString(loc);
		UUID teamId = claims.remove(location);
		if (teamId == null) {
			return;
		}

		SQLiteTeamStorage storage = storageCache.get(teamId);
		if (storage != null) {
			storage.removeClaim(location);
		}
	}

	public int getTeamCount() {
		return loadedTeams.size();
	}

	private SQLiteTeamStorage getCachedStorage(Team team) {
		return storageCache.computeIfAbsent(team.getID(), ignored -> new SQLiteTeamStorage(this, team, false));
	}

	private void indexTeam(Team team) {
		if (team.getName() != null) {
			teamNameIndex.put(normalizeName(team.getName()), team.getID());
		}
		indexTeamPlayers(team);
		getCachedStorage(team).getClaimedChests().forEach(location -> claims.put(location, team.getID()));
	}

	private String normalizeName(String name) {
		return name.toUpperCase(Locale.ROOT);
	}

	private void startPeriodicFlush() {
		saveIntervalSeconds = Math.max(1, Main.plugin.getConfig().getInt(
				"database.saveIntervalSeconds", DEFAULT_SAVE_INTERVAL_SECONDS));
		long intervalTicks = saveIntervalSeconds * 20L;
		Main.plugin.getFoliaLib().getScheduler().runTimerAsync(() -> flushDirtyTeams(false), intervalTicks, intervalTicks);
	}

	public void flushDirtyTeams(boolean force) {
		if (!flushing.compareAndSet(false, true)) {
			return;
		}

		List<UUID> deletes = new ArrayList<>(deletedTeams);
		deletedTeams.removeAll(deletes);

		List<TeamSnapshot> snapshots = new ArrayList<>();
		List<SQLiteTeamStorage> snapshottedStorages = new ArrayList<>();
		for (SQLiteTeamStorage storage : storageCache.values()) {
			TeamSnapshot snapshot = storage.snapshot(force);
			if (snapshot != null) {
				snapshots.add(snapshot);
				snapshottedStorages.add(storage);
			}
		}

		if (deletes.isEmpty() && snapshots.isEmpty()) {
			flushing.set(false);
			return;
		}

		try {
			database.executeStatementOrThrow("BEGIN IMMEDIATE TRANSACTION");
			for (UUID deletedTeam : deletes) {
				database.executeStatementOrThrow("DELETE FROM %s WHERE teamID = ?".formatted(TableName.TEAM),
						deletedTeam.toString());
			}
			for (TeamSnapshot snapshot : snapshots) {
				saveSnapshot(snapshot);
			}
			database.executeStatementOrThrow("COMMIT");
		} catch (SQLException | RuntimeException e) {
			try {
				database.executeStatementOrThrow("ROLLBACK");
			} catch (SQLException rollbackException) {
				e.addSuppressed(rollbackException);
			}
			deletedTeams.addAll(deletes);
			snapshottedStorages.forEach(SQLiteTeamStorage::restoreDirty);
			Main.plugin.getLogger().severe("Could not flush cached team storage to SQLite");
			e.printStackTrace();
		} finally {
			flushing.set(false);
		}
	}

	private void saveSnapshot(TeamSnapshot snapshot) throws SQLException {
		database.executeStatementOrThrow("""
				INSERT INTO Team (teamID, name, description, open, score, money, home, color, echest, level, tag, pvp, anchor)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(teamID) DO UPDATE SET
				name = excluded.name,
				description = excluded.description,
				open = excluded.open,
				score = excluded.score,
				money = excluded.money,
				home = excluded.home,
				color = excluded.color,
				echest = excluded.echest,
				level = excluded.level,
				tag = excluded.tag,
				pvp = excluded.pvp,
				anchor = excluded.anchor
				""", snapshot.teamId(), snapshot.name(), snapshot.description(), snapshot.open(), snapshot.score(),
				snapshot.money(), snapshot.home(), snapshot.color(), snapshot.echest(), snapshot.level(),
				snapshot.tag(), snapshot.pvp(), snapshot.anchor());

		rewritePlayers(snapshot);
		rewriteSimpleTable(TableName.BANS, "playerUUID", snapshot.teamId(), snapshot.bans());
		rewriteAllies(snapshot);
		rewriteSimpleTable(TableName.ALLYREQUESTS, "requestingTeamID", snapshot.teamId(), snapshot.allyRequests(),
				"receivingTeamID");
		rewriteSimpleTable(TableName.WARPS, "warpInfo", snapshot.teamId(), snapshot.warps());
		rewriteSimpleTable(TableName.CHESTCLAIMS, "chestLoc", snapshot.teamId(), snapshot.claimedChests());
		rewriteMeta(snapshot);
	}

	private void rewritePlayers(TeamSnapshot snapshot) throws SQLException {
		database.executeStatementOrThrow("DELETE FROM %s WHERE teamID = ?".formatted(TableName.PLAYERS), snapshot.teamId());
		for (StoredPlayer player : snapshot.players()) {
			database.executeStatementOrThrow("INSERT OR IGNORE INTO %s (playerUUID, teamID, playerRank, title, anchor) VALUES (?, ?, ?, ?, ?)"
							.formatted(TableName.PLAYERS),
					player.uuid().toString(), snapshot.teamId(), player.rank(), player.title(), player.anchor());
		}
	}

	private void rewriteSimpleTable(TableName tableName, String valueColumn, String teamId, List<String> values)
			throws SQLException {
		rewriteSimpleTable(tableName, valueColumn, teamId, values, "teamID");
	}

	private void rewriteSimpleTable(TableName tableName, String valueColumn, String teamId, List<String> values,
	                                String teamColumn) throws SQLException {
		database.executeStatementOrThrow("DELETE FROM %s WHERE %s = ?".formatted(tableName, teamColumn), teamId);
		for (String value : values) {
			database.executeStatementOrThrow("INSERT OR IGNORE INTO %s (%s, %s) VALUES (?, ?)"
							.formatted(tableName, teamColumn, valueColumn),
					teamId, value);
		}
	}

	private void rewriteAllies(TeamSnapshot snapshot) throws SQLException {
		database.executeStatementOrThrow("DELETE FROM %s WHERE team1ID = ? OR team2ID = ?".formatted(TableName.ALLIES),
				snapshot.teamId(), snapshot.teamId());
		for (String ally : snapshot.allies()) {
			String team1 = snapshot.teamId().compareTo(ally) <= 0 ? snapshot.teamId() : ally;
			String team2 = snapshot.teamId().compareTo(ally) <= 0 ? ally : snapshot.teamId();
			database.executeStatementOrThrow("INSERT OR IGNORE INTO %s (team1ID, team2ID) VALUES (?, ?)"
					.formatted(TableName.ALLIES), team1, team2);
		}
	}

	private void rewriteMeta(TeamSnapshot snapshot) throws SQLException {
		database.executeStatementOrThrow("DELETE FROM %s WHERE teamID = ?".formatted(TableName.TEAM_META), snapshot.teamId());
		for (Map.Entry<String, String> entry : snapshot.meta().entrySet()) {
			database.executeStatementOrThrow("INSERT OR IGNORE INTO %s (teamID, metaKey, metaValue) VALUES (?, ?, ?)"
							.formatted(TableName.TEAM_META),
					snapshot.teamId(), entry.getKey(), entry.getValue());
		}
	}
}
