package com.booksaw.betterTeams.commands.team;

import com.booksaw.betterTeams.*;
import com.booksaw.betterTeams.commands.presets.TeamSubCommand;
import com.booksaw.betterTeams.events.InventoryManagement;
import org.bukkit.command.CommandSender;

import java.util.List;

public class EchestCommand extends TeamSubCommand {

	@Override
	public CommandResponse onCommand(TeamPlayer player, String label, String[] args, Team team) {

		if (InventoryManagement.isViewing(player.getPlayer().getPlayer())) {
			Main.plugin.getLogger().warning(player.getPlayer().getPlayer().getName() + " tried duping :C get em!");
			return new CommandResponse(false);
		}

		InventoryManagement.open(player.getPlayer().getPlayer(), team);

		return new CommandResponse(true);
	}

	@Override
	public String getCommand() {
		return "echest";
	}

	@Override
	public String getNode() {
		return "echest";
	}

	@Override
	public String getHelp() {
		return "View your teams ender chest";
	}

	@Override
	public String getArguments() {
		return "";
	}

	@Override
	public int getMinimumArguments() {
		return 0;
	}

	@Override
	public int getMaximumArguments() {
		return 0;
	}

	@Override
	public void onTabComplete(List<String> options, CommandSender sender, String label, String[] args) {
	}

	@Override
	public PlayerRank getDefaultRank() {
		return PlayerRank.DEFAULT;
	}

	@Override
	public boolean runAsync(String[] args) {
		return false;
	}

}
