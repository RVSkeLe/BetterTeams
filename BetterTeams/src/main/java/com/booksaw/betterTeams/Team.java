package com.booksaw.betterTeams;

import com.booksaw.betterTeams.customEvents.DemotePlayerEvent;
import com.booksaw.betterTeams.customEvents.DisbandTeamEvent;
import com.booksaw.betterTeams.customEvents.PlayerJoinTeamEvent;
import com.booksaw.betterTeams.customEvents.PlayerLeaveTeamEvent;
import com.booksaw.betterTeams.customEvents.PromotePlayerEvent;
import com.booksaw.betterTeams.customEvents.RelationChangeTeamEvent;
import com.booksaw.betterTeams.customEvents.TeamColorChangeEvent;
import com.booksaw.betterTeams.customEvents.TeamDisallowedPvPEvent;
import com.booksaw.betterTeams.customEvents.TeamNameChangeEvent;
import com.booksaw.betterTeams.customEvents.TeamTagChangeEvent;
import com.booksaw.betterTeams.customEvents.post.PostDemotePlayerEvent;
import com.booksaw.betterTeams.customEvents.post.PostDisbandTeamEvent;
import com.booksaw.betterTeams.customEvents.post.PostPlayerJoinTeamEvent;
import com.booksaw.betterTeams.customEvents.post.PostPlayerLeaveTeamEvent;
import com.booksaw.betterTeams.customEvents.post.PostPromotePlayerEvent;
import com.booksaw.betterTeams.customEvents.post.PostRelationChangeTeamEvent;
import com.booksaw.betterTeams.customEvents.post.PostTeamColorChangeEvent;
import com.booksaw.betterTeams.customEvents.post.PostTeamNameChangeEvent;
import com.booksaw.betterTeams.customEvents.post.PostTeamTagChangeEvent;
import com.booksaw.betterTeams.exceptions.CancelledEventException;
import com.booksaw.betterTeams.message.Message;
import com.booksaw.betterTeams.message.MessageManager;
import com.booksaw.betterTeams.message.ReferencedFormatMessage;
import com.booksaw.betterTeams.team.TeamData;
import com.booksaw.betterTeams.team.TeamData.AnchorResult;
import com.booksaw.betterTeams.team.TeamManager;
import com.booksaw.betterTeams.team.controller.TeamMessageController;
import com.booksaw.betterTeams.team.level.LevelManager;
import com.booksaw.betterTeams.team.level.TeamLevel;
import com.booksaw.betterTeams.team.meta.TeamMeta;
import com.booksaw.betterTeams.team.storage.storageManager.SQLiteStorageManager;
import com.booksaw.betterTeams.team.storage.team.StoredTeamValue;
import com.booksaw.betterTeams.team.storage.team.TeamStorage;
import com.booksaw.betterTeams.text.LegacyTextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is used to manage a team and all of it's participants
 *
 * @author booksaw
 */
public class Team {

	private static TeamManager TEAMMANAGER;

	public static void setupTeamManager() {
		if (TEAMMANAGER != null) {
			throw new IllegalArgumentException("The team manager has already been setup");
		}

		TEAMMANAGER = new SQLiteStorageManager();

	}

	/**
	 * Used to disable betterteams so the singleton is removed
	 */
	public static void disable() {
		TEAMMANAGER.disable();
		TEAMMANAGER = null;
	}

	public static TeamManager getTeamManager() {
		return TEAMMANAGER;
	}

	@Contract(pure = true, value = "null -> null")
	public static @Nullable Team getTeamByTeamId(@Nullable UUID teamId) {
		return TEAMMANAGER.getTeamByTeamId(teamId);
	}

	@Contract(pure = true, value = "null -> null")
	public static @Nullable Team getTeamByTeamName(@Nullable String teamName) {
		return TEAMMANAGER.getTeamByTeamName(teamName);
	}

	@Contract(pure = true, value = "null -> null")
	public static @Nullable Team getTeamByPlayer(@Nullable OfflinePlayer player) {
		return TEAMMANAGER.getTeamByPlayer(player);
	}

	@Contract(pure = true, value = "null -> null")
	public static @Nullable Team getTeamByPlayerId(@Nullable UUID playerId) {
		return TEAMMANAGER.getTeamByPlayerId(playerId);
	}

	@Contract(pure = true, value = "null -> null")
	public static @Nullable Team getTeamByPlayerName(@Nullable String playerName) {
		return TEAMMANAGER.getTeamByPlayerName(playerName);
	}

	@Contract(pure = true, value = "null -> null")
	public static @Nullable Team getTeamByTeamOrPlayerName(@Nullable String identifier) {
		return TEAMMANAGER.getTeamByTeamOrPlayerName(identifier);
	}

	/**
	 * @deprecated Use {@link #getTeamByTeamId(UUID)} instead.
	 */
	@Deprecated
	@Contract(pure = true, value = "null -> null")
	public static @Nullable Team getTeam(@Nullable UUID uuid) {
		return getTeamByTeamId(uuid);
	}

	/**
	 * @deprecated Use {@link #getTeamByTeamOrPlayerName(String)} when both values
	 * are accepted, or {@link #getTeamByTeamName(String)} /
	 * {@link #getTeamByPlayerName(String)} for explicit lookup.
	 */
	@Deprecated
	@Contract(pure = true, value = "null -> null")
	public static @Nullable Team getTeam(@Nullable String name) {
		return getTeamByTeamOrPlayerName(name);
	}

	/**
	 * @deprecated Use {@link #getTeamByPlayer(OfflinePlayer)} instead.
	 */
	@Deprecated
	@Contract(pure = true, value = "null -> null")
	public static @Nullable Team getTeam(@Nullable OfflinePlayer player) {
		return getTeamByPlayer(player);
	}

	/**
	 * @deprecated Use {@link #getTeamByTeamName(String)} instead.
	 */
	@Deprecated
	public static @Nullable Team getTeamByName(String name) {
		return getTeamByTeamName(name);
	}

