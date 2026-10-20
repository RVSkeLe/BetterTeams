package com.booksaw.betterTeams.commands.team;

import com.booksaw.betterTeams.*;
import com.booksaw.betterTeams.commands.ParentCommand;
import com.booksaw.betterTeams.commands.presets.TeamSubCommand;
import com.booksaw.betterTeams.customEvents.TeamWithdrawEvent;
import com.booksaw.betterTeams.customEvents.post.PostTeamWithdrawEvent;
import com.booksaw.betterTeams.message.HelpMessage;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.util.List;

public class WithdrawCommand extends TeamSubCommand {

	private final ParentCommand parentCommand;

	public WithdrawCommand(ParentCommand parentCommand) {
		this.parentCommand = parentCommand;
	}

	@Override
	public CommandResponse onCommand(TeamPlayer player, String label, String[] args, Team team) {

		double amount;
		try {
			amount = new BigDecimal(args[0]).doubleValue();
		} catch (Exception e) {
			return new CommandResponse(new HelpMessage(this, label, parentCommand));
		}

		if (!isValidAmount(amount)) {
			return new CommandResponse("withdraw.tooLittle");
		}

		final TeamWithdrawEvent event = new TeamWithdrawEvent(team, player, amount);

		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			return new CommandResponse("withdraw.fail");
		}

		amount = event.getAmount();
		if (!isValidAmount(amount)) {
			return new CommandResponse("withdraw.fail");
		}

		if (!team.canWithdrawMoney(amount)) {
			return new CommandResponse("withdraw.notEnough");
		}

		if (Main.econ == null) {
			return new CommandResponse("withdraw.fail");
		}

		if (!team.tryWithdrawMoney(amount)) {
			return new CommandResponse("withdraw.notEnough");
		}

		EconomyResponse response = Main.econ.depositPlayer(player.getPlayer(), amount);

		if (response == null || !response.transactionSuccess()) {
			if (!team.tryDepositMoney(amount)) {
				Main.plugin.getLogger().severe("Could not restore team balance after a failed withdrawal for " + team.getID());
			}
			return new CommandResponse("withdraw.fail");
		}

		Bukkit.getPluginManager().callEvent(new PostTeamWithdrawEvent(team, player, amount));

		return new CommandResponse(true, "withdraw.success");
	}

	private boolean isValidAmount(double amount) {
		return Double.isFinite(amount) && amount > 0;
	}

	@Override
	public String getCommand() {
		return "withdraw";
	}

	@Override
	public String getNode() {
		return "balance";
	}

	@Override
	public String getHelp() {
		return "Withdraw money from the teams balance";
	}

	@Override
	public String getArguments() {
		return "<amount>";
	}

	@Override
	public int getMinimumArguments() {
		return 1;
	}

	@Override
	public int getMaximumArguments() {
		return 1;
	}

	@Override
	public void onTabComplete(List<String> options, CommandSender sender, String label, String[] args) {
		options.add("<amount>");
	}

	@Override
	public PlayerRank getDefaultRank() {
		return PlayerRank.ADMIN;
	}

	@Override
	public boolean runAsync(String[] args) {
		return false;
	}

}
