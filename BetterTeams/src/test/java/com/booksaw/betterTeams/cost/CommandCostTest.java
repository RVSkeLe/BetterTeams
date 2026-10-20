package com.booksaw.betterTeams.cost;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.Team;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandCostTest {

	@Test
	void refundsThePlayerWhenTheTeamBalanceCannotBeDebited() {
		Player player = mock(Player.class);
		Team team = mock(Team.class);
		Economy economy = mock(Economy.class);
		boolean previousCostSource = CostManager.costFromTeam;
		Economy previousEconomy = Main.econ;
		CostManager.costFromTeam = true;
		Main.econ = economy;
		when(player.hasPermission("betterteams.cost.bypass")).thenReturn(false);
		when(team.getMoney()).thenReturn(5D);
		when(team.tryWithdrawMoney(5D)).thenReturn(false);
		when(economy.withdrawPlayer(player, 5D)).thenReturn(response(ResponseType.SUCCESS));
		when(economy.depositPlayer(player, 5D)).thenReturn(response(ResponseType.SUCCESS));

		try {
			try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
				 MockedStatic<Team> teams = org.mockito.Mockito.mockStatic(Team.class)) {
				bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
				teams.when(() -> Team.getTeamByPlayer(player)).thenReturn(team);

				assertThat(new CommandCost("test", 10D).runCommand(player)).isFalse();
			}

			verify(economy).depositPlayer(player, 5D);
		} finally {
			CostManager.costFromTeam = previousCostSource;
			Main.econ = previousEconomy;
		}
	}

	private EconomyResponse response(ResponseType type) {
		return new EconomyResponse(5D, 0D, type, null);
	}
}
