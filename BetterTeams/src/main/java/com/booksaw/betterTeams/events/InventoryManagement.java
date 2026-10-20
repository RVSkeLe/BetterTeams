package com.booksaw.betterTeams.events;

import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.team.TeamData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InventoryManagement implements Listener {

	private static final Map<UUID, Team> viewers = new HashMap<>();

	public static boolean isViewing(Player player) {
		return viewers.containsKey(player.getUniqueId());
	}

	public static void open(Player player, Team team) {
		Team previous = viewers.remove(player.getUniqueId());
		if (previous != null) {
			previous.markDirty();
		}

		Inventory inventory = team.getEchest();
		if (inventory == null || inventory.getSize() == 0) {
			throw new IllegalStateException("Cannot open an empty team enderchest for " + team.getID());
		}

		player.openInventory(inventory);
		viewers.put(player.getUniqueId(), team);
	}

	public static void closeAll() {
		List<Map.Entry<UUID, Team>> sessions = new ArrayList<>(viewers.entrySet());
		viewers.clear();

		for (Map.Entry<UUID, Team> entry : sessions) {
			Player player = Bukkit.getPlayer(entry.getKey());
			Team team = entry.getValue();
			if (player != null && player.getOpenInventory().getTopInventory() == team.getEchest()) {
				player.closeInventory();
			}

			team.markDirty();
		}
	}

	public static void closeForTeam(Team team) {
		List<Map.Entry<UUID, Team>> sessions = viewers.entrySet().stream()
				.filter(entry -> entry.getValue() == team)
				.toList();

		for (Map.Entry<UUID, Team> entry : sessions) {
			if (!viewers.remove(entry.getKey(), team)) {
				continue;
			}

			Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null && player.getOpenInventory().getTopInventory() == team.getEchest()) {
				player.closeInventory();
			}
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent e) {
		if (e.getView().getType() != InventoryType.CHEST
				|| (!(e.getInventory().getHolder(false) instanceof TeamData.EchestInventoryHolder))) {
			return;
		}

		Player player = (Player) e.getPlayer();
		Team team = viewers.get(player.getUniqueId());
		if (team == null || team.getEchest() != e.getInventory()) {
			return;
		}

		viewers.remove(player.getUniqueId(), team);
		team.markDirty();
	}

	@EventHandler
	public void onClick(InventoryClickEvent e) {
		if (e.isCancelled()) {
			return;
		}

		Team team = viewers.get(e.getWhoClicked().getUniqueId());
		if (team != null && e.getView().getTopInventory() == team.getEchest()) {
			team.markDirty();
		}
	}

	@EventHandler
	public void onDrag(InventoryDragEvent e) {
		if (e.isCancelled()) {
			return;
		}

		Team team = viewers.get(e.getWhoClicked().getUniqueId());
		if (team == null || e.getView().getTopInventory() != team.getEchest()) {
			return;
		}

		if (e.getRawSlots().stream().anyMatch(slot -> slot < team.getEchest().getSize())) {
			team.markDirty();
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		Team team = viewers.remove(e.getPlayer().getUniqueId());
		if (team != null) {
			team.markDirty();
		}

	}

}