	public static Team getClaimingTeam(Block block) {
		return TEAMMANAGER.getClaimingTeam(block);
	}

	/**
	 * @param location the location of the chest - must already be normalised
	 * @return The team which has claimed that chest the provided chest, will return null if that
	 * location is not claimed
	 */
	public static Team getClaimingTeam(Location location) {
		return TEAMMANAGER.getClaimingTeam(location);
	}

	public static Location getClaimingLocation(Block block) {
		if (block.getType() != Material.CHEST) {
			return null;
		}
		return TEAMMANAGER.getClaimingLocation(block);
	}

	/**
	 * Used to get the config value checking if ally chests can be opened
	 *
	 * @return If ally chests can be opened
	 */
	public static boolean canOpenAllyChests() {
		return Main.plugin.getConfig().getBoolean("allowAllyChests");
	}

	/**
	 * Used to check if the provided team name is a valid name for a team
	 *
	 * @param name The name of the team
	 * @return If the team name is valid
	 */
	@Contract("null -> false")
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean isValidTeamName(@Nullable String name) {
		if (name == null) {
			return false;
		}

		for (String temp : Main.plugin.getConfig().getStringList("blacklist")) {
			if (temp.equalsIgnoreCase(name.toLowerCase())) {
				return false;
			}
		}

		String chars = Main.plugin.getConfig().getString("bannedChars");
		if (chars != null) {
			for (char temp : chars.toCharArray()) {
				if (name.contains(Character.toString(temp))) {
					return false;
				}
			}
		}

		if (!(name.equals(ChatColor.stripColor(name)))) {
			return false;
		}

		// stop players inputting color codes
		if (name.contains("&") || name.contains(":")) {
			return false;
		}

		String allowed = Main.plugin.getConfig().getString("allowedChars");

		if (allowed != null && !allowed.isEmpty()) {
			for (char temp : name.toCharArray()) {
				if (!allowed.contains(Character.toString(temp))) {
					return false;
				}
			}
		}

		return true;
	}

	@Getter
	private final TeamStorage storage;

	@Getter
	private final TeamData data = new TeamData();

	/**
	 * The ID of the team (this is a unique identifier of the team which will never change)
	 */
	private final UUID id;

	@Getter
	private final TeamMessageController teamMessageController = new TeamMessageController(this);

	private org.bukkit.scoreboard.Team team;

	/**
	 * this is used to load a team from the configuration file
	 *
	 * @param id the ID of the team to load
	 */
	public Team(UUID id) {
		this.id = id;

		storage = TEAMMANAGER.createTeamStorage(this);

		data.load(storage);

		if (data.getName() == null) {
			// removing it from the team list, the java GC will handle the reset
			getTeamManager().disbandTeam(this);

			throw new IllegalArgumentException(
					"The team that attempted loading is invalid, disbanding the team to avoid problems");
		}
	}

	/**
	 * Creates a new team with the provided name
	 * <p>
	 * This is a private method as the creation of a new team should be done by the
	 * Team.createNewTeam(name) method
	 * </p>
	 *
	 * @param name  The selected name for the team
	 * @param id    The UUID of the team
	 * @param owner The owner of the team (whoever initiated the creation of the team)
	 */
	public Team(String name, UUID id, Player owner) {
		this.id = id;

		if (name == null) {
			Main.plugin.getLogger()
					.warning("Provided team name was null, this should never occur. Team uuid = " + id);
			name = "invalidName";

			try {
				throw new IllegalArgumentException();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		storage = TEAMMANAGER.createNewTeamStorage(this);
		data.initializeNew(name, storage);
		if (owner != null) {
			addMember(new TeamPlayer(owner, PlayerRank.OWNER));
		}
		data.saveMembers(storage);
		data.saveAnchoredPlayers(storage);
		/*
		 * do not need to save config as createNewTeam saves the config after more
		 * settings modified
		 */
	}

	/**
	 * This is used to set the name of the team, it is important that you check that the name is
	 * unique before running this method
	 *
	 * @param name the new team nametag
	 */
	public void setName(String name, Player playerSource) {
		final String previousName = data.getName();

		TeamNameChangeEvent event = new TeamNameChangeEvent(this, name, playerSource);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			throw new IllegalArgumentException("Renaming was cancelled by another plugin");
		}
		name = event.getNewTeamName();

		TEAMMANAGER.teamNameChange(this, name);
		data.setName(name);
		getStorage().set(StoredTeamValue.NAME, name);

		registerTeamName();

		Bukkit.getPluginManager()
				.callEvent(new PostTeamNameChangeEvent(this, previousName, name, playerSource));
	}

	private void registerTeamName() {
		if (Main.plugin.teamManagement != null) {
			if (team != null) {
				for (TeamPlayer p : data.getMembersClone()) {
					if (p.getPlayer().isOnline()) {
						team.removeEntry(Objects.requireNonNull(p.getPlayer().getName()));
					}
				}
				team.unregister();
			}

			team = null;

			for (TeamPlayer p : data.getMembersClone()) {
				if (p.getPlayer().isOnline()) {
					Main.plugin.teamManagement.displayBelowName(
							Objects.requireNonNull(p.getPlayer().getPlayer()));
				}
			}
		}
	}

	public @NotNull String getOpenColor() {
		return LegacyTextUtils.colorToAdventure(data.getColor().asBungee());
	}

	public @NotNull String getCloseColor() {
		return LegacyTextUtils.colorToAdventure(data.getColor().asBungee(), true);
	}

	public @NotNull String getAdventureDisplayName() {
		return getAdventureDisplayName(false);
	}

	public @NotNull String getAdventureDisplayName(boolean checkConfig) {
		boolean doColor = !checkConfig || Main.plugin.getConfig().getBoolean("colorTeamName", true);
		return (doColor ? getOpenColor() : "")
				+ getName()
				+ (doColor ? getCloseColor() : "");
	}

	public @NotNull String getDisplayName(ChatColor resetTo) {
		return getDisplayName(resetTo, true);
	}

	/**
	 * Used to get the current name of the team
	 *
	 * @param resetTo the color to return to at the end of the string
	 * @return the name of the team
	 */
	public @NotNull String getDisplayName(ChatColor resetTo, boolean asAdventure) {
		if (resetTo == null) {
			return data.getName();
		} else if (asAdventure) {
			return getAdventureDisplayName(true) + LegacyTextUtils.colorToAdventure(resetTo.asBungee());
		} else {
			return getDisplayName(false) + resetTo;
		}
	}

	public @NotNull String getDisplayName() {
		return getDisplayName(true);
	}

	public @NotNull String getDisplayName(boolean asAdventure) {
		if (asAdventure) {
			return getAdventureDisplayName(true);
		} else {
			return
					(data.getColor() != null && Main.plugin.getConfig().getBoolean("colorTeamName", true) ? data.getColor() : "")
							+ data.getName();
		}
	}

	public String getAdventureTag() {
		return getAdventureTag(false);
	}

	public String getAdventureTag(boolean checkConfig) {
		if (data.getTag() == null || data.getTag().isEmpty()) {
			return getAdventureDisplayName(checkConfig);
		}
		boolean doColor = !checkConfig || Main.plugin.getConfig().getBoolean("colorTeamName", true);
		return (doColor ? getOpenColor() : "")
				+ data.getTag()
				+ (doColor ? getCloseColor() : "");
	}

	public String getTag() {
		return getTag(true);
	}

	public String getTag(boolean asAdventure) {
		if (asAdventure) {
			return getAdventureTag(true);
		} else {
			return data.getTag() == null || data.getTag().isEmpty() ? getDisplayName() :
					(data.getColor() != null && Main.plugin.getConfig().getBoolean("colorTeamName", true) ? data.getColor() : "")
							+ data.getTag();
		}
	}

	public String getTag(ChatColor returnTo) {
		return getTag(returnTo, true);
	}

	public String getTag(ChatColor returnTo, boolean asAdventure) {
		if (returnTo == null) {
			return getOriginalTag();
		} else if (data.getTag() == null || data.getTag().isEmpty()) {
			return getDisplayName(asAdventure);
		} else {
			return getTag(asAdventure) + (asAdventure ? LegacyTextUtils.colorToAdventure(
					returnTo.asBungee()) : returnTo);
		}
	}

	public String getOriginalTag() {
		return data.getTag() != null ? data.getTag() : "";
	}

	public void setTag(String tag) {
		final String oldTag = getTag();

		TeamTagChangeEvent event = new TeamTagChangeEvent(this, tag);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			throw new IllegalArgumentException("Changing tag was cancelled by another plugin");
		}
		tag = Optional.ofNullable(event.getNewTeamTag()).orElse("");

		data.setTag(tag);
		getStorage().set(StoredTeamValue.TAG, tag);

		registerTeamName();

		Bukkit.getPluginManager().callEvent(new PostTeamTagChangeEvent(this, oldTag, getTag(false)));
	}

