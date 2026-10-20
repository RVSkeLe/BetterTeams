package com.booksaw.betterTeams.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

final class PaperMessageSender {

	public void sendMessage(@NotNull CommandSender recipient, @NotNull Component message) {
		recipient.sendMessage(message);
	}

	public void sendMessage(@NotNull Collection<? extends CommandSender> recipients, @NotNull Component message) {
		recipients.forEach(recipient -> recipient.sendMessage(message));
	}

	public void sendTitle(@NotNull Player recipient, @NotNull Component title) {
		recipient.showTitle(Title.title(title, Component.empty()));
	}

	public void sendTitle(@NotNull Collection<Player> recipients, @NotNull Component title) {
		Title titleObject = Title.title(title, Component.empty());
		recipients.forEach(recipient -> recipient.showTitle(titleObject));
	}

	public void sendSubTitle(@NotNull Player recipient, @NotNull Component subtitle) {
		recipient.showTitle(Title.title(Component.empty(), subtitle));
	}

	public void sendSubTitle(@NotNull Collection<Player> recipients, @NotNull Component subtitle) {
		Title titleObject = Title.title(Component.empty(), subtitle);
		recipients.forEach(recipient -> recipient.showTitle(titleObject));
	}

	public void sendTitleAndSub(@NotNull Player recipient, @NotNull Component title, @NotNull Component subtitle) {
		recipient.showTitle(Title.title(title, subtitle));
	}

	public void sendTitleAndSub(@NotNull Collection<Player> recipients, @NotNull Component title, @NotNull Component subtitle) {
		Title titleObject = Title.title(title, subtitle);
		recipients.forEach(recipient -> recipient.showTitle(titleObject));
	}

	public void sendActionBar(@NotNull Player recipient, @NotNull Component message) {
		recipient.sendActionBar(message);
	}

	public void sendActionBar(@NotNull Collection<Player> recipients, @NotNull Component message) {
		recipients.forEach(recipient -> recipient.sendActionBar(message));
	}
}
