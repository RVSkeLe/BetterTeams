package com.booksaw.betterTeams.team.storage.team;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.PlayerRank;
import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import com.booksaw.betterTeams.Utils;
import com.booksaw.betterTeams.Warp;
import com.booksaw.betterTeams.database.TableName;
import com.booksaw.betterTeams.team.TeamData;
import com.booksaw.betterTeams.team.storage.storageManager.SQLiteStorageManager;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SQLiteTeamStorage extends TeamStorage {

	private final SQLiteStorageManager storageManager;
	private final Map<String, Object> values = new HashMap<>();
	private final Map<UUID, StoredPlayer> players = new LinkedHashMap<>();
	private final Set<String> bans = new HashSet<>();
	private final Set<String> allies = new HashSet<>();
	private final Set<String> allyRequests = new HashSet<>();
	private final Set<String> warps = new HashSet<>();
	private final Set<String> claimedChests = new HashSet<>();
	private final Map<String, String> meta = new HashMap<>();

	private byte[] echest;
	private byte[] persistedEchest;
	private boolean dirty;

	public SQLiteTeamStorage(SQLiteStorageManager storageManager, Team team, boolean newTeam) {
		super(team);
		this.storageManager = storageManager;
		if (!newTeam) {
			loadFromDatabase();
		}
	}

	private String getCondition() {
		return "teamID = ?";
	}

	private void loadFromDatabase() {
		storageManager.executeOnDatabaseThread(this::loadFromDatabaseLocked);
	}

	private void loadFromDatabaseLocked() {
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.TEAM, getCondition(), team.getID().toString())) {
			ResultSet result = ps.executeQuery();
			if (!result.next()) {
				return;
			}

			putLoaded(StoredTeamValue.NAME.getReference(), result.getString("name"));
			putLoaded(StoredTeamValue.DESCRIPTION.getReference(), result.getString("description"));
			putLoaded(StoredTeamValue.OPEN.getReference(), result.getBoolean("open"));
			putLoaded(StoredTeamValue.SCORE.getReference(), result.getInt("score"));
			putLoaded(StoredTeamValue.MONEY.getReference(), result.getDouble("money"));
			putLoaded(StoredTeamValue.HOME.getReference(), result.getString("home"));
			putLoaded(StoredTeamValue.COLOR.getReference(), result.getString("color"));
			putLoaded(StoredTeamValue.LEVEL.getReference(), result.getInt("level"));
			putLoaded(StoredTeamValue.TAG.getReference(), result.getString("tag"));
			putLoaded(StoredTeamValue.PVP.getReference(), result.getBoolean("pvp"));
			putLoaded(StoredTeamValue.ANCHOR.getReference(), result.getBoolean("anchor"));
			echest = result.getBytes("echest");
			persistedEchest = echest == null ? null : echest.clone();
		} catch (SQLException e) {
			throw new IllegalStateException("Could not load team " + team.getID(), e);
		}

		loadPlayers();
		loadSimpleSet(TableName.BANS, "playerUUID", bans);
		loadAllies();
		loadAllyRequests();
		loadSimpleSet(TableName.WARPS, "warpInfo", warps);
		loadSimpleSet(TableName.CHESTCLAIMS, "chestLoc", claimedChests);
		loadMeta();
	}

	private void putLoaded(String key, Object value) {
		if (value != null) {
			values.put(key, value);
		}
	}

	private void loadPlayers() {
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.PLAYERS, getCondition(), team.getID().toString())) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				UUID playerUuid = UUID.fromString(result.getString("playerUUID"));
				players.put(playerUuid, new StoredPlayer(playerUuid, result.getInt("playerRank"),
						result.getString("title"), result.getBoolean("anchor")));
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Could not load players for team " + team.getID(), e);
		}
	}

	private void loadSimpleSet(TableName tableName, String column, Set<String> target) {
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere(column, tableName, getCondition(), team.getID().toString())) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				target.add(result.getString(column));
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Could not load " + tableName + " for team " + team.getID(), e);
		}
	}

	private void loadAllies() {
		String teamId = team.getID().toString();
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.ALLIES,
				"team1ID = ? OR team2ID = ?", teamId, teamId)) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				String team1 = result.getString("team1ID");
				allies.add(team1.equals(teamId) ? result.getString("team2ID") : team1);
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Could not load allies for team " + team.getID(), e);
		}
	}

	private void loadAllyRequests() {
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.ALLYREQUESTS,
				"receivingTeamID = ?", team.getID().toString())) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				allyRequests.add(result.getString("requestingTeamID"));
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Could not load ally requests for team " + team.getID(), e);
		}
	}

	private void loadMeta() {
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.TEAM_META,
				"teamID = ?", team.getID().toString())) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				meta.put(result.getString("metaKey"), result.getString("metaValue"));
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Could not load metadata for team " + team.getID(), e);
		}
	}

	@Override
	public synchronized void markDirty() {
		dirty = true;
		invalidateCache();
	}

	public synchronized void invalidateCache() {
		if (Main.placeholderAPI) {
			Main.plugin.getTeamPlaceholders().invalidateCache();
		}
	}

	@Override
	public synchronized String getString(String reference) {
		Object value = values.get(reference);
		return value == null ? null : value.toString();
	}

	@Override
	public synchronized boolean getBoolean(String reference) {
		Object value = values.get(reference);
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof Number number) {
			return number.intValue() != 0;
		}
		if ("1".equals(value)) {
			return true;
		}
		if ("0".equals(value)) {
			return false;
		}
		return Boolean.parseBoolean(String.valueOf(value));
	}

	@Override
	public synchronized double getDouble(String reference) {
		Object value = values.get(reference);
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		return value == null ? 0 : Double.parseDouble(value.toString());
	}

	@Override
	public synchronized int getInt(String reference) {
		Object value = values.get(reference);
		if (value instanceof Number number) {
			return number.intValue();
		}
		return value == null ? 0 : Integer.parseInt(value.toString());
	}

	@Override
	public synchronized List<TeamPlayer> getPlayerList() {
		List<TeamPlayer> toReturn = new ArrayList<>();
		for (StoredPlayer player : players.values()) {
			toReturn.add(player.toTeamPlayer());
		}
		return toReturn;
	}

	@Override
	public synchronized List<UUID> getAnchoredPlayerList() {
		List<UUID> toReturn = new ArrayList<>();
		for (StoredPlayer player : players.values()) {
			if (player.anchor()) {
				toReturn.add(player.uuid());
			}
		}
		return toReturn;
	}

	@Override
	public synchronized List<String> getBanList() {
		return new ArrayList<>(bans);
	}

	@Override
	public synchronized List<String> getAllyList() {
		return new ArrayList<>(allies);
	}

	@Override
	public synchronized List<String> getAllyRequestList() {
		return new ArrayList<>(allyRequests);
	}

	@Override
	public synchronized void getEchestContents(Inventory inventory) {
		if (echest == null || echest.length == 0) {
			return;
		}

		try {
			Utils.deserializeItemsIntoInventory(inventory, echest);
		} catch (RuntimeException e) {
			Utils.deserializeIntoInventory(inventory, new String(echest, StandardCharsets.UTF_8));
		}
	}

	@Override
	public synchronized List<String> getWarps() {
		return new ArrayList<>(warps);
	}

	@Override
	public synchronized List<String> getClaimedChests() {
		return new ArrayList<>(claimedChests);
	}

	@Override
	public synchronized Map<String, String> getRawMeta() {
		return new HashMap<>(meta);
	}

	public synchronized TeamSnapshot snapshot(boolean force) {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException("Team snapshots must be created on the Paper thread");
		}

		byte[] currentEchest = Utils.serializeInventoryAsBytes(team.getEchest());
		if (!force && !dirty && Arrays.equals(currentEchest, persistedEchest)) {
			return null;
		}

		List<StoredPlayer> storedPlayers = team.getMembersClone().stream()
				.map(StoredPlayer::fromTeamPlayer)
				.toList();
		List<String> storedBans = team.getBannedPlayersClone().stream()
				.map(UUID::toString)
				.toList();
		List<String> storedAllies = team.getAlliesClone().stream()
				.map(UUID::toString)
				.toList();
		List<String> storedAllyRequests = team.getAllyRequests().stream()
				.map(UUID::toString)
				.toList();
		List<String> storedWarps = team.getWarps().stream()
				.map(Warp::toString)
				.toList();
		List<String> storedClaims = team.getClaimsClone().stream()
				.map(TeamData::locationToString)
				.filter(value -> value != null)
				.toList();

		TeamSnapshot snapshot = new TeamSnapshot(
				team.getID().toString(),
				team.getName(),
				team.getDescription(),
				team.isOpen(),
				team.getScore(),
				team.getMoney(),
				TeamData.locationToString(team.getTeamHome()),
				String.valueOf(team.getColor().getChar()),
				currentEchest,
				team.getLevel(),
				team.getOriginalTag(),
				team.isPvp(),
				team.isUseTeamHomeAsAnchor(),
				storedPlayers,
				storedBans,
				storedAllies,
				storedAllyRequests,
				storedWarps,
				storedClaims,
				team.getMeta().getSerialized());
		dirty = false;
		persistedEchest = currentEchest.clone();
		return snapshot;
	}

	public synchronized boolean isDirty() {
		return dirty;
	}

	public synchronized void restoreDirty() {
		dirty = true;
	}

	public record TeamSnapshot(String teamId, String name, String description, boolean open, int score, double money,
	                           String home, String color, byte[] echest, int level, String tag, boolean pvp,
	                           boolean anchor, List<StoredPlayer> players, List<String> bans, List<String> allies,
	                           List<String> allyRequests, List<String> warps, List<String> claimedChests,
	                           Map<String, String> meta) {
		public TeamSnapshot {
			echest = echest == null ? null : echest.clone();
			players = List.copyOf(players);
			bans = List.copyOf(bans);
			allies = List.copyOf(allies);
			allyRequests = List.copyOf(allyRequests);
			warps = List.copyOf(warps);
			claimedChests = List.copyOf(claimedChests);
			meta = Collections.unmodifiableMap(new HashMap<>(meta));
		}

		@Override
		public byte[] echest() {
			return echest == null ? null : echest.clone();
		}
	}

	public record StoredPlayer(UUID uuid, int rank, String title, boolean anchor) {
		static StoredPlayer fromTeamPlayer(TeamPlayer player) {
			return new StoredPlayer(player.getPlayerUUID(), player.getRank().value, player.getTitle(), player.isAnchored());
		}

		TeamPlayer toTeamPlayer() {
			return new TeamPlayer(Bukkit.getOfflinePlayer(uuid), PlayerRank.getRank(rank), title, anchor);
		}
	}
}