	public void setOpen(boolean open) {
		data.setOpen(open);
		getStorage().set(StoredTeamValue.OPEN, open);
	}

	/**
	 * Used to change the team description
	 *
	 * @param description the new team description
	 */
	public void setDescription(String description) {
		data.setDescription(description);
		getStorage().set(StoredTeamValue.DESCRIPTION, description);
	}

	/**
	 * Used to change the team color
	 *
	 * @param color the new team color
	 */
	public void setColor(ChatColor color) {
		TeamColorChangeEvent event = new TeamColorChangeEvent(this, color);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			throw new IllegalArgumentException("Recoloring was cancelled by another plugin");
		}

		color = event.getNewTeamColor();

		final ChatColor oldColor = getColor();
		data.setColor(color);
		getStorage().set(StoredTeamValue.COLOR, color.getChar());

		registerTeamName();

		Bukkit.getPluginManager().callEvent(new PostTeamColorChangeEvent(this, oldColor, color));
	}

	/**
	 * Used to save the members list to the configuration file
	 */
	private void savePlayers() {
		data.saveMembers(getStorage());
	}

	/**
	 * Used to save anchored players in this team
	 */
	private void saveAnchoredPlayers() {
		data.saveAnchoredPlayers(getStorage());
	}

	/**
	 * Used to save the bans list to the configuration file
	 */
	private void saveBans() {
		data.saveBans(getStorage());
	}

	/**
	 * Used to remove the given player from the team, you must firstly be sure that the player is in
	 * this team (as it is not checked or caught in this method)
	 *
	 * @param p the player to remove from the team
	 * @return If the player was removed from the team
	 */
	public boolean removePlayer(OfflinePlayer p) {
		return removePlayer(getTeamPlayer(p));
	}

	/**
	 * Used to remove the given teamPlayer from the team, you must firstly be sure that the player is
	 * in this team (as it is not checked or caught in this method)
	 *
	 * @param p the player to remove from the team
	 * @return If the player was removed from the team
	 */
	public boolean removePlayer(TeamPlayer p) {
		try {
			PlayerLeaveTeamEvent event = new PlayerLeaveTeamEvent(this, p);
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				throw new CancelledEventException(event);
			}
		} catch (CancelledEventException e) {
			return false;
		}

		if (Main.plugin.teamManagement != null && p.getPlayer().isOnline()) {
			Main.plugin.teamManagement.remove(p.getPlayer().getPlayer());
		}
		getTeamManager().playerLeaveTeam(this, p);
		data.removeMember(p);
		savePlayers();
		if (p.isAnchored()) {
			data.unanchorPlayer(p);
			saveAnchoredPlayers();
		}
		Optional<Player> onlinePlayerOpt = p.getOnlinePlayer();
		if (onlinePlayerOpt.isPresent()) {
			Player onlinePlayer = onlinePlayerOpt.get();

			if (onlinePlayer.getOpenInventory().getTopInventory().getHolder()
					instanceof TeamData.EchestInventoryHolder) {

				Bukkit.getScheduler().runTaskLater(Main.plugin, () -> onlinePlayer.closeInventory(), 1L);
			}
		}

		if (team != null && p.getPlayer().isOnline()) {
			Main.plugin.teamManagement.remove(p.getPlayer().getPlayer());
		}

		Bukkit.getPluginManager().callEvent(new PostPlayerLeaveTeamEvent(this, p));
		return true;
	}

	public boolean isPlayerAnchored(OfflinePlayer p) {
		return isPlayerAnchored(getTeamPlayer(p));
	}

	/**
	 * Used to check if the given team player is anchored within this team
	 *
	 * @param p the team player
	 */
	public boolean isPlayerAnchored(TeamPlayer p) {
		return data.isPlayerAnchored(p);
	}

	public AnchorResult setPlayerAnchor(OfflinePlayer p, boolean anchor) {
		return setPlayerAnchor(getTeamPlayer(p), anchor);
	}

	public AnchorResult setPlayerAnchor(TeamPlayer p, boolean anchor) {
		return anchor ? anchorPlayer(p) : unanchorPlayer(p);
	}

	/**
	 * Used for anchoring this player.
	 *
	 * @param p the team player to anchor
	 * @return AnchorResult
	 */
	public AnchorResult anchorPlayer(TeamPlayer p) {
		AnchorResult result = data.anchorPlayer(p);
		if (result == AnchorResult.SUCCESS) {
			getStorage().setAnchor(p, true);
			saveAnchoredPlayers();
		}
		return result;
	}

	/**
	 * Used to unanchor this player.
	 *
	 * @param p the team player to unanchor
	 * @return AnchorResult
	 */
	public AnchorResult unanchorPlayer(TeamPlayer p) {
		AnchorResult result = data.unanchorPlayer(p);
		if (result == AnchorResult.SUCCESS) {
			getStorage().setAnchor(p, false);
			saveAnchoredPlayers();
		}
		return result;
	}

	/**
	 * Used to get the teamPlayer version of an included player
	 *
	 * @param player the player to search for
	 * @return the team player object for that player [null - player is not in the team]
	 */
	@Nullable
	public TeamPlayer getTeamPlayer(OfflinePlayer player) {
		if (player == null) {
			return null;
		}
		return data.getTeamPlayer(player);
	}

	/**
	 * Used to get all players which have the specified rank within the team
	 *
	 * @param rank the rank to search for
	 * @return a list of players which have that rank [empty list - no players have that rank]
	 */
	public List<TeamPlayer> getRank(PlayerRank rank) {
		return data.getRank(rank);
	}

	/**
	 * This command is used to disband a team, BE CAREFUL, this is irreversible
	 */
	public void disband() {
		disband(null);
	}

	/**
	 * This command is used to disband a team, BE CAREFUL, this is irreversible
	 *
	 * @param player The player responsible for disbandment [null - initiated by console]
	 */
	public void disband(Player player) {
		DisbandTeamEvent event = new DisbandTeamEvent(this, player);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			throw new IllegalArgumentException("Disbanding was cancelled by another plugin");
		}

		// I've got to store this here, because otherwise the team information is gone.
		final Set<UUID> alliesClone = data.getAlliesClone();
		final Set<TeamPlayer> membersClone = data.getMembersClone();

		alliesClone.forEach(uuid -> {
			Team team = Team.getTeamByTeamId(uuid);
			if (team != null) {
				team.becomeNeutral(this, false);
			}
		});

		for (TeamPlayer teamPlayer : membersClone) {
			getTeamManager().playerLeaveTeam(this, teamPlayer);
		}

		// removing it from the team list, the java GC will handle the reset
		getTeamManager().disbandTeam(this);

		if (Main.plugin.teamManagement != null) {
			for (TeamPlayer p : membersClone) {
				if (p.getPlayer().isOnline()) {
					Main.plugin.teamManagement.remove(p.getPlayer().getPlayer());
				}
			}

			if (team != null) {
				team.unregister();
			}
			team = null;
		}

		Bukkit.getPluginManager()
				.callEvent(new PostDisbandTeamEvent(this, player, alliesClone, membersClone));
	}

	/**
	 * Used to check if a player is invited to this team
	 *
	 * @param uuid the UUID of the player to check
	 * @return [true - the player is invited] [false - the player is not invited]
	 */
	public boolean isInvited(UUID uuid) {
		for (UUID temp : data.getInvitedPlayers()) {
			if (temp.compareTo(uuid) == 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Used to create an invitation for the included player to this team
	 *
	 * @param uniqueId the UUID of the player being invited
	 */
	public void invite(UUID uniqueId) {
		data.getInvitedPlayers().add(uniqueId);

		int invite = Main.plugin.getConfig().getInt("invite");

		if (invite <= 0) {
			return;
		}

		Bukkit.getScheduler().runTaskLater(Main.plugin, () -> {
			Player p = Bukkit.getPlayer(uniqueId);
			if (p == null || getTeamPlayer(p) != null) {
				return;
			}
			data.getInvitedPlayers().remove(uniqueId);
			MessageManager.sendMessage(p, "invite.expired", getName());
		}, invite * 20L);
	}

	/**
	 * This is used when a player is joining the team
	 *
	 * @param p the player who is joining the team
	 * @return true if the player joined the team, else false
	 */
	public boolean join(Player p) {
		try {
			addMember(new TeamPlayer(p, PlayerRank.DEFAULT));
		} catch (CancelledEventException e) {
			return false;
		}
		savePlayers();
		return true;

	}

	private void addMember(TeamPlayer teamPlayer) {
		PlayerJoinTeamEvent event = new PlayerJoinTeamEvent(this, teamPlayer);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			throw new CancelledEventException(event);
		}

		OfflinePlayer offlinePlayer = teamPlayer.getPlayer();
		data.getInvitedPlayers().remove(offlinePlayer.getUniqueId());
		getTeamManager().playerJoinTeam(this, teamPlayer);
		data.addMember(teamPlayer);

		Player onlinePlayer = offlinePlayer.getPlayer();
		if (offlinePlayer.isOnline() && onlinePlayer != null) {
			for (TeamPlayer player : data.getMembers()) {
				if (player.getPlayer().isOnline()) {
					MessageManager.sendMessage(player.getPlayer().getPlayer(), "join.notify", onlinePlayer.getDisplayName());
				}
			}

			if (Main.plugin.teamManagement != null) {
				Main.plugin.teamManagement.displayBelowName(onlinePlayer);
			}
		}

		if (offlinePlayer.isOnline() && onlinePlayer != null && onlinePlayer.hasPermission("betterteams.anchor.allowonjoin")) {
			setPlayerAnchor(teamPlayer, true);
		}

		Bukkit.getPluginManager().callEvent(new PostPlayerJoinTeamEvent(this, teamPlayer));
	}

	/**
	 * This method is used to promote a player to the next applicable rank, this method does not check
	 * the promotion is valid but instead only promotes the player, see PromoteCommand to see
	 * validation
	 *
	 * @param promotePlayer the player to be promoted
	 */
	public void promotePlayer(TeamPlayer promotePlayer) {
		PlayerRank newRank;
		if (promotePlayer.getRank() == PlayerRank.DEFAULT) {
			newRank = PlayerRank.ADMIN;
		} else {
			newRank = PlayerRank.OWNER;
		}
		promotePlayer(promotePlayer, newRank);
	}

	public void promotePlayerToOwner(TeamPlayer promotePlayer) {
		promotePlayer(promotePlayer, PlayerRank.OWNER);
	}

	private void promotePlayer(TeamPlayer promotePlayer, PlayerRank newRank) {
		PlayerRank oldRank = promotePlayer.getRank();

		final PromotePlayerEvent event = new PromotePlayerEvent(this, promotePlayer, oldRank, newRank);

		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			return;
		}

		promotePlayer.setRank(event.getNewRank());
		storage.promotePlayer(promotePlayer);
		savePlayers();

		Bukkit.getPluginManager()
				.callEvent(new PostPromotePlayerEvent(this, promotePlayer, oldRank, newRank));
	}

	/**
	 * This method is used to demote a player to the next applicable rank, this method does not check
	 * the demotion is valid but instead only promotes the player, see DemoteCommand to see
	 * validation
	 *
	 * @param demotePlayer the player to be demoted
	 */
	public void demotePlayer(TeamPlayer demotePlayer) {

		PlayerRank oldRank = demotePlayer.getRank();
		PlayerRank newRank;
		if (oldRank == PlayerRank.ADMIN) {
			newRank = PlayerRank.DEFAULT;
		} else {
			newRank = PlayerRank.ADMIN;
		}
		final DemotePlayerEvent event = new DemotePlayerEvent(this, demotePlayer, oldRank, newRank);

		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			return;
		}

		demotePlayer.setRank(event.getNewRank());
		storage.demotePlayer(demotePlayer);
		savePlayers();

		Bukkit.getPluginManager()
				.callEvent(new PostDemotePlayerEvent(this, demotePlayer, oldRank, newRank));
	}

	public void setTeamHome(Location teamHome) {
		data.setTeamHome(teamHome);
		getStorage().set(StoredTeamValue.HOME, TeamData.locationToString(teamHome));
	}

	public void deleteTeamHome() {
		data.setTeamHome(null);
		getStorage().set(StoredTeamValue.HOME, "");
		if (data.isUseTeamHomeAsAnchor()) {
			setAnchored(false);
		}
	}

	/**
	 * This method is used to add a player to the list of players which are banned from the team
	 *
	 * @param player the player to add to the list
	 */
	public void banPlayer(OfflinePlayer player) {
		data.addBan(player.getUniqueId());
		getStorage().addBan(player.getUniqueId());
		saveBans();
	}

	/**
	 * This method is used to remove a player from the list of players which are banned from the team
	 *
	 * @param player the player to remove from the list
	 */
	public void unbanPlayer(OfflinePlayer player) {
		data.removeBan(player.getUniqueId());
		getStorage().removeBan(player.getUniqueId());
		saveBans();
	}

	/**
	 * This method searches the ban list to check if the player is banned
	 *
	 * @param player the player to check
	 * @return [true - the player is banned] [false - the player isn't banned]
	 */
	public boolean isBanned(OfflinePlayer player) {
		return data.isBanned(player);
	}

	/**
	 * Used when a player sends a message to the team chat Use
	 *
	 * @param sender  the player which sent the message to the team chat
	 * @param message the message to send to the team chat
	 * @deprecated This method is going to be removed in simplifying the Team class. Use
	 * team.getTeamMessageController().sendTeamChatMessage() instead
	 */
	@Deprecated(forRemoval = true)
	public void sendMessage(TeamPlayer sender, String message) {
		teamMessageController.sendTeamChatMessage(sender, message);
	}

	/**
	 * Used to get the chat syntax and apply placeholders when possible
	 *
	 * @param sender - The team player who sent the command
	 * @deprecated use Team.getTeamMessageController().getTeamChatSyntax() instead
	 */
	@Deprecated
	public String getTeamChatSyntax(TeamPlayer sender) {
		return teamMessageController.getChatSyntax(sender,
				TeamMessageController.TeamMessageType.TEAM_CHAT_MESSAGE);
	}

	/**
	 * @param sender The team player who sent the command
	 * @return The syntax
	 * @deprecated Use team.getTeamMessageController().getChatSyntax() instead
	 */
	@Deprecated
	public String getAllyChatSyntax(TeamPlayer sender) {
		return teamMessageController.getChatSyntax(sender,
				TeamMessageController.TeamMessageType.ALLY_CHAT_MESSAGE);
	}

	/**
	 * Used to send a message to all the team's allies
	 *
	 * @param sender  the player who sent the message
	 * @param message the message that the player sent
	 * @deprecated Use team.getTeamMessageController().sendAllyChatMessage() instead
	 */
	@Deprecated
	public void sendAllyMessage(TeamPlayer sender, String message) {
		teamMessageController.sendAllyChatMessage(sender, message);
	}

	public int getScore() {
		return data.getScore();
	}

	public void setScore(int score) {
		data.setScore(score);
		getStorage().set(StoredTeamValue.SCORE, data.getScore());
	}

	public double getMoney() {
		return data.getMoney();
	}

	public String getMoneyShortFormatted() {
		return data.getMoneyShortFormatted();
	}

	public void setMoney(double money) {
		data.setMoney(money);
		getStorage().set(StoredTeamValue.MONEY, data.getMoney());
	}

	/**
	 * @return the rank of the team (-1 if the team has not been ranked)
	 */
	public int getTeamRank() {
		return data.getRank();
	}

	public void setTeamRank(int rank) {
		data.setRank(rank);
	}

	public void setTeamBalRank(int rank) {
		data.setBalRank(rank);
	}

	public int getTeamBalRank() {
		return data.getBalRank();
	}

	/**
	 * Used throughout all below name management (showing team name above player name)
	 *
	 * @param board the scoreboard to add the team to
	 * @return the team that has been created
	 */
	public org.bukkit.scoreboard.Team getScoreboardTeam(Scoreboard board) {
		if (team != null) {
			return team;
		}

		String name = data.getColor() + LegacyTextUtils.parseAllAdventure(
				MessageManager.getMessage("nametag.syntax", getTag(ChatColor.RESET, false)));

		int attempt = 0;
		do {
			try {
				String attemptStr = ((attempt > 0) ? attempt + "" : "");
				String teamName = getName();

				while (teamName.length() + attemptStr.length() > 16) {
					teamName = teamName.substring(0, teamName.length() - 1);
				}

				if (board.getTeam(teamName + attemptStr) != null) {
					team = null;
					attempt++;
					continue;
				}
				team = board.registerNewTeam(teamName + attemptStr);

			} catch (Exception e) {
				team = null;
				attempt++;
			}

		} while (team == null && attempt < 100);

		if (team == null) {
			Main.plugin.getLogger().warning(
					"An available team cannot be found, be prepared for a lot of errors. (this should never happen, and should always be reported to booksaw)");
			Main.plugin.getLogger().warning("This catch is merely here to stop the server crashing");
			return null;
		}

		Main.plugin.teamManagement.setupTeam(team, name);

		return team;

	}

	/**
	 * Used to return the scoreboard team, and not create a new one if it does not exist
	 *
	 * @return The scoreboard team (if already created)
	 */
	public org.bukkit.scoreboard.Team getScoreboardTeamOrNull() {
		return team;
	}

	public String getBalance() {
		return data.getMoneyFormatted();
	}

	public void setTitle(TeamPlayer player, String title) {
		player.setTitle(title);
		storage.setTitle(player);
		savePlayers();
	}

	/**
	 * call the event, let the user do stuff.
	 *
	 * @param otherTeam the other team
	 * @return true when the no status change should be effected
	 */
	private boolean callUserEvent(Team otherTeam, RelationType prevStatus, RelationType newStatus) {
		final RelationChangeTeamEvent event = new RelationChangeTeamEvent(this, otherTeam, prevStatus,
				newStatus);
		Bukkit.getPluginManager().callEvent(event);
		return event.isCancelled() || prevStatus == event.getNewRelation();
	}

	/**
	 * Used to add an ally for this team
	 *
	 * @param otherTeam     the UUID of the new ally
	 * @param sendPostEvent If you want the post event to be sent. This is useful if you are switching
	 *                      from one relation to another.
	 */
	public void addAlly(UUID otherTeam, boolean sendPostEvent) {
		if (isAlly(otherTeam)) {
			return;
		}

		RelationType prevRelation = RelationType.NEUTRAL;
		final Team other = Team.getTeamByTeamId(otherTeam);
		if (callUserEvent(other, prevRelation, RelationType.ALLY)) {
			return;
		}

		data.addAlly(otherTeam);
		getStorage().addAlly(otherTeam);
		saveAllies();

		List<String> channelsToUse = Main.plugin.getConfig().getStringList("onAllyMessageChannel");
		final String displayName = getTeamByTeamId(otherTeam).getDisplayName();
		if (channelsToUse.isEmpty() || channelsToUse.contains("CHAT")) {
			Message message = new ReferencedFormatMessage("ally.ally", displayName);
			data.broadcastMessage(message);
		}
		if (channelsToUse.isEmpty() || channelsToUse.contains("TITLE")) {
			Message message = new ReferencedFormatMessage("ally.ally_title", displayName);
			data.broadcastTitle(message);
		}

		if (sendPostEvent) {
			Bukkit.getPluginManager()
					.callEvent(new PostRelationChangeTeamEvent(this, other, prevRelation, RelationType.ALLY));
		}
	}

	/**
	 * Used to add an ally for this team
	 *
	 * @param ally          the UUID of the new ally
	 * @param sendPostEvent If you want the post event to be sent. This is useful if you are switching
	 *                      from one relation to another.
	 */
	public void addAlly(@Nullable Team ally, boolean sendPostEvent) {
		if (ally == null) {
			return;
		}

		addAlly(ally.getID(), sendPostEvent);
	}

	public void addAlly(@Nullable Team ally) {
		addAlly(ally, true);
	}

	public void addAlly(@Nullable UUID ally) {
		addAlly(ally, true);
	}

	/**
	 * Used to become neutral to a team
	 *
	 * @param otherTeam     the team to become neutral to
	 * @param sendPostEvent If you want the post event to be sent. This is useful if you are switching
	 *                      from one relation to another.
	 */
	public void becomeNeutral(UUID otherTeam, boolean sendPostEvent) {
		if (!isAlly(otherTeam)) {
			return;
		}

		final Team other = Team.getTeamByTeamId(otherTeam);

		RelationType prevRelation = RelationType.ALLY;
		if (callUserEvent(other, prevRelation, RelationType.NEUTRAL)) {
			return;
		}

		data.removeAlly(otherTeam);
		getStorage().removeAlly(otherTeam);
		saveAllies();

		List<String> channelsToUse;
		Message chatMessage, titleMessage;
		channelsToUse = Main.plugin.getConfig().getStringList("onAllyMessageChannel");
		chatMessage = new ReferencedFormatMessage("neutral.remove", other.getDisplayName());
		titleMessage = new ReferencedFormatMessage("neutral.remove_title", other.getDisplayName());

		if (channelsToUse.isEmpty() || channelsToUse.contains("CHAT")) {
			data.broadcastMessage(chatMessage);
		}
		if (channelsToUse.isEmpty() || channelsToUse.contains("TITLE")) {
			data.broadcastTitle(titleMessage);
		}

		if (sendPostEvent) {
			Bukkit.getPluginManager().callEvent(
					new PostRelationChangeTeamEvent(this, other, prevRelation, RelationType.NEUTRAL));
		}
	}

	/**
	 * Used to become neutral to a team
	 *
	 * @param otherTeam     the team to become neutral to
	 * @param sendPostEvent If you want the post event to be sent. This is useful if you are switching
	 *                      from one relation to another.
	 */
	public void becomeNeutral(Team otherTeam, boolean sendPostEvent) {
		if (otherTeam == null) {
			return;
		}
		becomeNeutral(otherTeam.getID(), sendPostEvent);
	}

	/**
	 * Used to check if a team is in alliance with this team
	 *
	 * @param team the team to check for allies
	 * @return if the team is an ally
	 */
	public boolean isAlly(UUID team) {
		return data.getAllies().contains(team);
	}

	/**
	 * Used to check if a team is in alliance with this team
	 *
	 * @param team the team to check for allies
	 * @return if the team is an ally
	 */
	public boolean isAlly(@Nullable Team team) {
		if (team == null) {
			return false;
		}

		return isAlly(team.getID());
	}

	/**
	 * Used to check if the provided team is a neutral to the other team
	 *
	 * @param team the team to check
	 * @return if the team is neutral
	 */
	public boolean isNeutral(UUID team) {
		return !data.getAllies().contains(team);
	}

	public boolean isNeutral(@Nullable Team team) {
		if (team == null) {
			return true;
		}

		return isNeutral(team.getID());
	}

	/**
	 * Used to add an ally request to this team
	 *
	 * @param team the team that has sent the request
	 */
	public void addAllyRequest(UUID team) {
		data.addAllyRequest(team);
		getStorage().addAllyRequest(team);
		saveAllyRequests();
	}

	/**
	 * Used to add an ally request to this team
	 *
	 * @param team the team that has sent the request
	 */
	public void addAllyRequest(@Nullable Team team) {
		if (team == null) {
			return;
		}

		addAllyRequest(team.getID());
	}

	/**
	 * Used to remove an ally request from this team
	 *
	 * @param team the team to remove the ally request for
	 */
	public void removeAllyRequest(UUID team) {
		data.removeAllyRequest(team);
		getStorage().removeAllyRequest(team);
		saveAllyRequests();
	}

	/**
	 * Used to remove an ally request from this team
	 *
	 * @param team the team to remove the ally request for
	 */
	public void removeAllyRequest(@Nullable Team team) {
		if (team == null) {
			return;
		}

		removeAllyRequest(team.getID());
	}

	/**
	 * Used to check if a team has sent an ally request for this team
	 *
	 * @param team the team to check for
	 * @return if they have sent an ally request
	 */
	public boolean hasRequested(UUID team) {
		return data.getAllyRequests().contains(team);
	}

	/**
	 * Used to check if a team has sent an ally request for this team
	 *
	 * @param team the team to check for
	 * @return if they have sent an ally request
	 */
	public boolean hasRequested(@Nullable Team team) {
		if (team == null) {
			return false;
		}

		return hasRequested(team.getID());
	}

	/**
	 * @return the set of all UUIDS of teams that have sent ally requests
	 */
	public Set<UUID> getAllyRequests() {
		return data.getAllyRequests();
	}

	/**
	 * Used to save the list of allies for this team
	 */
	private void saveAllies() {
		data.saveAllies(getStorage());
	}

	/**
	 * Used to save the list of requests for allies for this team
	 */
	private void saveAllyRequests() {
		data.saveAllyRequests(storage);
	}

	public UUID getID() {
		return id;
	}

	public String getName() {
		return data.getName();
	}

	public String getDescription() {
		return data.getDescription();
	}

	public boolean isOpen() {
		return data.isOpen();
	}

	public Location getTeamHome() {
		return data.getTeamHome();
	}

	public Set<TeamPlayer> getMembers() {
		return data.getMembers();
	}

	public Set<TeamPlayer> getMembersClone() {
		return data.getMembersClone();
	}

	public Set<UUID> getAllies() {
		return data.getAllies();
	}

	public Set<UUID> getAlliesClone() {
		return data.getAlliesClone();
	}

	public List<UUID> getInvitedPlayers() {
		return data.getInvitedPlayers();
	}

	public Set<Warp> getWarps() {
		return data.getWarps();
	}

	public TeamMeta getMeta() {
		return data.getMeta();
	}

	public ChatColor getColor() {
		return data.getColor();
	}

	public boolean isPvp() {
		return data.isPvp();
	}

	public int getLevel() {
		return data.getLevel();
	}

	/**
	 * Used to check if a member of this team can damage the specified player
	 *
	 * @param player the player to check for
	 * @param source the source of the damage
	 * @return if this team can damage that player
	 */
	public boolean canDamage(Player player, Player source) {
		Team team = Team.getTeamByPlayer(player);
		if (team == null) {
			return true;
		}
		return canDamage(team, source);
	}

	/**
	 * Used to check if this team can damage members of the specified team
	 *
	 * @param team   the team to test
	 * @param source The source of the damage
	 * @return if players of this team can damage members of the other team
	 */
	public boolean canDamage(Team team, Player source) {
		final boolean isProtected = team.isAlly(getID()) || team == this;

		boolean disallow;

		if (isProtected) {
			if (data.isPvp() && team.isPvp()) {
				disallow = false;
			} else if (Main.plugin.wgManagement != null) {
				disallow = !Main.plugin.wgManagement.canTeamPvp(source);
			} else {
				disallow = true;
			}

			if (disallow) {
				final TeamDisallowedPvPEvent event = new TeamDisallowedPvPEvent(team, source, this, true);

				Bukkit.getPluginManager().callEvent(event);
				if (event.isCancelled()) {
					return true;
				}
			}

			return !disallow;
		}

		return true;
	}

	/**
	 * Used to check if a member of this team can damage the specified player
	 *
	 * @param player the player to check for
	 * @return if this team can damage that player
	 */
	public boolean canDamage(Player player) {
		Team team = Team.getTeamByPlayer(player);
		if (team == null) {
			return true;
		}
		return canDamage(team);
	}

	/**
	 * Used to check if this team can damage members of the specified team
	 *
	 * @param team the team to test
	 * @return if players of this team can damage members of the other team
	 */
	public boolean canDamage(Team team) {
		if (team.isAlly(getID()) || team == this) {
			return data.isPvp() && team.isPvp();
		}
		return true;
	}

	public boolean hasMaxAllies() {
		int limit = Main.plugin.getConfig().getInt("allyLimit");
		if (limit == -1) {
			return false;
		}

		return data.getAllies().size() >= limit;
	}

	/**
	 * Used to save all warps that this team has set
	 */
	public void saveWarps() {
		data.saveWarps(storage);
	}

	/**
	 * Used to get a warp with the specified name
	 *
	 * @param name the name of the warp
	 * @return the warp with that name
	 */
	public Warp getWarp(String name) {
		return data.getWarp(name);
	}

	public void addWarp(Warp warp) {
		data.addWarp(warp);
		getStorage().addWarp(warp);
		saveWarps();
	}

	public void delWarp(String name) {
		Warp warp = getWarp(name);
		data.removeWarp(warp);
		getStorage().removeWarp(warp);
		saveWarps();
	}

	/**
	 * Used to get a list of all the online players that are on this team
	 *
	 * @return a list of online members for this team
	 */
	public List<Player> getOnlineMembers() {
		return data.getOnlinePlayers();
	}

	// CHEST CLAIM COMPONENT

	/**
	 * Used to add a chest claim to this team
	 *
	 * @param location The location of the chest claim (round to the nearest block)
	 */
	public void addClaim(Location location) {
		data.addClaim(location);
		Team.getTeamManager().addChestClaim(this, location);
		saveClaims();
	}

	/**
	 * Used to remove a chest claim from this team
	 *
	 * @param location The location of the chest claim (round to the nearest block)
	 */
	public void removeClaim(Location location) {
		data.removeClaim(location);
		Team.getTeamManager().removeChestclaim(location);
		saveClaims();
	}

	public void clearClaims() {
		for (Location location : data.getClaimsClone()) {
			Team.getTeamManager().removeChestclaim(location);
		}
		data.clearClaims();
		saveClaims();
	}

	public int getClaimCount() {
		return data.getClaimCount();
	}

	public boolean isClaimed(Location location) {
		return data.isClaimed(location);
	}

	private void saveClaims() {
		data.saveClaims(getStorage());
	}

	public void saveEchest() {
		data.saveEchest(getStorage());
	}

	public Inventory getEchest() {
		return data.getEchest();
	}

	public int getMaxWarps() {
		return getLevelObject().getMaxWarps();
	}

	public int getMaxChests() {
		return getLevelObject().getMaxChests();
	}

	public void setLevel(int level) {
		data.setLevel(level);
		getStorage().set(StoredTeamValue.LEVEL, level);

	}

	public void setPvp(boolean pvp) {
		data.setPvp(pvp);
		getStorage().set(StoredTeamValue.PVP, pvp);

	}

	/**
	 * Toggle anchor status for this team
	 *
	 * @return false if trying to anchor the team and its home is not set, true otherwise
	 */
	public boolean toggleAnchor() {
		return setAnchored(!data.isUseTeamHomeAsAnchor());
	}

	public boolean setAnchored(boolean anchor) {
		if (anchor && data.getTeamHome() == null) {
			return false;
		}
		data.setUseTeamHomeAsAnchor(anchor);
		getStorage().set(StoredTeamValue.ANCHOR, anchor);
		return true;
	}

	public boolean isAnchored() {
		return data.isUseTeamHomeAsAnchor();
	}

	public double getMaxMoney() {
		return getLevelObject().getMaxBalance();
	}

	public int getTeamLimit() {
		if (!Main.plugin.getConfig().getBoolean("permissionLevels") || Main.perms == null) {
			return getLevelObject().getTeamLimit();
		} else {

			int limit = 1;

			// looping through every owner to find the max team limit
			for (TeamPlayer player : getRank(PlayerRank.OWNER)) {

				OfflinePlayer op = player.getPlayer();

				for (int i = 100; i > 0 && i > limit; i--) {
					if (Main.perms.playerHas(Bukkit.getWorlds().get(0).getName(), op,
							"betterteams.limit." + i)) {
						limit = i;
					}
				}

			}
			return limit;
		}
	}

	public boolean isTeamFull() {
		return getMembers().size() >= getTeamLimit();
	}

	public int getMaxAdmins() {
		return getLevelObject().getMaxAdmins();
	}

	public int getMaxOwners() {
		return getLevelObject().getMaxOwners();
	}

	public boolean isMaxAdmins() {
		int max = getMaxAdmins();
		if (max == -1) {
			return false;
		}
		return max <= getRank(PlayerRank.ADMIN).size();
	}

	public boolean isMaxOwners() {
		int max = getMaxOwners();
		if (max == -1) {
			return false;
		}
		return max <= getRank(PlayerRank.OWNER).size();

	}

	public void setAndSaveMeta(String key, String value) {
		data.getMeta().set(key, value);
		data.saveMeta(getStorage());
	}

	public void removeAndSaveMeta(String key) {
		data.getMeta().remove(key);
		data.saveMeta(getStorage());
	}

	public TeamLevel getLevelObject() {
		return LevelManager.getLevel(data.getLevel());
	}
}
