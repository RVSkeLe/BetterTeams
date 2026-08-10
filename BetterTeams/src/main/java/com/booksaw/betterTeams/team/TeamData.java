package com.booksaw.betterTeams.team;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.PlayerRank;
import com.booksaw.betterTeams.TeamPlayer;
import com.booksaw.betterTeams.Warp;
import com.booksaw.betterTeams.message.Message;
import com.booksaw.betterTeams.message.MessageManager;
import com.booksaw.betterTeams.team.meta.TeamMeta;
import com.booksaw.betterTeams.team.storage.team.StoredTeamValue;
import com.booksaw.betterTeams.team.storage.team.TeamStorage;
import com.booksaw.betterTeams.util.MoneyUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeamData {

	public enum AnchorResult {
		SUCCESS,
		NOT_IN_TEAM,
		ALREADY_ANCHORED,
		NOT_ANCHORED
	}

	public static class EchestInventoryHolder implements InventoryHolder {
		@Override
		public @NotNull Inventory getInventory() {
			return null;
		}
	}

	private final int minScore;
	private final Set<TeamPlayer> members = new HashSet<>();
	private final Set<UUID> anchoredPlayers = new HashSet<>();
	private final Set<UUID> allies = new HashSet<>();
	private final Set<UUID> bannedPlayers = new HashSet<>();
	private final Set<UUID> allyRequests = new HashSet<>();
	private final Set<Location> claims = new HashSet<>();
	private final Set<Warp> warps = new HashSet<>();
	private final List<UUID> invitedPlayers = new ArrayList<>();
	private final TeamMeta meta = new TeamMeta();
	private Inventory echest;
	private String name;
	private String description = "";
	private boolean open;
	private Location teamHome;
	private boolean pvp;
	private boolean useTeamHomeAsAnchor;
	private ChatColor color;
	private int rank = -1;
	private int balRank;
	private int score;
	private double money;
	private int level = 1;
	private String tag = "";

	public TeamData() {
		minScore = Main.plugin.getConfig().getInt("minScore");
		color = defaultColor();
		echest = Bukkit.createInventory(new EchestInventoryHolder(), 27, MessageManager.getMessage("echest.echest"));
	}

	public void load(TeamStorage storage) {
		name = storage.getString(StoredTeamValue.NAME);
		description = Optional.ofNullable(storage.getString(StoredTeamValue.DESCRIPTION)).orElse("");
		open = storage.getBoolean(StoredTeamValue.OPEN);
		pvp = storage.getBoolean(StoredTeamValue.PVP);
		useTeamHomeAsAnchor = storage.getBoolean(StoredTeamValue.ANCHOR);
		color = parseColor(storage.getString(StoredTeamValue.COLOR));
		score = Math.max(minScore, storage.getInt(StoredTeamValue.SCORE));
		money = storage.getDouble(StoredTeamValue.MONEY);
		storage.getEchestContents(echest);
		members.clear();
		members.addAll(storage.getPlayerList());
		anchoredPlayers.clear();
		anchoredPlayers.addAll(storage.getAnchoredPlayerList());
		syncMemberAnchors();
		bannedPlayers.clear();
		bannedPlayers.addAll(parseUuids(storage.getBanList()));
		allies.clear();
		allies.addAll(parseUuids(storage.getAllyList()));
		allyRequests.clear();
		allyRequests.addAll(parseUuids(storage.getAllyRequestList()));
		warps.clear();
		for (String warp : storage.getWarps()) {
			warps.add(new Warp(warp.split(";")));
		}
		claims.clear();
		for (String claim : storage.getClaimedChests()) {
			try {
				claims.add(locationFromString(claim));
			} catch (IllegalArgumentException e) {
				Main.plugin.getLogger().severe("Invalid chest claim location stored for team: " + e.getMessage());
			}
		}
		meta.load(storage.getRawMeta());
		teamHome = null;
		String teamHomeStr = storage.getString(StoredTeamValue.HOME);
		if (teamHomeStr != null && !teamHomeStr.isEmpty()) {
			teamHome = locationFromString(teamHomeStr);
		}
		level = storage.getInt(StoredTeamValue.LEVEL);
		if (level < 1) {
			level = 1;
		}
		tag = Optional.ofNullable(storage.getString(StoredTeamValue.TAG)).orElse("");
	}

	public void initializeNew(String name, TeamStorage storage) {
		this.name = name;
		description = "";
		open = false;
		pvp = false;
		useTeamHomeAsAnchor = false;
		teamHome = null;
		rank = -1;
		color = defaultColor();
		level = 1;
		tag = "";

		storage.set(StoredTeamValue.NAME, name);
		storage.set(StoredTeamValue.DESCRIPTION, description);
		storage.set(StoredTeamValue.OPEN, open);
		storage.set(StoredTeamValue.PVP, pvp);
		storage.set(StoredTeamValue.ANCHOR, useTeamHomeAsAnchor);
		storage.set(StoredTeamValue.HOME, "");
		storage.set(StoredTeamValue.COLOR, color.getChar());
		saveClaims(storage);
		saveMembers(storage);
		saveAnchoredPlayers(storage);
		storage.set(StoredTeamValue.LEVEL, level);
		storage.set(StoredTeamValue.TAG, tag);
	}

	public void saveMembers(TeamStorage storage) {
		storage.setPlayerList(members.stream().map(TeamPlayer::toString).collect(Collectors.toList()));
	}

	public void saveAnchoredPlayers(TeamStorage storage) {
		storage.setAnchoredPlayerList(anchoredPlayers.stream().map(UUID::toString).collect(Collectors.toList()));
	}

	public void saveBans(TeamStorage storage) {
		storage.setBanList(bannedPlayers.stream().map(UUID::toString).collect(Collectors.toList()));
	}

	public void saveAllies(TeamStorage storage) {
		storage.setAllyList(allies.stream().map(UUID::toString).collect(Collectors.toList()));
	}

	public void saveAllyRequests(TeamStorage storage) {
		storage.setAllyRequestList(allyRequests.stream().map(UUID::toString).collect(Collectors.toList()));
	}

	public void saveClaims(TeamStorage storage) {
		storage.setClaimedChests(claims.stream().map(TeamData::locationToString).filter(value -> value != null)
				.collect(Collectors.toList()));
	}

	public void saveWarps(TeamStorage storage) {
		storage.setWarps(warps.stream().map(Warp::toString).collect(Collectors.toList()));
	}

	public void saveEchest(TeamStorage storage) {
		storage.setEchestContents(echest);
	}

	public void saveMeta(TeamStorage storage) {
		storage.saveMeta(meta);
	}

	public static Location locationFromString(String loc) {
		String[] split = loc.split(":");
		if (split.length < 6) {
			throw new IllegalArgumentException("Invalid location string: " + loc);
		}
		return new Location(Bukkit.getWorld(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]),
				Double.parseDouble(split[3]), Float.parseFloat(split[4]), Float.parseFloat(split[5]));
	}

	public static String locationToString(Location loc) {
		if (loc == null) {
			return null;
		}
		if (!loc.isWorldLoaded()) {
			Main.plugin.getLogger().warning("Location " + loc + " is not in a loaded world so it will not be stored");
			return null;
		}
		return loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ() + ":" + loc.getYaw()
				+ ":" + loc.getPitch();
	}

	public static Location normalise(Location loc) {
		return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
	}

	private ChatColor defaultColor() {
		String colorStr = Main.plugin.getConfig().getString("defaultColor", "6");
		return parseColor(colorStr);
	}

	private ChatColor parseColor(String colorStr) {
		if (colorStr == null || colorStr.isEmpty()) {
			colorStr = "6";
		}
		return Optional.ofNullable(ChatColor.getByChar(colorStr.charAt(0))).orElse(ChatColor.GOLD);
	}

	private List<UUID> parseUuids(Iterable<String> values) {
		List<UUID> uuids = new ArrayList<>();
		for (String value : values) {
			uuids.add(UUID.fromString(value));
		}
		return uuids;
	}

	private void syncMemberAnchors() {
		for (TeamPlayer member : members) {
			member.setAnchor(anchoredPlayers.contains(member.getPlayerUUID()));
		}
	}

	public Set<TeamPlayer> getMembers() {
		return members;
	}

	public Set<TeamPlayer> getMembersClone() {
		return new HashSet<>(members);
	}

	public boolean hasMember(OfflinePlayer player) {
		return getTeamPlayer(player) != null;
	}

	public boolean hasMember(TeamPlayer player) {
		return player != null && hasMember(player.getPlayer());
	}

	public @Nullable TeamPlayer getTeamPlayer(@NotNull OfflinePlayer player) {
		return members.stream()
				.filter(teamPlayer -> player.getUniqueId().equals(teamPlayer.getPlayerUUID()))
				.findFirst()
				.orElse(null);
	}

	public List<TeamPlayer> getRank(PlayerRank rank) {
		return getMembersClone().stream()
				.filter(player -> player.getRank() == rank)
				.collect(Collectors.toList());
	}

	public List<Player> getOnlinePlayers() {
		return getMembersClone().stream()
				.map(TeamPlayer::getPlayer)
				.filter(OfflinePlayer::isOnline)
				.map(OfflinePlayer::getPlayer)
				.collect(Collectors.toList());
	}

	public List<OfflinePlayer> getOfflinePlayers() {
		return getMembersClone().stream()
				.map(TeamPlayer::getPlayer)
				.filter(player -> !player.isOnline())
				.collect(Collectors.toList());
	}

	public void broadcastMessage(@NotNull Message message) {
		message.sendMessage(getOnlinePlayers());
	}

	public void broadcastTitle(@NotNull Message message) {
		message.sendTitle(getOnlinePlayers());
	}

	public String getOnlinePlayersString() {
		return getPlayersString(getOnlinePlayers());
	}

	public String getOfflinePlayersString() {
		return getPlayersString(getOfflinePlayers());
	}

	private String getPlayersString(@NotNull List<? extends OfflinePlayer> players) {
		return players.stream().map(OfflinePlayer::getName).collect(Collectors.joining(", "));
	}

	public void addMember(TeamPlayer player) {
		members.add(player);
	}

	public void removeMember(TeamPlayer player) {
		members.remove(player);
	}

	public AnchorResult anchorPlayer(TeamPlayer player) {
		if (!hasMember(player)) {
			return AnchorResult.NOT_IN_TEAM;
		}
		if (isPlayerAnchored(player)) {
			return AnchorResult.ALREADY_ANCHORED;
		}
		player.setAnchor(true);
		anchoredPlayers.add(player.getPlayerUUID());
		return AnchorResult.SUCCESS;
	}

	public AnchorResult unanchorPlayer(TeamPlayer player) {
		if (!hasMember(player)) {
			return AnchorResult.NOT_IN_TEAM;
		}
		if (!isPlayerAnchored(player)) {
			return AnchorResult.NOT_ANCHORED;
		}
		player.setAnchor(false);
		anchoredPlayers.remove(player.getPlayerUUID());
		return AnchorResult.SUCCESS;
	}

	public boolean isPlayerAnchored(TeamPlayer player) {
		return player != null && anchoredPlayers.contains(player.getPlayerUUID());
	}

	public Set<UUID> getAllies() {
		return allies;
	}

	public Set<UUID> getAlliesClone() {
		return new HashSet<>(allies);
	}

	public Set<UUID> getAllyRequests() {
		return allyRequests;
	}

	public void addAlly(UUID ally) {
		allies.add(ally);
	}

	public void removeAlly(UUID ally) {
		allies.remove(ally);
	}

	public void addAllyRequest(UUID team) {
		allyRequests.add(team);
	}

	public void removeAllyRequest(UUID team) {
		allyRequests.remove(team);
	}

	public void addBan(UUID player) {
		bannedPlayers.add(player);
	}

	public void removeBan(UUID player) {
		bannedPlayers.remove(player);
	}

	public boolean isBanned(OfflinePlayer player) {
		return player != null && bannedPlayers.contains(player.getUniqueId());
	}

	public void addClaim(Location location) {
		claims.add(location);
	}

	public void removeClaim(Location location) {
		for (Location claim : getClaimsClone()) {
			if (claim.equals(location)) {
				claims.remove(claim);
				return;
			}
		}
	}

	public void clearClaims() {
		claims.clear();
	}

	public Set<Location> getClaimsClone() {
		return new HashSet<>(claims);
	}

	public boolean isClaimed(Location location) {
		for (Location claim : getClaimsClone()) {
			if (claim.equals(location)) {
				return true;
			}
		}
		return false;
	}

	public int getClaimCount() {
		return claims.size();
	}

	public Set<Warp> getWarps() {
		return warps;
	}

	public Warp getWarp(String name) {
		for (Warp warp : new ArrayList<>(warps)) {
			if (warp.getName().equalsIgnoreCase(name)) {
				return warp;
			}
		}
		return null;
	}

	public void addWarp(Warp warp) {
		warps.add(warp);
	}

	public void removeWarp(Warp warp) {
		warps.remove(warp);
	}

	public String getMoneyFormatted() {
		return MoneyUtils.getFormattedDouble(money);
	}

	public String getMoneyShortFormatted() {
		return MoneyUtils.getFormattedShortDouble(money);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isOpen() {
		return open;
	}

	public void setOpen(boolean open) {
		this.open = open;
	}

	public Location getTeamHome() {
		return teamHome;
	}

	public void setTeamHome(Location teamHome) {
		this.teamHome = teamHome;
	}

	public boolean isPvp() {
		return pvp;
	}

	public void setPvp(boolean pvp) {
		this.pvp = pvp;
	}

	public boolean isUseTeamHomeAsAnchor() {
		return useTeamHomeAsAnchor;
	}

	public void setUseTeamHomeAsAnchor(boolean useTeamHomeAsAnchor) {
		this.useTeamHomeAsAnchor = useTeamHomeAsAnchor;
	}

	public ChatColor getColor() {
		return color;
	}

	public void setColor(ChatColor color) {
		this.color = color;
	}

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
	}

	public int getBalRank() {
		return balRank;
	}

	public void setBalRank(int balRank) {
		this.balRank = balRank;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = Math.max(minScore, score);
	}

	public double getMoney() {
		return money;
	}

	public void setMoney(double money) {
		this.money = money;
	}

	public Inventory getEchest() {
		return echest;
	}

	public void setEchest(Inventory echest) {
		this.echest = echest;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public List<UUID> getInvitedPlayers() {
		return invitedPlayers;
	}

	public TeamMeta getMeta() {
		return meta;
	}
}
