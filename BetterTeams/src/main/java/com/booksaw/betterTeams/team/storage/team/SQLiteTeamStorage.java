package com.booksaw.betterTeams.team.storage.team;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.PlayerRank;
import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import com.booksaw.betterTeams.Utils;
import com.booksaw.betterTeams.Warp;
import com.booksaw.betterTeams.database.TableName;
import com.booksaw.betterTeams.team.meta.TeamMeta;
import com.booksaw.betterTeams.team.storage.storageManager.SQLiteStorageManager;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
	private boolean dirty;

	public SQLiteTeamStorage(SQLiteStorageManager storageManager, Team team, boolean newTeam) {
		super(team);
		this.storageManager = storageManager;
		if (!newTeam) {
			loadFromDatabase();
		}
	}

	private String getCondition() {
		return "teamID LIKE '" + team.getID() + "'";
	}

	private void loadFromDatabase() {
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.TEAM, getCondition())) {
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
		} catch (SQLException e) {
			e.printStackTrace();
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
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.PLAYERS, getCondition())) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				UUID playerUuid = UUID.fromString(result.getString("playerUUID"));
				players.put(playerUuid, new StoredPlayer(playerUuid, result.getInt("playerRank"),
						result.getString("title"), result.getBoolean("anchor")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void loadSimpleSet(TableName tableName, String column, Set<String> target) {
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere(column, tableName, getCondition())) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				target.add(result.getString(column));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void loadAllies() {
		String teamId = team.getID().toString();
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.ALLIES,
				"team1ID LIKE '" + teamId + "' OR team2ID LIKE '" + teamId + "'")) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				String team1 = result.getString("team1ID");
				allies.add(team1.equals(teamId) ? result.getString("team2ID") : team1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void loadAllyRequests() {
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.ALLYREQUESTS,
				"receivingTeamID LIKE '" + team.getID() + "'")) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				allyRequests.add(result.getString("requestingTeamID"));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void loadMeta() {
		try (PreparedStatement ps = storageManager.getDatabase().selectWhere("*", TableName.TEAM_META,
				"teamID = '" + team.getID() + "'")) {
			ResultSet result = ps.executeQuery();
			while (result.next()) {
				meta.put(result.getString("metaKey"), result.getString("metaValue"));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void markDirty() {
		dirty = true;
		invalidateCache();
	}

	public synchronized void invalidateCache() {
		if (Main.placeholderAPI) {
			Main.plugin.getTeamPlaceholders().invalidateCache();
		}
	}

	@Override
	protected synchronized void setValue(String location, TeamStorageType storageType, Object value) {
		if (value == null) {
			values.remove(location);
		} else {
			values.put(location, value);
		}
		markDirty();
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
	public synchronized void setEchestContents(Inventory inventory) {
		echest = Utils.serializeInventoryAsBytes(inventory);
		markDirty();
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
	public synchronized void addBan(UUID component) {
		bans.add(component.toString());
		markDirty();
	}

	@Override
	public synchronized void removeBan(UUID component) {
		bans.remove(component.toString());
		markDirty();
	}

	@Override
	public synchronized void addAlly(UUID ally) {
		allies.add(ally.toString());
		markDirty();
	}

	@Override
	public synchronized void removeAlly(UUID ally) {
		allies.remove(ally.toString());
		markDirty();
	}

	@Override
	public synchronized void addAllyRequest(UUID requesting) {
		allyRequests.add(requesting.toString());
		markDirty();
	}

	@Override
	public synchronized void removeAllyRequest(UUID requesting) {
		allyRequests.remove(requesting.toString());
		markDirty();
	}

	@Override
	public synchronized void addWarp(Warp component) {
		warps.add(component.toString());
		markDirty();
	}

	@Override
	public synchronized void removeWarp(Warp component) {
		warps.remove(component.toString());
		markDirty();
	}

	@Override
	public void promotePlayer(TeamPlayer promotePlayer) {
		updatePlayer(promotePlayer);
	}

	@Override
	public void demotePlayer(TeamPlayer demotePlayer) {
		updatePlayer(demotePlayer);
	}

	@Override
	public synchronized void setTitle(TeamPlayer player) {
		updatePlayer(player);
	}

	@Override
	public synchronized void setAnchor(TeamPlayer player, boolean anchor) {
		StoredPlayer existing = players.get(player.getPlayerUUID());
		String title = existing == null ? player.getTitle() : existing.title();
		int rank = existing == null ? player.getRank().value : existing.rank();
		players.put(player.getPlayerUUID(), new StoredPlayer(player.getPlayerUUID(), rank, title, anchor));
		markDirty();
	}

	public synchronized void updatePlayer(TeamPlayer player) {
		players.put(player.getPlayerUUID(), StoredPlayer.fromTeamPlayer(player));
		markDirty();
	}

	public synchronized void removePlayer(TeamPlayer player) {
		players.remove(player.getPlayerUUID());
		markDirty();
	}

	public synchronized void addClaim(String location) {
		claimedChests.add(location);
		markDirty();
	}

	public synchronized void removeClaim(String location) {
		claimedChests.remove(location);
		markDirty();
	}

	@Override
	public synchronized void setPlayerList(List<String> players) {
		this.players.clear();
		for (String player : players) {
			StoredPlayer storedPlayer = StoredPlayer.fromString(player);
			this.players.put(storedPlayer.uuid(), storedPlayer);
		}
		markDirty();
	}

	@Override
	public synchronized void setAnchoredPlayerList(List<String> players) {
		Set<UUID> anchoredPlayers = new HashSet<>();
		for (String player : players) {
			anchoredPlayers.add(UUID.fromString(player));
		}
		this.players.replaceAll((uuid, storedPlayer) -> storedPlayer.withAnchor(anchoredPlayers.contains(uuid)));
		markDirty();
	}

	@Override
	public synchronized void setBanList(List<String> players) {
		bans.clear();
		bans.addAll(players);
		markDirty();
	}

	@Override
	public synchronized void setAllyList(List<String> players) {
		allies.clear();
		allies.addAll(players);
		markDirty();
	}

	@Override
	public synchronized void setAllyRequestList(List<String> players) {
		allyRequests.clear();
		allyRequests.addAll(players);
		markDirty();
	}

	@Override
	public synchronized void setWarps(List<String> warps) {
		this.warps.clear();
		this.warps.addAll(warps);
		markDirty();
	}

	@Override
	public synchronized void setClaimedChests(List<String> chests) {
		claimedChests.clear();
		claimedChests.addAll(chests);
		markDirty();
	}

	@Override
	public synchronized Map<String, String> getRawMeta() {
		return new HashMap<>(meta);
	}

	@Override
	public synchronized void saveMeta(TeamMeta meta) {
		this.meta.clear();
		this.meta.putAll(meta.getSerialized());
		markDirty();
	}

	public synchronized TeamSnapshot snapshot(boolean force) {
		if (!force && !dirty) {
			return null;
		}

		dirty = false;
		return new TeamSnapshot(
				team.getID().toString(),
				stringValue(StoredTeamValue.NAME),
				stringValue(StoredTeamValue.DESCRIPTION),
				booleanValue(StoredTeamValue.OPEN),
				intValue(StoredTeamValue.SCORE),
				doubleValue(StoredTeamValue.MONEY),
				stringValue(StoredTeamValue.HOME),
				stringValue(StoredTeamValue.COLOR),
				echest == null ? null : echest.clone(),
				intValue(StoredTeamValue.LEVEL),
				stringValue(StoredTeamValue.TAG),
				booleanValue(StoredTeamValue.PVP),
				booleanValue(StoredTeamValue.ANCHOR),
				new ArrayList<>(players.values()),
				new ArrayList<>(bans),
				new ArrayList<>(allies),
				new ArrayList<>(allyRequests),
				new ArrayList<>(warps),
				new ArrayList<>(claimedChests),
				new HashMap<>(meta));
	}

	public synchronized void restoreDirty() {
		dirty = true;
	}

	private String stringValue(StoredTeamValue storedValue) {
		return getString(storedValue.getReference());
	}

	private boolean booleanValue(StoredTeamValue storedValue) {
		return getBoolean(storedValue.getReference());
	}

	private int intValue(StoredTeamValue storedValue) {
		return getInt(storedValue.getReference());
	}

	private double doubleValue(StoredTeamValue storedValue) {
		return getDouble(storedValue.getReference());
	}

	public record TeamSnapshot(String teamId, String name, String description, boolean open, int score, double money,
	                           String home, String color, byte[] echest, int level, String tag, boolean pvp,
	                           boolean anchor, List<StoredPlayer> players, List<String> bans, List<String> allies,
	                           List<String> allyRequests, List<String> warps, List<String> claimedChests,
	                           Map<String, String> meta) {
	}

	public record StoredPlayer(UUID uuid, int rank, String title, boolean anchor) {
		static StoredPlayer fromTeamPlayer(TeamPlayer player) {
			return new StoredPlayer(player.getPlayerUUID(), player.getRank().value, player.getTitle(), player.isAnchored());
		}

		static StoredPlayer fromString(String player) {
			String[] split = player.split(",", 3);
			UUID uuid = UUID.fromString(split[0]);
			PlayerRank rank = split.length > 1 ? PlayerRank.getRank(split[1]) : PlayerRank.DEFAULT;
			if (rank == null) {
				try {
					rank = PlayerRank.getRank(Integer.parseInt(split[1]));
				} catch (NumberFormatException e) {
					rank = PlayerRank.DEFAULT;
				}
			}
			String title = split.length > 2 ? split[2] : "";
			return new StoredPlayer(uuid, rank.value, title, false);
		}

		TeamPlayer toTeamPlayer() {
			return new TeamPlayer(Bukkit.getOfflinePlayer(uuid), PlayerRank.getRank(rank), title, anchor);
		}

		StoredPlayer withAnchor(boolean anchor) {
			return new StoredPlayer(uuid, rank, title, anchor);
		}
	}
}
