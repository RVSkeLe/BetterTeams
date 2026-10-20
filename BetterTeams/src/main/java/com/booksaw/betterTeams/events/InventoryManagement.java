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

	private static final Map<UUID, ViewerSession> viewers = new HashMap<>();

	private record ViewerSession(Team team, Inventory inventory) {
	}

	public static boolean isViewing(Player player) {
		return viewers.containsKey(player.getUniqueId());
	}

	public static void open(Player player, Team team) {
		ViewerSession previous = viewers.remove(player.getUniqueId());
		if (previous != null) {
			previous.team().markDirty();
		}

		Inventory inventory = team.getEchest();
		if (inventory == null || inventory.getSize() == 0) {
			throw new IllegalStateException("Cannot open an empty team enderchest for " + team.getID());
		}

		player.openInventory(inventory);
		viewers.put(player.getUniqueId(), new ViewerSession(team, inventory));
	}

	public static void closeAll() {
		List<Map.Entry<UUID, ViewerSession>> sessions = new ArrayList<>(viewers.entrySet());
		viewers.clear();

		for (Map.Entry<UUID, ViewerSession> entry : sessions) {
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null && player.getOpenInventory().getTopInventory() == entry.getValue().inventory()) {
				player.closeInventory();
			}

			ViewerSession session = entry.getValue();
			session.team().markDirty();
		}
	}

	public static void closeForTeam(Team team) {
		List<Map.Entry<UUID, ViewerSession>> sessions = viewers.entrySet().stream()
				.filter(entry -> entry.getValue().team() == team)
				.toList();

		for (Map.Entry<UUID, ViewerSession> entry : sessions) {
			if (!viewers.remove(entry.getKey(), entry.getValue())) {
				continue;
			}

			Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null && player.getOpenInventory().getTopInventory() == entry.getValue().inventory()) {
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
		ViewerSession session = viewers.get(player.getUniqueId());
		if (session == null || session.inventory() != e.getInventory()) {
			return;
		}

		viewers.remove(player.getUniqueId(), session);
		session.team().markDirty();
	}

	@EventHandler
	public void onClick(InventoryClickEvent e) {
		if (e.isCancelled()) {
			return;
		}

		ViewerSession session = viewers.get(e.getWhoClicked().getUniqueId());
		if (session != null && e.getView().getTopInventory() == session.inventory()) {
			session.team().markDirty();
		}
	}

	@EventHandler
	public void onDrag(InventoryDragEvent e) {
		if (e.isCancelled()) {
			return;
		}

		ViewerSession session = viewers.get(e.getWhoClicked().getUniqueId());
		if (session == null || e.getView().getTopInventory() != session.inventory()) {
			return;
		}

		if (e.getRawSlots().stream().anyMatch(slot -> slot < session.inventory().getSize())) {
			session.team().markDirty();
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		ViewerSession session = viewers.remove(e.getPlayer().getUniqueId());
		if (session != null) {
			session.team().markDirty();
		}

	}

}
