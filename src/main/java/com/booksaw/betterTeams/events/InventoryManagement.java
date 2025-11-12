package com.booksaw.betterTeams.events;

import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.message.MessageManager;
import com.booksaw.betterTeams.team.EChestComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.HashMap;
import java.util.Map;

public class InventoryManagement implements Listener {

	public static final Map<Player, Team> adminViewers = new HashMap<>();

	private final String inventoryName;

	public InventoryManagement() {
		inventoryName = MessageManager.getMessage("echest.echest");
	}

	@EventHandler
	public void onClose(InventoryCloseEvent e) {
		if (!(e.getInventory().getHolder(false) instanceof EChestComponent.EchestInventoryHolder)) return;
		if (!(e.getPlayer() instanceof Player player)) return;

		Team t = adminViewers.get(player);

		if (t == null) {
			t = Team.getTeam(player);
			if (t == null) {
				return;
			}
		}

		adminViewers.remove(player);
		t.saveEchest();
	}

}
