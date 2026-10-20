package com.booksaw.betterTeams.team.meta;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeamMetaTest {

	@Test
	void getAllDoesNotExposeTheBackingMap() {
		TeamMeta meta = new TeamMeta();
		try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			meta.load(Map.of("key", "before"));

			Map<String, String> snapshot = meta.getAll();
			meta.load(Map.of("key", "after"));

			assertThat(snapshot).containsEntry("key", "before");
			assertThat(meta.getAll()).containsEntry("key", "after");
		}
	}
}
