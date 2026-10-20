package com.booksaw.betterTeams.message;

import com.booksaw.betterTeams.text.Formatter;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

public abstract class StaticComponentHolderMessage implements ComponentHolderMessage {

	protected final Component message;

	protected StaticComponentHolderMessage(String message) {
		this.message = Formatter.absolute().process(message);
	}

	protected StaticComponentHolderMessage(Component message) {
		this.message = message;
	}

	@Override
	public Component getMessage() {
		return this.message;
	}

	@Override
	public void sendMessage(CommandSender recipient) {
		MessageManager.sendFullMessage(recipient, message);
	}

	@Override
	public void sendMessage(Collection<? extends CommandSender> recipients) {
		MessageManager.sendFullMessage(recipients, message);
	}

	@Override
	public void sendTitle(Player recipient) {
		MessageManager.sendFullTitle(recipient, message);
	}

	@Override
	public void sendTitle(Collection<Player> recipients) {
		MessageManager.sendFullTitle(recipients, message);
	}
}
