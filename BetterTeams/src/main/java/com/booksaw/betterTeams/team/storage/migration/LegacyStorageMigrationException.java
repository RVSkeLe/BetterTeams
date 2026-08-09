package com.booksaw.betterTeams.team.storage.migration;

public class LegacyStorageMigrationException extends Exception {

	public LegacyStorageMigrationException(String message) {
		super(message);
	}

	public LegacyStorageMigrationException(String message, Throwable cause) {
		super(message, cause);
	}
}
