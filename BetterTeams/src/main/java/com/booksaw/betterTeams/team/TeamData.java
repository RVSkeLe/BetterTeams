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
import com.booksaw.betterTeams.util.ThreadGuard;
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
import java.util.concurrent.ConcurrentHashMap;
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
	private final Set<TeamPlayer> members = ConcurrentHashMap.newKeySet();
	private final Set<UUID> anchoredPlayers = new HashSet<>();
	private final Set<UUID> allies = new HashSet<>();
	private final Set<UUID> bannedPlayers = new HashSet<>();
	private final Set<UUID> allyRequests = new HashSet<>();
	private final Set<Location> claims = new HashSet<>();
	private final Set<Warp> warps = new HashSet<>();
	private final List<UUID> invitedPlayers = new ArrayList<>();
	private final TeamMeta meta = new TeamMeta();
	private Runnable mutationListener = () -> {
	};
	private Inventory echest;
	private volatile String name;
	private String description = "";
	private boolean open;
	private Location teamHome;
	private boolean pvp;
	private boolean useTeamHomeAsAnchor;
	private ChatColor color;
	private int rank = -1;
	private int balRank;
	private volatile int score;
	private volatile double money;
	private int level = 1;
	private String tag = "";

	public TeamData() {
		minScore = Main.plugin.getConfig().getInt("minScore");
		color = defaultColor();
		echest = Bukkit.createInventory(new EchestInventoryHolder(), 27, MessageManager.getMessage("echest.echest"));
	}

	public void load(TeamStorage storage) {
		ThreadGuard.requirePrimaryThread();
		name = storage.getString(StoredTeamValue.NAME);
		description = Optional.ofNullable(storage.getString(StoredTeamValue.DESCRIPTION)).orElse("");
		open = storage.getBoolean(StoredTeamValue.OPEN);
		pvp = storage.getBoolean(StoredTeamValue.PVP);
		useTeamHomeAsAnchor = storage.getBoolean(StoredTeamValue.ANCHOR);
		color = parseColor(storage.getString(StoredTeamValue.COLOR));
		score = Math.max(minScore, storage.getInt(StoredTeamValue.SCORE));
		double storedMoney = storage.getDouble(StoredTeamValue.MONEY);
		if (!Double.isFinite(storedMoney) || storedMoney < 0) {
			Main.plugin.getLogger().warning("Invalid balance stored for team; resetting it to zero");
			storedMoney = 0;
		}
		money = storedMoney;
		storage.getEchestContents(echest);
		members.clear();
		members.addAll(storage.getPlayerList());
		anchoredPlayers.clear();
		anchoredPlayers.addAll(storage.getAnchoredPlayerList());
		syncMemberAnchors();
		for (TeamPlayer member : members) {
			member.setMutationListener(mutationListener);
		}
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

	public void initializeNew(String name) {
		ThreadGuard.requirePrimaryThread();
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

	}

	@Deprecated
	public void initializeNew(String name, TeamStorage storage) {
		initializeNew(name);
		storage.markDirty();
	}

	@Deprecated
	public void saveMembers(TeamStorage storage) {
		storage.markDirty();
	}

	@Deprecated
	public void saveAnchoredPlayers(TeamStorage storage) {
		storage.markDirty();
	}

	@Deprecated
	public void saveBans(TeamStorage storage) {
		storage.markDirty();
	}

	@Deprecated
	public void saveAllies(TeamStorage storage) {
		storage.markDirty();
	}

	@Deprecated
	public void saveAllyRequests(TeamStorage storage) {
		storage.markDirty();
	}

	@Deprecated
	public void saveClaims(TeamStorage storage) {
		storage.markDirty();
	}

	@Deprecated
	public void saveWarps(TeamStorage storage) {
		storage.markDirty();
	}

	@Deprecated
	public void saveEchest(TeamStorage storage) {
		storage.markDirty();
	}

	@Deprecated
	public void saveMeta(TeamStorage storage) {
		storage.markDirty();
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
		return new HashSet<>(members);
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
		ThreadGuard.requirePrimaryThread();
		player.setMutationListener(mutationListener);
		members.add(player);
		changed();
	}

	public void removeMember(TeamPlayer player) {
		ThreadGuard.requirePrimaryThread();
		members.remove(player);
		player.setMutationListener(null);
		changed();
	}

	public AnchorResult anchorPlayer(TeamPlayer player) {
		ThreadGuard.requirePrimaryThread();
		if (!hasMember(player)) {
			return AnchorResult.NOT_IN_TEAM;
		}
		if (isPlayerAnchored(player)) {
			return AnchorResult.ALREADY_ANCHORED;
		}
		player.setAnchor(true);
		anchoredPlayers.add(player.getPlayerUUID());
		changed();
		return AnchorResult.SUCCESS;
	}

	public AnchorResult unanchorPlayer(TeamPlayer player) {
		ThreadGuard.requirePrimaryThread();
		if (!hasMember(player)) {
			return AnchorResult.NOT_IN_TEAM;
		}
		if (!isPlayerAnchored(player)) {
			return AnchorResult.NOT_ANCHORED;
		}
		player.setAnchor(false);
		anchoredPlayers.remove(player.getPlayerUUID());
		changed();
		return AnchorResult.SUCCESS;
	}

	public boolean isPlayerAnchored(TeamPlayer player) {
		return player != null && anchoredPlayers.contains(player.getPlayerUUID());
	}

	public Set<UUID> getAllies() {
		return new HashSet<>(allies);
	}

	public Set<UUID> getBannedPlayersClone() {
		return new HashSet<>(bannedPlayers);
	}

	public Set<UUID> getAlliesClone() {
		return new HashSet<>(allies);
	}

	public Set<UUID> getAllyRequests() {
		return new HashSet<>(allyRequests);
	}

	public void addAlly(UUID ally) {
		ThreadGuard.requirePrimaryThread();
		allies.add(ally);
		changed();
	}

	public void removeAlly(UUID ally) {
		ThreadGuard.requirePrimaryThread();
		allies.remove(ally);
		changed();
	}

	public void addAllyRequest(UUID team) {
		ThreadGuard.requirePrimaryThread();
		allyRequests.add(team);
		changed();
	}

	public void removeAllyRequest(UUID team) {
		ThreadGuard.requirePrimaryThread();
		allyRequests.remove(team);
		changed();
	}

	public void addBan(UUID player) {
		ThreadGuard.requirePrimaryThread();
		bannedPlayers.add(player);
		changed();
	}

	public void removeBan(UUID player) {
		ThreadGuard.requirePrimaryThread();
		bannedPlayers.remove(player);
		changed();
	}

	public boolean isBanned(OfflinePlayer player) {
		return player != null && bannedPlayers.contains(player.getUniqueId());
	}

	public void addClaim(Location location) {
		ThreadGuard.requirePrimaryThread();
		claims.add(location.clone());
		changed();
	}

	public void removeClaim(Location location) {
		ThreadGuard.requirePrimaryThread();
		for (Location claim : getClaimsClone()) {
			if (claim.equals(location)) {
				claims.remove(claim);
				changed();
				return;
			}
		}
	}

	public void clearClaims() {
		ThreadGuard.requirePrimaryThread();
		claims.clear();
		changed();
	}

	public Set<Location> getClaimsClone() {
		Set<Location> clonedClaims = new HashSet<>();
		for (Location claim : claims) {
			clonedClaims.add(claim.clone());
		}
		return clonedClaims;
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
		return new HashSet<>(warps);
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
		ThreadGuard.requirePrimaryThread();
		warps.add(warp);
		changed();
	}

	public void removeWarp(Warp warp) {
		ThreadGuard.requirePrimaryThread();
		warps.remove(warp);
		changed();
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
		ThreadGuard.requirePrimaryThread();
		this.name = name;
		changed();
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		ThreadGuard.requirePrimaryThread();
		this.description = description;
		changed();
	}

	public boolean isOpen() {
		return open;
	}

	public void setOpen(boolean open) {
		ThreadGuard.requirePrimaryThread();
		this.open = open;
		changed();
	}

	public Location getTeamHome() {
		return teamHome == null ? null : teamHome.clone();
	}

	public void setTeamHome(Location teamHome) {
		ThreadGuard.requirePrimaryThread();
		this.teamHome = teamHome == null ? null : teamHome.clone();
		changed();
	}

	public boolean isPvp() {
		return pvp;
	}

	public void setPvp(boolean pvp) {
		ThreadGuard.requirePrimaryThread();
		this.pvp = pvp;
		changed();
	}

	public boolean isUseTeamHomeAsAnchor() {
		return useTeamHomeAsAnchor;
	}

	public void setUseTeamHomeAsAnchor(boolean useTeamHomeAsAnchor) {
		ThreadGuard.requirePrimaryThread();
		this.useTeamHomeAsAnchor = useTeamHomeAsAnchor;
		changed();
	}

	public ChatColor getColor() {
		return color;
	}

	public void setColor(ChatColor color) {
		ThreadGuard.requirePrimaryThread();
		this.color = color;
		changed();
	}

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		ThreadGuard.requirePrimaryThread();
		this.rank = rank;
		changed();
	}

	public int getBalRank() {
		return balRank;
	}

	public void setBalRank(int balRank) {
		ThreadGuard.requirePrimaryThread();
		this.balRank = balRank;
		changed();
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		ThreadGuard.requirePrimaryThread();
		this.score = Math.max(minScore, score);
		changed();
	}

	public double getMoney() {
		return money;
	}

	public void setMoney(double money) {
		ThreadGuard.requirePrimaryThread();
		if (!Double.isFinite(money) || money < 0) {
			throw new IllegalArgumentException("Team balance must be finite and non-negative");
		}
		this.money = money;
		changed();
	}

	public Inventory getEchest() {
		ThreadGuard.requirePrimaryThread();
		return echest;
	}

	public void setEchest(Inventory echest) {
		ThreadGuard.requirePrimaryThread();
		this.echest = echest;
		changed();
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		ThreadGuard.requirePrimaryThread();
		this.level = level;
		changed();
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		ThreadGuard.requirePrimaryThread();
		this.tag = tag;
		changed();
	}

	public List<UUID> getInvitedPlayers() {
		return new ArrayList<>(invitedPlayers);
	}

	public boolean isInvited(UUID player) {
		return invitedPlayers.contains(player);
	}

	public void addInvite(UUID player) {
		ThreadGuard.requirePrimaryThread();
		invitedPlayers.add(player);
	}

	public void removeInvite(UUID player) {
		ThreadGuard.requirePrimaryThread();
		invitedPlayers.remove(player);
	}

	public TeamMeta getMeta() {
		return meta;
	}

	public void setMutationListener(Runnable mutationListener) {
		this.mutationListener = mutationListener == null ? () -> {
		} : mutationListener;
		meta.setMutationListener(this.mutationListener);
		for (TeamPlayer member : members) {
			member.setMutationListener(this.mutationListener);
		}
	}

	private void changed() {
		mutationListener.run();
	}
}
