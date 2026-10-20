package com.booksaw.betterTeams.team.storage.team;

import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import com.booksaw.betterTeams.team.TeamData;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads persistence data for a team and tracks whether its live state needs to
 * be flushed. The storage manager snapshots the live team state before writing
 * it to the database.
 *
 * @author booksaw
 */
public abstract class TeamStorage {

	protected final Team team;

	protected TeamStorage(Team team) {
		this.team = team;
	}

	/**
	 * Loads this team's data. SQLite storage overrides this to load one immutable
	 * database snapshot; the default keeps compatibility with older storage
	 * implementations.
	 */
	public void load(TeamData data) {
		data.load(this);
	}

	private void checkCorrectType(StoredTeamValue reference, TeamStorageType type) {
		if (reference.getStorageType() != type) {
			throw new IllegalArgumentException("Requested value is not of the valid type.");
		}
	}

	public String getString(StoredTeamValue reference) {
		checkCorrectType(reference, TeamStorageType.STRING);
		return getString(reference.getReference());
	}

	public abstract String getString(String reference);

	public boolean getBoolean(StoredTeamValue reference) {
		checkCorrectType(reference, TeamStorageType.BOOLEAN);
		return getBoolean(reference.getReference());
	}

	public abstract boolean getBoolean(String reference);

	public double getDouble(StoredTeamValue reference) {
		checkCorrectType(reference, TeamStorageType.DOUBLE);
		return getDouble(reference.getReference());
	}

	public abstract double getDouble(String reference);

	public int getInt(StoredTeamValue reference) {
		checkCorrectType(reference, TeamStorageType.INTEGER);
		return getInt(reference.getReference());
	}

	public abstract int getInt(String reference);

	public abstract void markDirty();

	public abstract List<TeamPlayer> getPlayerList();

	public abstract List<UUID> getAnchoredPlayerList();

	public abstract List<String> getBanList();

	public abstract List<String> getAllyList();

	public abstract List<String> getAllyRequestList();

	public abstract void getEchestContents(Inventory inventory);

	public abstract List<String> getWarps();

	public abstract List<String> getClaimedChests();

	public abstract Map<String, String> getRawMeta();

}
