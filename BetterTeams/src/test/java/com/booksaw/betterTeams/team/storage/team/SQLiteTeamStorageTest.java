package com.booksaw.betterTeams.team.storage.team;

import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.team.storage.storageManager.SQLiteStorageManager;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SQLiteTeamStorageTest {

	@Test
	void dirtyMarkingIsOwnedByTheManager() {
		SQLiteStorageManager manager = mock(SQLiteStorageManager.class);
		Team team = mock(Team.class);

		SQLiteTeamStorage storage = new SQLiteTeamStorage(manager, team, true);
		storage.markDirty();

		verify(manager).markDirty(team);
	}
}
