package com.booksaw.betterTeams.team.storage.team;

import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.Utils;
import com.booksaw.betterTeams.team.storage.storageManager.SQLiteStorageManager;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SQLiteTeamStorageTest {

	@Test
	void failedSnapshotKeepsTheTeamDirty() {
		SQLiteStorageManager manager = mock(SQLiteStorageManager.class);
		Team team = mock(Team.class);
		Inventory inventory = mock(Inventory.class);
		when(team.getEchest()).thenReturn(inventory);
		when(team.getMembersClone()).thenThrow(new IllegalStateException("snapshot failure"));

		SQLiteTeamStorage storage = new SQLiteTeamStorage(manager, team, true);
		storage.markDirty();

		try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
			 MockedStatic<Utils> utils = org.mockito.Mockito.mockStatic(Utils.class)) {
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			utils.when(() -> Utils.serializeInventoryAsBytes(inventory)).thenReturn(new byte[0]);

			assertThatThrownBy(() -> storage.snapshot(false))
					.isInstanceOf(IllegalStateException.class)
					.hasMessage("snapshot failure");
		}

		assertThat(storage.isDirty()).isTrue();
	}
}
