package com.booksaw.betterTeams.commands.team;

import com.booksaw.betterTeams.*;
import com.booksaw.betterTeams.commands.ParentCommand;
import com.booksaw.betterTeams.commands.SubCommand;
import com.booksaw.betterTeams.message.HelpMessage;
import com.booksaw.betterTeams.message.MessageManager;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * This class handles the command /team info [team/player]
 *
 * @author booksaw
 */
public class InfoCommand extends SubCommand {

	private final ParentCommand parentCommand;

	public InfoCommand(ParentCommand parentCommand) {
		this.parentCommand = parentCommand;
	}

	public static List<@Nullable String> getInfoMessages(Team team) {
		List<String> infoMessages = new ArrayList<>();

		infoMessages.add(MessageManager.getMessage("info.name", team.getDisplayName()));
		if (team.getDescription() != null && !team.getDescription().isEmpty()) {
			infoMessages.add(MessageManager.getMessage("info.description", team.getDescription()));
		}

		infoMessages.add(MessageManager.getMessage("info.open", team.isOpen()));
		infoMessages.add(MessageManager.getMessage("info.score", team.getScore()));
		infoMessages.add(MessageManager.getMessage("info.money", team.getBalance()));
		infoMessages.add(MessageManager.getMessage("info.level", team.getLevel()));

		int currentMembers = team.getMembers().size();
		int maxMembers = team.getTeamLimit();
		int currentWarps = team.getWarps().size();
		int maxWarps = team.getMaxWarps();

		infoMessages.add(MessageManager.getMessage("info.limits",
				currentMembers, maxMembers, currentWarps, maxWarps));

		infoMessages.add(MessageManager.getMessage("info.tag", team.getTag()));

		if (Main.plugin.getConfig().getBoolean("anchor.enable")) {
			infoMessages.add(MessageManager.getMessage("info.anchor", team.isAnchored()));
		}

		infoMessages.add(getAlliesMessage(team));
		infoMessages.add(getPlayerList(team, PlayerRank.OWNER));
		infoMessages.add(getPlayerList(team, PlayerRank.ADMIN));
		infoMessages.add(getPlayerList(team, PlayerRank.DEFAULT));

		return infoMessages;
	}

	private static String getTeamSetMessage(Set<UUID> teams, final String referenceMessage) {
		StringBuilder tmp = new StringBuilder();
		for (UUID uuid : teams) {
			Team ally = Team.getTeamByTeamId(uuid);

			if (ally == null) {
				Main.plugin.getLogger().warning("Unable to locate team with UUID: " + uuid);
				continue;
			}

			tmp.append(ally.getDisplayName()).append(ChatColor.WHITE).append(", ");
		}

		if (tmp.length() > 2) {
			return MessageManager.getMessage(referenceMessage, tmp.substring(0, tmp.length() - 2));
		}

		return null;
	}

	private static String getAlliesMessage(Team team) {
		return getTeamSetMessage(team.getAllies(), "info.ally");
	}

	private static String getPlayerList(Team team, PlayerRank rank) {
		List<TeamPlayer> users = team.getRank(rank);

		if (!users.isEmpty()) {
			String space = MessageManager.getMessage("info.playerListSpace");
			List<String> playerList = new ArrayList<>();
			for (TeamPlayer player : users) {
				OfflinePlayer offplayer = player.getPlayer();
				playerList.add(MessageManager.getMessage(offplayer, "info." + ((offplayer.isOnline()
								&& player.getOnlinePlayer().map(
								p -> !Utils.isVanished(p)).orElse(false)) ? "onlinePlayer" : "offlinePlayer"),
						player.getPlayerPrefix() + offplayer.getName()));
			}

			return MessageManager.getMessage("info." + rank.toString().toLowerCase(), String.join(space, playerList));
		}

		return null;
	}

	@Override
	public CommandResponse onCommand(CommandSender sender, String label, String[] args) {
		if (args.length == 0) {
			if (!(sender instanceof Player)) {
				return new CommandResponse(new HelpMessage(this, label, parentCommand));
			}
			Team team = Team.getTeamByPlayer((Player) sender);
			if (team == null) {
				return new CommandResponse(new HelpMessage(this, label, parentCommand));
			}
			displayTeamInfo(sender, team);
			return new CommandResponse(true);
		}

		// player or team has been entered
		Team team = Team.getTeamByTeamOrPlayerName(args[0]);

		if (team != null) {
			displayTeamInfo(sender, team);
			return new CommandResponse(true);
		}
		return new CommandResponse("info.needTeam");

	}

	private void displayTeamInfo(CommandSender sender, Team team) {
		List<String> toDisplay = getInfoMessages(team);

		for (String str : toDisplay) {
			if (str == null || str.isEmpty()) {
				continue;
			}
			MessageManager.sendSafeMessage(sender, str, true);
		}
	}

	@Override
	public String getCommand() {
		return "info";
	}

	@Override
	public int getMinimumArguments() {
		return 0;
	}

	@Override
	public String getNode() {
		return "info";
	}

	@Override
	public String getHelp() {
		return "View information about the specified player / team";
	}

	@Override
	public String getArguments() {
		return "[team/player]";
	}

	@Override
	public int getMaximumArguments() {
		return 2;
	}

	@Override
	public void onTabComplete(List<String> options, CommandSender sender, String label, String[] args) {

		addTeamStringList(options, (args.length == 0) ? "" : args[0]);
		addPlayerStringList(options, (args.length == 0) ? "" : args[0]);

	}

}
