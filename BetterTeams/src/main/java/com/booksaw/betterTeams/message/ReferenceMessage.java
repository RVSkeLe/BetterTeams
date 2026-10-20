package com.booksaw.betterTeams.message;

/**
 * This type of message is used when you are referencing a message stored in
 * messages.yml
 *
 * @author booksaw
 */
public class ReferenceMessage extends ReferencedFormatMessage {

	/**
	 * @param reference The reference for the message
	 */
	public ReferenceMessage(String reference) {
		super(reference);
	}
}
