package com.booksaw.betterTeams.team.storage.team;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import com.booksaw.betterTeams.Warp;
import com.booksaw.betterTeams.team.TeamData;
import com.booksaw.betterTeams.team.storage.storageManager.SQLiteStorageManager;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compatibility facade for the former per-team SQLite cache.
 *
 * @deprecated Team state is loaded by {@link SQLiteStorageManager} and owned by
 * {@link TeamData}.
 */
@Deprecated
public class SQLiteTeamStorage extends TeamStorage {

	private final SQLiteStorageManager storageManager;

	public SQLiteTeamStorage(SQLiteStorageManager storageManager, Team team, boolean newTeam) {
		super(team);
		this.storageManager = storageManager;
	}

	@Override
	public void load(TeamData data) {
		data.load(storageManager.loadTeamData(team.getID()));
	}

	@Override
	public void markDirty() {
		storageManager.markDirty(team);
		invalidateCache();
	}

	public void invalidateCache() {
		if (Main.placeholderAPI) {
			Main.plugin.getTeamPlaceholders().invalidateCache();
		}
	}

	@Override
	public String getString(String reference) {
		return switch (reference) {
			case "name" -> team.getName();
			case "description" -> team.getDescription();
			case "home" -> TeamData.locationToString(team.getTeamHome());
			case "color" -> String.valueOf(team.getColor().getChar());
			case "tag" -> team.getOriginalTag();
			default -> null;
		};
	}

	@Override
	public boolean getBoolean(String reference) {
		return switch (reference) {
			case "open" -> team.isOpen();
			case "pvp" -> team.isPvp();
			case "anchor" -> team.isUseTeamHomeAsAnchor();
			default -> false;
		};
	}

	@Override
	public double getDouble(String reference) {
		return "money".equals(reference) ? team.getMoney() : 0;
	}

	@Override
	public int getInt(String reference) {
		return switch (reference) {
			case "score" -> team.getScore();
			case "level" -> team.getLevel();
			default -> 0;
		};
	}

	@Override
	public List<TeamPlayer> getPlayerList() {
		return new ArrayList<>(team.getMembersClone());
	}

	@Override
	public List<UUID> getAnchoredPlayerList() {
		return team.getMembersClone().stream()
				.filter(TeamPlayer::isAnchored)
				.map(TeamPlayer::getPlayerUUID)
				.toList();
	}

	@Override
	public List<String> getBanList() {
		return team.getBannedPlayersClone().stream().map(UUID::toString).toList();
	}

	@Override
	public List<String> getAllyList() {
		return team.getAlliesClone().stream().map(UUID::toString).toList();
	}

	@Override
	public List<String> getAllyRequestList() {
		return team.getAllyRequests().stream().map(UUID::toString).toList();
	}

	@Override
	public void getEchestContents(Inventory inventory) {
		inventory.setContents(team.getEchest().getContents());
	}

	@Override
	public List<String> getWarps() {
		return team.getWarps().stream().map(Warp::toString).toList();
	}

	@Override
	public List<String> getClaimedChests() {
		return team.getClaimsClone().stream().map(TeamData::locationToString).toList();
	}

	@Override
	public Map<String, String> getRawMeta() {
		return new HashMap<>(team.getMeta().getSerialized());
	}
}
