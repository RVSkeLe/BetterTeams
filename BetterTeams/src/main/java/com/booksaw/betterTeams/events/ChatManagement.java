package com.booksaw.betterTeams.events;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import com.booksaw.betterTeams.message.MessageManager;
import com.booksaw.betterTeams.text.LegacyTextUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ChatManagement implements Listener {

	private static PrefixType doPrefix;
	public final List<CommandSender> spy = new ArrayList<>();

	public static void enable() {
		doPrefix = PrefixType.getType(Main.plugin.getConfig().getString("prefix"));
	}

	/**
	 * This detects when the player speaks and either adds a prefix or puts the
	 * message in the team chat
	 *
	 * @param event the chat event
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onChat(AsyncPlayerChatEvent event) {

		if (event.isCancelled()) {
			return;
		}

		Player p = event.getPlayer();
		Team team = Team.getTeamByPlayer(p);

		if (team == null) {
			return;
		}

		TeamPlayer teamPlayer = team.getTeamPlayer(p);

		if (teamPlayer == null) {
			throw new IllegalStateException("Player " + p.getName() + " is registered to be in a team, yet has no playerdata associated with that team");
		}

		String anyChatToGlobalPrefix = Main.plugin.getConfig().getString("chatPrefixes.teamOrAllyToGlobal", "!");
		String globalToTeamPrefix = Main.plugin.getConfig().getString("chatPrefixes.globalToTeam", "!");
		String globalToAllyPrefix = Main.plugin.getConfig().getString("chatPrefixes.globalToAlly", "?");

		if (teamPlayer.isInTeamChat() || teamPlayer.isInAllyChat()) {
			if (!anyChatToGlobalPrefix.isEmpty() && event.getMessage().startsWith(anyChatToGlobalPrefix) && event.getMessage().length() > anyChatToGlobalPrefix.length()) {
				event.setMessage(event.getMessage().substring(anyChatToGlobalPrefix.length()));
			} else {
				// Player is not sending to global chat
				event.setCancelled(true);

				if (teamPlayer.isInTeamChat()) {
					team.getTeamMessageController().sendTeamChatMessage(teamPlayer, event.getMessage());
				} else {
					team.getTeamMessageController().sendAllyChatMessage(teamPlayer, event.getMessage());
				}
				// Used as some chat plugins do not accept when a message is cancelled
				event.setMessage("");
				event.setFormat("");
				return;
			}
		} else if (
				(!globalToTeamPrefix.isEmpty() && event.getMessage().startsWith(globalToTeamPrefix) && event.getMessage().length() > globalToTeamPrefix.length())
						|| (!globalToAllyPrefix.isEmpty() && event.getMessage().startsWith(globalToAllyPrefix) && event.getMessage().length() > globalToAllyPrefix.length())
		) {
			// Player is not sending to global chat
			event.setCancelled(true);

			if (event.getMessage().startsWith(globalToTeamPrefix)) {
				team.getTeamMessageController().sendTeamChatMessage(teamPlayer, event.getMessage().substring(globalToTeamPrefix.length()));
			} else {
				team.getTeamMessageController().sendAllyChatMessage(teamPlayer, event.getMessage().substring(globalToAllyPrefix.length()));
			}
			// Used as some chat plugins do not accept when a message is cancelled
			event.setMessage("");
			event.setFormat("");
			return;
		}

		PrefixType prefixType = doPrefix;
		if (prefixType != PrefixType.NONE) {
			event.setFormat(LegacyTextUtils.parseAllAdventure(getUpdatedFormat(prefixType, p, event.getFormat(), team)));
			// event.setFormat(ChatColor.AQUA + "[" + team.getName() + "] " + ChatColor.WHITE + event.getFormat());
		}

	}

	private String getUpdatedFormat(PrefixType prefixType, Player player, String format, Team team) {
		if (Bukkit.isPrimaryThread()) {
			return prefixType.getUpdatedFormat(player, format, team);
		}

		try {
			return Bukkit.getScheduler().callSyncMethod(Main.plugin,
					() -> prefixType.getUpdatedFormat(player, format, team)).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return format;
		} catch (ExecutionException e) {
			Main.plugin.getLogger().warning("Could not format the team chat prefix: " + e.getCause());
			return format;
		}
	}

	@EventHandler
	public void spyQuit(PlayerQuitEvent e) {
		spy.remove(e.getPlayer());
	}

	enum PrefixType {
		NONE, NAME, TAG;

		public static PrefixType getType(String str) {
			str = str.toLowerCase().trim();
			switch (str) {
				case "name":
				case "true":
					return NAME;
				case "tag":
					return TAG;
				default:
					return NONE;
			}
		}

		public String getUpdatedFormat(Player p, String format, Team team) {
			switch (this) {
				case NAME:
					return MessageManager.getMessage(p, "prefixSyntax", team.getDisplayName(), format);
				case TAG:
					return MessageManager.getMessage(p, "prefixSyntax", team.getColor() + team.getTag(), format);
				default:
					return format;
			}
		}

	}

}
