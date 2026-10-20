package com.booksaw.betterTeams.team;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import com.booksaw.betterTeams.customEvents.CreateTeamEvent;
import com.booksaw.betterTeams.customEvents.PurgeEvent;
import com.booksaw.betterTeams.customEvents.post.PostCreateTeamEvent;
import com.booksaw.betterTeams.customEvents.post.PostPurgeEvent;
import com.booksaw.betterTeams.events.ChestManagement;
import com.booksaw.betterTeams.player.PlayerIdentityCache;
import com.booksaw.betterTeams.team.storage.team.TeamStorage;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TeamManager {
	/**
	 * A list of all teams
	 */
	protected final Map<UUID, Team> loadedTeams;

	/**
	 * Reverse lookup from player UUID to team UUID.
	 */
	protected final Map<UUID, UUID> playerTeamIndex;

	/**
	 * If chat is being logged to the console
	 */
	@Getter
	private final boolean logChat;

	/**
	 * Used to create a new teamManager
	 */
	protected TeamManager() {
		logChat = Main.plugin.getConfig().getBoolean("logTeamChat");

		loadedTeams = new ConcurrentHashMap<>();
		playerTeamIndex = new ConcurrentHashMap<>();

	}

	/**
	 * Used to get an clone of the loaded team list. The team objects are not
	 * cloned, just the hashmap to avoid concurrent modification
	 *
	 * @return A clone of the team list
	 */
	@SuppressWarnings("unchecked")
	public Map<UUID, Team> getLoadedTeamListClone() {
		return new HashMap<>(loadedTeams);
	}

	/**
	 * Used to get the team with the provided team ID.
	 *
	 * @param teamId the ID of the team
	 * @return the team with that ID [null - the team does not exist]
	 */
	@Nullable
	@Contract(pure = true, value = "null -> null")
	public Team getTeamByTeamId(@Nullable UUID teamId) {
		return teamId == null ? null : loadedTeams.get(teamId);
	}

	/**
	 * Used to get a team by a team name first, then by a player name.
	 *
	 * @param identifier the display name of the team or a player within the team
	 * @return the team which matches the data[null - no team could be found]
	 */
	@Nullable
	@Contract(pure = true, value = "null -> null")
	public Team getTeamByTeamOrPlayerName(@Nullable String identifier) {
		if (identifier == null)
			return null;

		Team team = getTeamByTeamName(identifier);
		if (team != null) {
			return team;
		}

		return getTeamByPlayerName(identifier);
	}

	/**
	 * Used to find the team that a specified player is in.
	 *
	 * @param player the player which is in a team
	 * @return the team they are in [null - they are not in a team]
	 */
	@Nullable
	@Contract(pure = true, value = "null -> null")
	public Team getTeamByPlayer(@Nullable OfflinePlayer player) {
		if (player == null) {
			return null;
		}

		return getTeamByPlayerId(player.getUniqueId());
	}

	/**
	 * Used to find the team that a specified player UUID is in.
	 *
	 * @param playerId the player UUID to search for
	 * @return the team they are in [null - they are not in a team]
	 */
	@Nullable
	@Contract(pure = true, value = "null -> null")
	public Team getTeamByPlayerId(@Nullable UUID playerId) {
		return getTeamByTeamId(getTeamIdByPlayerId(playerId));
	}

	/**
	 * Used to find the team that a specified player name is in.
	 *
	 * @param playerName the player name to search for
	 * @return the team they are in [null - they are not in a team]
	 */
	@Nullable
	@Contract(pure = true, value = "null -> null")
	public Team getTeamByPlayerName(@Nullable String playerName) {
		return getTeamByPlayerId(PlayerIdentityCache.getUuid(playerName));
	}

	/**
	 * Used to get the team by its team name
	 *
	 * @param name The name of the team
	 * @return The team with that display name [null - no team with that name could
	 * be found]
	 */
	@Nullable
	@Contract(pure = true, value = "null -> null")
	public Team getTeamByTeamName(@Nullable String name) {
		if (!isTeamName(name)) {
			return null;
		}

		UUID uuid = getTeamIdByTeamName(name);

		if (uuid == null) {
			return null;
		}

		return getTeamByTeamId(uuid);
	}

	/**
	 * This method is used to create a new team with the specified name
	 * <p>
	 * Checks are not carried out to ensure that the name is available, so that
	 * should be done before this method is called
	 * </p>
	 *
	 * @param name  the name of the new team
	 * @param owner the owner of the new team (the player who ran /team create)
	 * @return The created team
	 */
	public Team createNewTeam(String name, Player owner) {

		UUID id = UUID.randomUUID();
		// ensuring the ID is unique
		while (getTeamByTeamId(id) != null) {
			id = UUID.randomUUID();
		}
		Team team = new Team(name, id, owner);
		loadedTeams.put(id, team);
		try {
			registerNewTeam(team, owner);

			CreateTeamEvent event = new CreateTeamEvent(team, owner);
			Bukkit.getPluginManager().callEvent(event);

			if (event.isCancelled()) {
				throw new IllegalArgumentException("Creating team was cancelled by another plugin");
			}
		} catch (RuntimeException e) {
			discardNewTeam(team);
			throw e;
		}

		if (Main.plugin.teamManagement != null && owner != null) {
			Main.plugin.teamManagement.displayBelowName(owner);
		}

		Bukkit.getPluginManager().callEvent(new PostCreateTeamEvent(team, owner));

		return team;
	}

	/**
	 * Used to get the team which has claimed the provided chest, will return null
	 * if that location is not claimed
	 *
	 * @param location the location of the chest - must already be normalised
	 * @return The team which has claimed that chest
	 */
	public Team getClaimingTeam(Location location) {
		UUID claimingTeam = getClaimingTeamUUID(location);

		if (claimingTeam == null) {
			return null;
		}

		if (!isTeamId(claimingTeam)) {
			return null;
		}

		return getTeamByTeamId(claimingTeam);
	}

	/**
	 * Used to get the UUID of the team which has claimed the provided chest, will
	 * return null if that location is not claimed
	 *
	 * @param location The location of the chest - must already be normalised
	 * @return the team which has claimed that chest
	 */
	public abstract UUID getClaimingTeamUUID(Location location);

	/**
	 * Used to get the claiming team of a chest, will check both parts of a double
	 * chest, it is assumed that the provided block is known to be a chest
	 *
	 * @param block The block being checked
	 * @return The team which has claimed that block
	 */
	public Team getClaimingTeam(Block block) {
		// player is opening a chest
		if (block.getType() != Material.CHEST) return null; // Just in case, should be a light check

		Location location1 = block.getLocation();
		Location location2 = ChestManagement.getOtherSide(block);

		if (location2 == null) {
			return null;
		}

		if (ChestManagement.isSingleChest(location1, location2)) {
			return getClaimingTeam(location1);
		}

		Team claimedBy = getClaimingTeam(location1);
		if (claimedBy != null) return claimedBy;

		return getClaimingTeam(location2);
	}

	/**
	 * Used to get the claiming location, will check both parts of a double chest,
	 * it is assumed that the provided block is known to be a chest
	 *
	 * @param block Part of the chest
	 * @return The location of the claim
	 */
	public Location getClaimingLocation(Block block) {
		// player is opening a chest
		if (block.getType() != Material.CHEST) return null;

		Location location1 = block.getLocation();
		Location location2 = ChestManagement.getOtherSide(block);

		if (location2 == null) {
			return null;
		}

		if (ChestManagement.isSingleChest(location1, location2)) {
			Team claimedBy = getClaimingTeam(location1);
			if (claimedBy != null) return location1;
		} else {
			Team claimedBy = getClaimingTeam(location1);
			if (claimedBy != null) return location1;

			claimedBy = getClaimingTeam(location2);
			if (claimedBy != null) return location2;
		}
		return null;
	}

	/**
	 * Used to reset all teams scores to 0
	 *
	 * @return If the teams were purged or not
	 */
	public boolean purgeTeams(boolean money, boolean score) {
		// calling custom bukkit event
		PurgeEvent event = new PurgeEvent();

		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return false;
		}

		if (score) {
			Main.plugin.getLogger().info("purging team score");
			purgeTeamScore();
		}
		if (money) {
			Main.plugin.getLogger().info("purging team score");
			purgeTeamMoney();
		}

		Bukkit.getPluginManager().callEvent(new PostPurgeEvent());
		return true;
	}

	/**
	 * Used to check if a team exists with that uuid
	 *
	 * @param uuid the UUID to check
	 * @return If a team exists with that uuid
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	@Contract(pure = true, value = "null -> false")
	public abstract boolean isTeamId(@Nullable UUID uuid);

	/**
	 * Used to check if a team exists with that name
	 *
	 * @param name the name to check
	 * @return If a team exists with that name
	 */
	@Contract(pure = true, value = "null -> false")
	public abstract boolean isTeamName(@Nullable String name);

	/**
	 * Used to check if the specified player is in a team
	 *
	 * @param player The player to check
	 * @return If they are in a team
	 */
	public boolean isPlayerInTeam(@Nullable OfflinePlayer player) {
		return player != null && isPlayerIdInTeam(player.getUniqueId());
	}

	/**
	 * Used to check if the specified player UUID is in a team
	 *
	 * @param playerId The player UUID to check
	 * @return If they are in a team
	 */
	public boolean isPlayerIdInTeam(@Nullable UUID playerId) {
		return playerId != null && playerTeamIndex.containsKey(playerId);
	}

	/**
	 * Used to get the uuid of the team that the specified player is in
	 *
	 * @param player the plyaer to check for
	 * @return The team uuid
	 */
	public UUID getTeamIdByPlayer(@Nullable OfflinePlayer player) {
		return player == null ? null : getTeamIdByPlayerId(player.getUniqueId());
	}

	/**
	 * Used to get the uuid of the team that the specified player UUID is in
	 *
	 * @param playerId the player UUID to check for
	 * @return The team uuid
	 */
	public UUID getTeamIdByPlayerId(@Nullable UUID playerId) {
		return playerId == null ? null : playerTeamIndex.get(playerId);
	}

	protected void indexPlayer(Team team, TeamPlayer player) {
		playerTeamIndex.put(player.getPlayerUUID(), team.getID());
		PlayerIdentityCache.cache(player.getPlayer());
	}

	protected void unindexPlayer(Team team, TeamPlayer player) {
		playerTeamIndex.remove(player.getPlayerUUID(), team.getID());
	}

	protected void indexTeamPlayers(Team team) {
		team.getMembersClone().forEach(player -> indexPlayer(team, player));
	}

	protected void unindexTeamPlayers(Team team) {
		team.getMembersClone().forEach(player -> unindexPlayer(team, player));
	}

	/**
	 * Used to get the team uuid from the team name
	 *
	 * @param name The name of the team
	 * @return The UUID of the specified team
	 */
	public abstract UUID getTeamIdByTeamName(String name);

	/**
	 * @deprecated Use {@link #getTeamByTeamId(UUID)} instead.
	 */
	@Deprecated
	@Nullable
	@Contract(pure = true, value = "null -> null")
	public Team getTeam(@Nullable UUID uuid) {
		return getTeamByTeamId(uuid);
	}

	/**
	 * @deprecated Use {@link #getTeamByTeamOrPlayerName(String)} when both values
	 * are accepted, or {@link #getTeamByTeamName(String)} /
	 * {@link #getTeamByPlayerName(String)} for explicit lookup.
	 */
	@Deprecated
	@Nullable
	@Contract(pure = true, value = "null -> null")
	public Team getTeam(@Nullable String name) {
		return getTeamByTeamOrPlayerName(name);
	}

	/**
	 * @deprecated Use {@link #getTeamByPlayer(OfflinePlayer)} instead.
	 */
	@Deprecated
	@Nullable
	@Contract(pure = true, value = "null -> null")
	public Team getTeam(@Nullable OfflinePlayer player) {
		return getTeamByPlayer(player);
	}

	/**
	 * @deprecated Use {@link #getTeamByTeamName(String)} instead.
	 */
	@Deprecated
	@Nullable
	public Team getTeamByName(@NotNull String name) {
		return getTeamByTeamName(name);
	}

	/**
	 * @deprecated Use {@link #isTeamId(UUID)} instead.
	 */
	@Deprecated
	@Contract(pure = true, value = "null -> false")
	public boolean isTeam(@Nullable UUID uuid) {
		return isTeamId(uuid);
	}

	/**
	 * @deprecated Use {@link #isTeamName(String)} instead.
	 */
	@Deprecated
	@Contract(pure = true, value = "null -> false")
	public boolean isTeam(@Nullable String name) {
		return isTeamName(name);
	}

	/**
	 * @deprecated Use {@link #isPlayerInTeam(OfflinePlayer)} instead.
	 */
	@Deprecated
	public boolean isInTeam(OfflinePlayer player) {
		return isPlayerInTeam(player);
	}

	/**
	 * @deprecated Use {@link #getTeamIdByPlayer(OfflinePlayer)} instead.
	 */
	@Deprecated
	public UUID getTeamUUID(OfflinePlayer player) {
		return getTeamIdByPlayer(player);
	}

	/**
	 * @deprecated Use {@link #getTeamIdByTeamName(String)} instead.
	 */
	@Deprecated
	public UUID getTeamUUID(String name) {
		return getTeamIdByTeamName(name);
	}

	/**
	 * Used to load the stored values into the storage manager
	 */
	public abstract void loadTeams();

	public boolean isLoaded(UUID teamUUID) {
		return loadedTeams.containsKey(teamUUID);
	}

	/**
	 * Called when a new team is registered, this can be used to register it in any
	 * full team trackers The team file will be fully prepared with the members
	 * within the team
	 *
	 * @param team   The new team
	 * @param player The player that created the team
	 */
	protected abstract void registerNewTeam(Team team, Player player);

	protected void discardNewTeam(Team team) {
	}

	/**
	 * Used to disband a team
	 *
	 * @param team The team that is being disbanded
	 */
	public void disbandTeam(Team team) {
		if (!loadedTeams.remove(team.getID(), team)) {
			return;
		}

		// if a team is being disbanded due to invalid team loading, the file should not
		// be deleted to preserve data
		if (team.getName() != null) {
			deleteTeamStorage(team);
		}
	}

	/**
	 * Used when a team is disbanded, can be used to remove it from any team
	 * trackers
	 *
	 * @param team The team that is being disbanded
	 */
	protected abstract void deleteTeamStorage(Team team);

	/**
	 * Called when a team changes its name as this will effect the getTeam(String
	 * teamName) method
	 *
	 * @param team    The new team
	 * @param newName The name the team has changed to
	 */
	public abstract void teamNameChange(Team team, String newName);

	/**
	 * Called when a player joins a team, this can be used to track the players
	 * location
	 *
	 * @param team   The team that the player has joined
	 * @param player The player that has joined the team
	 */
	public abstract void playerJoinTeam(Team team, TeamPlayer player);

	/**
	 * Called when a player leaves a team
	 *
	 * @param team   The team that the player has left
	 * @param player The team that the player has left
	 */
	public abstract void playerLeaveTeam(Team team, TeamPlayer player);

	/**
	 * Called when a team needs a storage manager to manage all information, this is
	 * called for preexisting teams
	 *
	 * @param team The team instance
	 * @return The created team storage
	 */
	public abstract TeamStorage createTeamStorage(Team team);

	/**
	 * Called when a new team is made
	 *
	 * @param team The team
	 * @return The created team storage
	 */
	public abstract TeamStorage createNewTeamStorage(Team team);

	/**
	 * This method is used to sort all the teams into an array ranking from highest
	 * score to lowest
	 *
	 * @return the array of teams in order of their rank
	 */
	public abstract String[] sortTeamsByScore();

	/**
	 * This method is used to sort all the team names into an array ranking from
	 * highest to lowest
	 *
	 * @return The sorted array
	 */
	public abstract String[] sortTeamsByBalance();

	/**
	 * Used to sort all members from largest to smallest by number of members
	 *
	 * @return the sorted array
	 */
	public abstract String[] sortTeamsByMembers();

	public int getTeamPositionByScore(Team team) {
		return getTeamPosition(sortTeamsByScore(), team);
	}

	public int getTeamPositionByBalance(Team team) {
		return getTeamPosition(sortTeamsByBalance(), team);
	}

	public int getTeamPositionByMembers(Team team) {
		return getTeamPosition(sortTeamsByMembers(), team);
	}

	private int getTeamPosition(String[] sortedTeams, Team team) {
		if (team == null || sortedTeams == null) {
			return 0;
		}
		for (int index = 0; index < sortedTeams.length; index++) {
			if (java.util.Objects.equals(sortedTeams[index], team.getName())) {
				return index + 1;
			}
		}
		return 0;
	}

	/**
	 * Used to reset the score of all teams
	 */
	public abstract void purgeTeamScore();

	/**
	 * Used to reset the balance of all teams
	 */
	public abstract void purgeTeamMoney();

	/**
	 * @return The stored hologram details
	 */
	public abstract List<String> getHoloDetails();

	/**
	 * Used to store and save the updated hologram details
	 *
	 * @param details the details to save
	 */
	public abstract void setHoloDetails(List<String> details);

	public abstract void addChestClaim(Team team, Location loc);

	public abstract void removeChestclaim(Location loc);

	public void removeChestclaim(Team team, Location loc) {
		removeChestclaim(loc);
	}

	public void invalidateLeaderboards() {
	}

	/**
	 * this can be overritten if any code needs to be run when onDisable is called
	 */
	public void disable() {
	}
}
