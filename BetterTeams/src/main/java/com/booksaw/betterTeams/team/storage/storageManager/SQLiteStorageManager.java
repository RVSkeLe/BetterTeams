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
import org.bukkit.Bukkit;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class SQLiteStorageManager extends TeamManager {

	private static final int DEFAULT_SAVE_INTERVAL_SECONDS = 300;
	private static final String HOLOPATH = "holos";

	@Getter
	private BetterTeamsDatabase database;

	private final Map<UUID, SQLiteTeamStorage> storageCache = new ConcurrentHashMap<>();
	private final Map<String, UUID> teamNameIndex = new ConcurrentHashMap<>();
	private final Map<String, UUID> claims = new ConcurrentHashMap<>();
	private final Set<UUID> deletedTeams = ConcurrentHashMap.newKeySet();
	private volatile Thread databaseThread;
	private final ScheduledExecutorService databaseExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "BetterTeams-SQLite-Database");
		thread.setDaemon(false);
		databaseThread = thread;
		return thread;
	});
	private final AtomicBoolean flushRequestQueued = new AtomicBoolean();
	private volatile boolean shuttingDown;
	private final AtomicBoolean flushQueued = new AtomicBoolean();
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
			throw new IllegalStateException("Could not migrate BetterTeams storage", e);
		}
	}

	@Override
	public void disable() {
		shuttingDown = true;
		QueuedFlush finalFlush = queueFlush(true);
		Future<?> shutdownBarrier = null;
		try {
			shutdownBarrier = databaseExecutor.submit(() -> {
			});
			databaseExecutor.shutdown();

			if (finalFlush != null) {
				try {
					finalFlush.future().get(30, TimeUnit.SECONDS);
					completeFlush(finalFlush.job());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					restoreFlush(finalFlush.job());
					Main.plugin.getLogger().severe("Interrupted while flushing cached team storage to SQLite");
				} catch (ExecutionException | java.util.concurrent.TimeoutException e) {
					restoreFlush(finalFlush.job());
					Main.plugin.getLogger().log(java.util.logging.Level.SEVERE,
							"Could not flush cached team storage to SQLite during shutdown", e);
				}
			}

			if (shutdownBarrier != null) {
				shutdownBarrier.get(30, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			Main.plugin.getLogger().severe("Interrupted while shutting down the SQLite database executor");
		} catch (ExecutionException | java.util.concurrent.TimeoutException e) {
			Main.plugin.getLogger().log(java.util.logging.Level.SEVERE,
					"Could not shut down the SQLite database executor cleanly", e);
		} finally {
			if (!databaseExecutor.isTerminated()) {
				databaseExecutor.shutdownNow();
			}
			try {
				databaseExecutor.awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			database.closeConnection();
		}
	}

	@Override
	public UUID getClaimingTeamUUID(Location location) {
		return claims.get(TeamData.locationToString(location));
	}

	@Override
	@Contract(pure = true, value = "null -> false")
	public boolean isTeamId(@Nullable UUID uuid) {
		return uuid != null && loadedTeams.containsKey(uuid) && !deletedTeams.contains(uuid);
	}

	@Override
	@Contract(pure = true, value = "null -> false")
	public boolean isTeamName(@Nullable String name) {
		return name != null && teamNameIndex.containsKey(normalizeName(name));
	}

	@Override
	public UUID getTeamIdByTeamName(String name) {
		return name == null ? null : teamNameIndex.get(normalizeName(name));
	}

	@Override
	public void loadTeams() {
		List<UUID> teamIds = executeOnDatabaseThread(() -> {
			List<UUID> ids = new ArrayList<>();
			try (PreparedStatement preparedStatement = database.select("teamID", TableName.TEAM)) {
				ResultSet results = preparedStatement.executeQuery();
				while (results.next()) {
					String teamId = results.getString("teamID");
					try {
						ids.add(UUID.fromString(teamId));
					} catch (IllegalArgumentException e) {
						Main.plugin.getLogger().warning("Skipping team with invalid UUID in SQLite: " + teamId);
					}
				}
			} catch (SQLException e) {
				throw new IllegalStateException("Could not load teams from SQLite", e);
			}
			return ids;
		});

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

		Team team = null;
		try {
			team = new Team(uuid);
			indexTeam(team);
			loadedTeams.put(uuid, team);
		} catch (RuntimeException e) {
			removeFailedTeamLoad(uuid, team);
			Main.plugin.getLogger().log(java.util.logging.Level.WARNING, "Skipping invalid team " + uuid, e);
		}
	}

	private void removeFailedTeamLoad(UUID uuid, Team team) {
		loadedTeams.remove(uuid);
		SQLiteTeamStorage storage = storageCache.remove(uuid);
		if (team == null) {
			return;
		}
		if (team.getName() != null) {
			teamNameIndex.remove(normalizeName(team.getName()), uuid);
		}
		unindexTeamPlayers(team);
		if (storage != null) {
			storage.getClaimedChests().forEach(location -> claims.remove(location, uuid));
		}
	}

	public void unloadTeam(UUID uuid) {
		Team team = Team.getTeamByTeamId(uuid);
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
		if (team.getName() != null) {
			teamNameIndex.remove(normalizeName(team.getName()), team.getID());
		}
		unindexTeamPlayers(team);
		if (storage != null) {
			storage.getClaimedChests().forEach(location -> claims.remove(location, team.getID()));
		}
	}

	@Override
	public void teamNameChange(Team team, String newName) {
		if (newName == null) {
			throw new IllegalArgumentException("Team name cannot be null");
		}
		UUID existingTeam = teamNameIndex.get(normalizeName(newName));
		if (existingTeam != null && !existingTeam.equals(team.getID())) {
			throw new IllegalArgumentException("A team with that name already exists");
		}
		teamNameIndex.remove(normalizeName(team.getName()), team.getID());
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
		return executeOnDatabaseThread(() -> {
			String serialized = database.getResult("dataValue", TableName.PLUGIN_DATA, "dataKey = ?", HOLOPATH);
			if (serialized == null || serialized.isEmpty()) {
				return new ArrayList<>();
			}
			return new ArrayList<>(Arrays.asList(serialized.split("\\R")));
		});
	}

	@Override
	public void setHoloDetails(List<String> details) {
		executeOnDatabaseThread(() -> database.executeStatement("INSERT INTO %s (dataKey, dataValue) VALUES (?, ?) ON CONFLICT(dataKey) DO UPDATE SET dataValue = excluded.dataValue"
				.formatted(TableName.PLUGIN_DATA), HOLOPATH, String.join("\n", details)));
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
			String normalizedName = normalizeName(team.getName());
			UUID existingTeam = teamNameIndex.get(normalizedName);
			if (existingTeam != null && !existingTeam.equals(team.getID())) {
				throw new IllegalArgumentException("Duplicate team name in SQLite: " + team.getName());
			}
		}
		for (TeamPlayer player : team.getMembersClone()) {
			UUID existingTeam = playerTeamIndex.get(player.getPlayerUUID());
			if (existingTeam != null && !existingTeam.equals(team.getID())) {
				throw new IllegalArgumentException("Player " + player.getPlayerUUID()
						+ " is stored in multiple teams");
			}
		}
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
		databaseExecutor.scheduleAtFixedRate(this::requestPeriodicFlush,
				saveIntervalSeconds, saveIntervalSeconds, TimeUnit.SECONDS);
	}

	private void requestPeriodicFlush() {
		if (shuttingDown || !flushRequestQueued.compareAndSet(false, true)) {
			return;
		}

		try {
			Bukkit.getScheduler().runTask(Main.plugin, () -> {
				flushRequestQueued.set(false);
				if (!shuttingDown) {
					flushDirtyTeams(false);
				}
			});
		} catch (RuntimeException e) {
			flushRequestQueued.set(false);
			Main.plugin.getLogger().log(java.util.logging.Level.WARNING,
					"Could not schedule periodic SQLite flush on the Paper thread", e);
		}
	}

	public void flushDirtyTeams(boolean force) {
		QueuedFlush queuedFlush = queueFlush(force);
		if (queuedFlush == null) {
			return;
		}

		databaseExecutor.execute(() -> {
			boolean successful = false;
			try {
				queuedFlush.future().get();
				successful = true;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				Main.plugin.getLogger().log(java.util.logging.Level.SEVERE,
						"Could not flush cached team storage to SQLite", e.getCause());
			}

			if (shuttingDown) {
				return;
			}

			boolean flushSucceeded = successful;
			Bukkit.getScheduler().runTask(Main.plugin, () -> {
				if (flushSucceeded) {
					completeFlush(queuedFlush.job());
				} else {
					restoreFlush(queuedFlush.job());
				}
			});
		});
	}

	private QueuedFlush queueFlush(boolean force) {
		if (!force && !flushQueued.compareAndSet(false, true)) {
			return null;
		}

		List<UUID> deletes = new ArrayList<>(deletedTeams);
		List<SQLiteTeamStorage> snapshottedStorages = new ArrayList<>();
		List<TeamSnapshot> snapshots = new ArrayList<>();
		for (SQLiteTeamStorage storage : storageCache.values()) {
			TeamSnapshot snapshot = storage.snapshot(force);
			if (snapshot != null) {
				snapshots.add(snapshot);
				snapshottedStorages.add(storage);
			}
		}

		if (deletes.isEmpty() && snapshots.isEmpty()) {
			flushQueued.set(false);
			return null;
		}

		FlushJob job = new FlushJob(deletes, snapshottedStorages, snapshots);
		try {
			flushQueued.set(true);
			Future<?> future = databaseExecutor.submit(() -> executeFlush(job));
			return new QueuedFlush(job, future);
		} catch (RejectedExecutionException e) {
			restoreFlush(job);
			throw e;
		}
	}

	private void executeFlush(FlushJob job) {
		try {
			database.executeStatementOrThrow("BEGIN IMMEDIATE TRANSACTION");
			for (UUID deletedTeam : job.deletes()) {
				database.executeStatementOrThrow("DELETE FROM %s WHERE teamID = ?".formatted(TableName.TEAM),
						deletedTeam.toString());
			}
			for (TeamSnapshot snapshot : job.snapshots()) {
				saveSnapshot(snapshot);
			}
			database.executeStatementOrThrow("COMMIT");
		} catch (SQLException | RuntimeException e) {
			try {
				database.executeStatementOrThrow("ROLLBACK");
			} catch (SQLException | RuntimeException rollbackException) {
				e.addSuppressed(rollbackException);
			}
			throw new IllegalStateException("Could not flush cached team storage to SQLite", e);
		}
	}

	private void completeFlush(FlushJob job) {
		job.deletes().forEach(deletedTeams::remove);
		flushQueued.set(false);
		if (!shuttingDown && (!deletedTeams.isEmpty() || storageCache.values().stream().anyMatch(SQLiteTeamStorage::isDirty))) {
			flushDirtyTeams(false);
		}
	}

	private void restoreFlush(FlushJob job) {
		deletedTeams.addAll(job.deletes());
		job.snapshottedStorages().forEach(SQLiteTeamStorage::restoreDirty);
		flushQueued.set(false);
	}

	public <T> T executeOnDatabaseThread(Supplier<T> action) {
		if (Thread.currentThread() == databaseThread) {
			return action.get();
		}

		try {
			return databaseExecutor.submit(action::get).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while executing SQLite operation", e);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new IllegalStateException("Could not execute SQLite operation", e.getCause());
		}
	}

	public void executeOnDatabaseThread(Runnable action) {
		executeOnDatabaseThread(() -> {
			action.run();
			return null;
		});
	}

	private record FlushJob(List<UUID> deletes, List<SQLiteTeamStorage> snapshottedStorages,
	                        List<TeamSnapshot> snapshots) {
	}

	private record QueuedFlush(FlushJob job, Future<?> future) {
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
