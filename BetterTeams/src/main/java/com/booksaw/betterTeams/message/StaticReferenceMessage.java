package com.booksaw.betterTeams.message;

/**
 * @deprecated Use {@link StaticMessage} with {@link MessageManager#getMessage(String)}.
 */
@Deprecated
public class StaticReferenceMessage extends StaticComponentHolderMessage {

	public StaticReferenceMessage(String reference) {
		super(MessageManager.getMessage(reference));
	}
}
