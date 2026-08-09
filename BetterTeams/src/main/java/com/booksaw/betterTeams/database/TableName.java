package com.booksaw.betterTeams.database;

public enum TableName {
	TEAM("Team"), PLAYERS("Players"), ALLYREQUESTS("AllyRequests"), WARPS("warps"), CHESTCLAIMS("ChestClaims"),
	BANS("Bans"), ALLIES("Allies"), TEAM_META("TeamMeta"), PLUGIN_DATA("PluginData");

	private final String tableName;

	TableName(String tableName) {
		this.tableName = tableName;
	}

	@Override
	public String toString() {
		return tableName;
	}

}
