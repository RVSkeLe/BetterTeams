package com.booksaw.betterTeams.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerIdentityCache implements Listener {

	private static final Map<String, UUID> USERNAME_TO_UUID = new ConcurrentHashMap<>();
	private static final Map<UUID, String> UUID_TO_USERNAME = new ConcurrentHashMap<>();

	public static void cache(Player player) {
		if (player != null) {
			cache(player.getUniqueId(), player.getName());
		}
	}

	public static void cache(OfflinePlayer player) {
		if (player != null) {
			cache(player.getUniqueId(), player.getName());
		}
	}

	public static void cache(UUID uuid, @Nullable String username) {
		if (uuid == null || username == null || username.isBlank()) {
			return;
		}

		String normalizedName = normalize(username);
		String previousName = UUID_TO_USERNAME.put(uuid, username);
		if (previousName != null && !previousName.equalsIgnoreCase(username)) {
			USERNAME_TO_UUID.remove(normalize(previousName), uuid);
		}

		UUID previousUuid = USERNAME_TO_UUID.put(normalizedName, uuid);
		if (previousUuid != null && !previousUuid.equals(uuid)) {
			String previousUuidName = UUID_TO_USERNAME.get(previousUuid);
			if (previousUuidName != null && previousUuidName.equalsIgnoreCase(username)) {
				UUID_TO_USERNAME.remove(previousUuid);
			}
		}
	}

	public static @Nullable UUID getUuid(String username) {
		if (username == null || username.isBlank()) {
			return null;
		}

		Player onlinePlayer = Bukkit.getPlayerExact(username);
		if (onlinePlayer != null) {
			cache(onlinePlayer);
			return onlinePlayer.getUniqueId();
		}

		UUID uuid = USERNAME_TO_UUID.get(normalize(username));
		if (uuid != null) {
			return uuid;
		}

		@SuppressWarnings("deprecation")
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
		if (!offlinePlayer.hasPlayedBefore()) {
			return null;
		}

		String cachedName = UUID_TO_USERNAME.get(offlinePlayer.getUniqueId());
		if (cachedName != null && !cachedName.equalsIgnoreCase(username)) {
			return null;
		}

		cache(offlinePlayer.getUniqueId(), offlinePlayer.getName() == null ? username : offlinePlayer.getName());
		return offlinePlayer.getUniqueId();
	}

	public static @Nullable String getUsername(UUID uuid) {
		if (uuid == null) {
			return null;
		}

		Player onlinePlayer = Bukkit.getPlayer(uuid);
		if (onlinePlayer != null) {
			cache(onlinePlayer);
			return onlinePlayer.getName();
		}

		return UUID_TO_USERNAME.get(uuid);
	}

	public static @Nullable OfflinePlayer getOfflinePlayer(String username) {
		UUID uuid = getUuid(username);
		return uuid == null ? null : Bukkit.getOfflinePlayer(uuid);
	}

	public static void seedOnlinePlayers() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			cache(player);
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		cache(event.getPlayer());
	}

	private static String normalize(String username) {
		return username.toLowerCase(Locale.ROOT);
	}
}
