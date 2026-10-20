package com.booksaw.betterTeams.util;

import org.bukkit.Bukkit;

public final class ThreadGuard {

	private ThreadGuard() {
	}

	public static void requirePrimaryThread() {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException("BetterTeams state must be mutated on the Paper thread");
		}
	}
}
