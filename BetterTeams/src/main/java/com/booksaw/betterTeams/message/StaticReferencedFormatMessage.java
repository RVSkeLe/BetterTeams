package com.booksaw.betterTeams.message;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link StaticMessage} with {@link MessageManager#getMessage(String, Object...)}.
 */
@Deprecated
public class StaticReferencedFormatMessage extends StaticComponentHolderMessage {

	public StaticReferencedFormatMessage(String reference, Object... replacements) {
		super(MessageManager.getMessage(reference, replacements));
	}

	public StaticReferencedFormatMessage(@Nullable OfflinePlayer player, String reference, Object... replacements) {
		super(MessageManager.getMessage(player, reference, replacements));
	}
}
