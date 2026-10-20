package com.booksaw.betterTeams.events;

import com.booksaw.betterTeams.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryManagementTest {

	@Test
	void closeAllClearsSessionsAndMarksTheTeamDirty() {
		Player player = mock(Player.class);
		Team team = mock(Team.class);
		Inventory inventory = mock(Inventory.class);
		InventoryView view = mock(InventoryView.class);
		UUID playerId = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(playerId);
		when(team.getEchest()).thenReturn(inventory);
		when(inventory.getSize()).thenReturn(27);
		when(view.getTopInventory()).thenReturn(inventory);
		when(player.getOpenInventory()).thenReturn(view);

		try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			InventoryManagement.open(player, team);
			assertThat(InventoryManagement.isViewing(player)).isTrue();

			bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
			InventoryManagement.closeAll();
		}

		assertThat(InventoryManagement.isViewing(player)).isFalse();
		verify(player).closeInventory();
		verify(team).markDirty();
	}
}
