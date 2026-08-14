package com.booksaw.betterTeams.team.storage.migration;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.PlayerRank;
import com.booksaw.betterTeams.database.BetterTeamsDatabase;
import com.booksaw.betterTeams.database.TableName;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LegacyStorageMigrator {

	private static final String MIGRATION_MARKER = "legacyStorageMigrated";
	private static final String HOLOPATH = "holos";
	private static final String TEAM_INFO_FOLDER = "teamInfo";
	private static final String TEAMS_FILE = "teams.yml";
	private static final int ECHEST_SIZE = 27;
	private static final Object CONFIGURATION_SERIALIZATION_LOG_LOCK = new Object();

	private final BetterTeamsDatabase database;
	private final Logger logger;
	private final File dataFolder;
	private int downgradedLegacyEchestItems;
	private int skippedLegacyEchestItems;

	public LegacyStorageMigrator(BetterTeamsDatabase database) {
		this.database = database;
		this.logger = Main.plugin.getLogger();
		this.dataFolder = Main.plugin.getDataFolder();
	}

	public void migrateIfNeeded() throws LegacyStorageMigrationException {
		if (isMarkedMigrated()) {
			return;
		}

		LegacySource source = detectSource();
		if (source == LegacySource.NONE) {
			return;
		}

		if (database.hasResult(TableName.TEAM, "1 = 1")) {
			logger.warning("Legacy " + source.displayName + " storage was detected, but SQLite already contains team data. Skipping automatic migration.");
			return;
		}

		logger.info("Legacy " + source.displayName + " storage detected. Migrating team data to SQLite.");
		int importedTeams = switch (source) {
			case MYSQL -> migrateMysql();
			case SEPARATED_YAML -> migrateSeparatedYaml();
			case FLATFILE -> migrateFlatfile();
			case NONE -> 0;
		};

		logLegacyEchestMigrationWarnings();
		markMigrated(source, importedTeams);
		logger.info("Legacy storage migration complete. Imported " + importedTeams + " teams into SQLite.");
	}

	private boolean isMarkedMigrated() {
		return "true".equalsIgnoreCase(database.getResult("dataValue", TableName.PLUGIN_DATA,
				"dataKey = '" + MIGRATION_MARKER + "'"));
	}

	private void markMigrated(LegacySource source, int importedTeams) {
		setPluginData(MIGRATION_MARKER, "true");
		setPluginData("legacyStorageMigrationSource", source.displayName);
		setPluginData("legacyStorageMigrationTeamCount", Integer.toString(importedTeams));
		setPluginData("legacyStorageMigrationTime", Instant.now().toString());
	}

	private LegacySource detectSource() {
		File teamsFile = getTeamsFile();
		YamlConfiguration teamsConfig = teamsFile.exists() ? loadYamlWithoutEchestsOrEmpty(teamsFile) : new YamlConfiguration();

		String storageType = teamsConfig.getString("storageType", "");
		if (storageType.isEmpty()) {
			storageType = Main.plugin.getConfig().getString("storageType", "");
		}
		storageType = storageType.toUpperCase(Locale.ROOT);

		if ("SQL".equals(storageType) || "MYSQL".equals(storageType)) {
			return LegacySource.MYSQL;
		}
		if ("YAML".equals(storageType)) {
			return LegacySource.SEPARATED_YAML;
		}
		if ("FLATFILE".equals(storageType)) {
			return LegacySource.FLATFILE;
		}

		File teamInfo = new File(dataFolder, TEAM_INFO_FOLDER);
		if (teamInfo.isDirectory() && getYamlFiles(teamInfo).length > 0) {
			return LegacySource.SEPARATED_YAML;
		}
		if (!teamsConfig.getStringList("teams").isEmpty() || teamsConfig.getConfigurationSection("team") != null) {
			return LegacySource.FLATFILE;
		}

		return LegacySource.NONE;
	}

	private int migrateSeparatedYaml() throws LegacyStorageMigrationException {
		File teamInfo = new File(dataFolder, TEAM_INFO_FOLDER);
		File[] teamFiles = getYamlFiles(teamInfo);
		List<Relation> allies = new ArrayList<>();
		List<Relation> allyRequests = new ArrayList<>();
		Set<String> importedTeamIds = new HashSet<>();
		int importedTeams = 0;

		for (File teamFile : teamFiles) {
			UUID teamId = parseTeamId(teamFile.getName().replace(".yml", ""));

			try {
				LegacyYaml teamConfig = loadLegacyYamlWithoutEchests(teamFile);
				byte[] echest = serializeLegacyEchest(teamId.toString(), getMap(teamConfig.rawRoot(), "echest"));
				importTeam(teamId, teamConfig.config(), echest, allies, allyRequests);
				importedTeamIds.add(teamId.toString());
				importedTeams++;
			} catch (Throwable t) {
				logger.severe("Failed migrating legacy YAML team " + teamId + ": " + t.getMessage());
//				t.printStackTrace();
			}
		}

		importRelations(allies, allyRequests, importedTeamIds);
		importTeamsFileData();
		return importedTeams;
	}

	private int migrateFlatfile() throws LegacyStorageMigrationException {
		File teamsFile = getTeamsFile();
		if (!teamsFile.exists()) {
			throw new LegacyStorageMigrationException("Could not find legacy teams.yml for flatfile migration");
		}

		LegacyYaml legacyTeams = loadLegacyYamlWithoutEchests(teamsFile);
		YamlConfiguration teamsConfig = legacyTeams.config();
		List<String> teams = teamsConfig.getStringList("teams");
		ConfigurationSection teamSection = teamsConfig.getConfigurationSection("team");
		if (teams.isEmpty() && teamSection != null) {
			teams = new ArrayList<>(teamSection.getKeys(false));
		}

		List<Relation> allies = new ArrayList<>();
		List<Relation> allyRequests = new ArrayList<>();
		Set<String> importedTeamIds = new HashSet<>();
		int importedTeams = 0;

		for (String team : teams) {
			UUID teamId = parseTeamId(team);
			ConfigurationSection section = teamsConfig.getConfigurationSection("team." + team);
			if (section == null) {
				logger.warning("Could not find flatfile data for team " + team + ", skipping");
				continue;
			}
			byte[] echest = serializeLegacyEchest(teamId.toString(), getMap(legacyTeams.rawRoot(), "team", team, "echest"));
			importTeam(teamId, section, echest, allies, allyRequests);
			importedTeamIds.add(teamId.toString());
			importedTeams++;
		}

		importRelations(allies, allyRequests, importedTeamIds);
		importTeamsFileData(teamsConfig);
		return importedTeams;
	}

	private int migrateMysql() throws LegacyStorageMigrationException {
		try (Connection source = openMysqlConnection()) {
			String prefix = Main.plugin.getConfig().getString("database.tablePrefix", "BetterTeams_");
			int importedTeams = copyMysqlTeams(source, prefix);
			copyMysqlPlayers(source, prefix);
			copyMysqlSimpleTable(source, legacyTable(prefix, "Bans"), TableName.BANS,
					"playerUUID", "teamID");
			copyMysqlSimpleTable(source, legacyTable(prefix, "warps"), TableName.WARPS,
					"teamID", "warpInfo");
			copyMysqlSimpleTable(source, legacyTable(prefix, "ChestClaims"), TableName.CHESTCLAIMS,
					"teamID", "chestLoc");
			copyMysqlSimpleTable(source, legacyTable(prefix, "Allies"), TableName.ALLIES,
					"team1ID", "team2ID");
			copyMysqlSimpleTable(source, legacyTable(prefix, "AllyRequests"), TableName.ALLYREQUESTS,
					"requestingTeamID", "receivingTeamID");
			copyMysqlSimpleTable(source, legacyTable(prefix, "TeamMeta"), TableName.TEAM_META,
					"teamID", "metaKey", "metaValue");
			importTeamsFileData();
			return importedTeams;
		} catch (SQLException e) {
			throw new LegacyStorageMigrationException("Could not migrate MySQL storage to SQLite", e);
		}
	}

	private Connection openMysqlConnection() throws LegacyStorageMigrationException {
		ConfigurationSection section = Main.plugin.getConfig().getConfigurationSection("database");
		if (section == null) {
			throw new LegacyStorageMigrationException("No database section found for legacy MySQL migration");
		}

		StringBuilder options = new StringBuilder("?autoReconnect=true");
		for (String property : section.getStringList("additionalOptions")) {
			options.append("&").append(property);
		}

		String url = "jdbc:mysql://" + section.getString("host", "localhost") + ":"
				+ section.getInt("port", 3306) + "/"
				+ section.getString("database", "spigot") + options;

		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			return DriverManager.getConnection(url, section.getString("user", "root"),
					section.getString("password", "password"));
		} catch (ClassNotFoundException | SQLException e) {
			throw new LegacyStorageMigrationException("Could not connect to the legacy MySQL database", e);
		}
	}

	private int copyMysqlTeams(Connection source, String prefix) throws SQLException {
		int importedTeams = 0;
		try (Statement statement = source.createStatement();
			 ResultSet resultSet = statement.executeQuery("SELECT * FROM " + mysqlIdentifier(legacyTable(prefix, "Team")))) {
			ColumnLookup columns = new ColumnLookup(resultSet);
			while (resultSet.next()) {
				String teamId = columns.getString(resultSet, "teamID", null);
				if (teamId == null) {
					continue;
				}
				insertTeam(teamId, columns.getString(resultSet, "name", teamId),
						columns.getString(resultSet, "description", null),
						columns.getBoolean(resultSet, "open", false),
						columns.getInt(resultSet, "score", 0),
						columns.getDouble(resultSet, "money", 0),
						columns.getString(resultSet, "home", null),
						columns.getString(resultSet, "color", "6"),
						serializeLegacyMysqlEchest(teamId, columns.getString(resultSet, "echest", null)),
						columns.getInt(resultSet, "level", 1),
						columns.getString(resultSet, "tag", null),
						columns.getBoolean(resultSet, "pvp", false),
						columns.getBoolean(resultSet, "anchor", false));
				importedTeams++;
			}
		}
		return importedTeams;
	}

	private void copyMysqlPlayers(Connection source, String prefix) throws SQLException {
		try (Statement statement = source.createStatement();
			 ResultSet resultSet = statement.executeQuery("SELECT * FROM " + mysqlIdentifier(legacyTable(prefix, "Players")))) {
			ColumnLookup columns = new ColumnLookup(resultSet);
			while (resultSet.next()) {
				database.executeStatement("INSERT OR IGNORE INTO %s (playerUUID, teamID, playerRank, title, anchor) VALUES (?, ?, ?, ?, ?)"
								.formatted(TableName.PLAYERS),
						columns.getString(resultSet, "playerUUID", null),
						columns.getString(resultSet, "teamID", null),
						columns.getInt(resultSet, "playerRank", PlayerRank.DEFAULT.value),
						columns.getString(resultSet, "title", null),
						columns.getBoolean(resultSet, "anchor", false));
			}
		}
	}

	private void copyMysqlSimpleTable(Connection source, String sourceTable, TableName targetTable, String... columns)
			throws SQLException {
		try (Statement statement = source.createStatement();
			 ResultSet resultSet = statement.executeQuery("SELECT * FROM " + mysqlIdentifier(sourceTable))) {
			ColumnLookup lookup = new ColumnLookup(resultSet);
			String columnList = String.join(", ", columns);
			String placeholders = String.join(", ", Collections.nCopies(columns.length, "?"));
			String sql = "INSERT OR IGNORE INTO %s (%s) VALUES (%s)".formatted(targetTable, columnList, placeholders);

			while (resultSet.next()) {
				Object[] values = new Object[columns.length];
				for (int i = 0; i < columns.length; i++) {
					values[i] = lookup.getObject(resultSet, columns[i]);
				}
				database.executeStatement(sql, values);
			}
		} catch (SQLException e) {
			if (isMissingMysqlTable(e)) {
				logger.info("Legacy MySQL table " + sourceTable + " was not found, skipping it");
				return;
			}
			throw e;
		}
	}

	private boolean isMissingMysqlTable(SQLException e) {
		return "42S02".equals(e.getSQLState()) || e.getErrorCode() == 1146;
	}

	private void importTeam(UUID teamId, ConfigurationSection config, byte[] echest, List<Relation> allies,
	                        List<Relation> allyRequests) throws LegacyStorageMigrationException {
		insertTeam(teamId.toString(), config.getString("name", teamId.toString()),
				config.getString("description", null),
				config.getBoolean("open", false),
				config.getInt("score", 0),
				config.getDouble("money", 0),
				config.getString("home", null),
				config.getString("color", "6"),
				echest,
				config.getInt("level", 1),
				config.getString("tag", null),
				config.getBoolean("pvp", false),
				config.getBoolean("anchor", false));

		for (String player : config.getStringList("players")) {
			importPlayer(teamId, player, config.getStringList("anchoredPlayers"));
		}
		for (String ban : config.getStringList("bans")) {
			insertBan(teamId, ban);
		}
		for (String warp : config.getStringList("warps")) {
			insertWarp(teamId, warp);
		}
		for (String chest : config.getStringList("chests")) {
			insertChestClaim(teamId, chest);
		}
		for (String ally : config.getStringList("allies")) {
			allies.add(new Relation(teamId.toString(), ally));
		}
		for (String allyRequest : config.getStringList("allyrequests")) {
			allyRequests.add(new Relation(teamId.toString(), allyRequest));
		}
		importMeta(teamId, config.getConfigurationSection("meta"));
	}

	private byte[] serializeLegacyMysqlEchest(String teamId, String legacyEchest) {
		try {
			return serializeLegacyInventoryString(teamId, legacyEchest);
		} catch (RuntimeException e) {
			logger.warning("Could not migrate legacy MySQL enderchest contents for team " + teamId);
//			e.printStackTrace();
			return null;
		}
	}

	private byte[] serializeLegacyInventoryString(String teamId, String legacyInventory) {
		if (legacyInventory == null || legacyInventory.isEmpty()) {
			return null;
		}

		Object rawYaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(legacyInventory);
		if (!(rawYaml instanceof Map<?, ?> rawRoot)) {
			return null;
		}
		return serializeLegacyEchest(teamId, getMap(rawRoot, "items"));
	}

	private byte[] serializeLegacyEchest(String teamId, Map<?, ?> rawEchest) {
		if (rawEchest == null || rawEchest.isEmpty()) {
			return null;
		}

		ItemStack[] contents = new ItemStack[ECHEST_SIZE];
		boolean hasItem = false;
		for (int i = 0; i < ECHEST_SIZE; i++) {
			ItemStack item = deserializeLegacyEchestItem(teamId, i, getMapValue(rawEchest, Integer.toString(i)));
			if (item != null) {
				contents[i] = item;
				hasItem = true;
			}
		}
		return hasItem ? ItemStack.serializeItemsAsBytes(contents) : null;
	}

	private ItemStack deserializeLegacyEchestItem(String teamId, int slot, Object rawItem) {
		if (rawItem == null) {
			return null;
		}
		if (rawItem instanceof ItemStack itemStack) {
			return itemStack;
		}
		if (!(rawItem instanceof Map<?, ?> rawItemMap)) {
			logger.warning("Could not migrate legacy enderchest slot " + slot + " for team " + teamId
					+ ": expected item map but found " + rawItem.getClass().getSimpleName());
			return null;
		}

		try {
			Map<String, Object> normalized = normalizeLegacyItemMap(rawItemMap);
			if (normalized.containsKey("meta")) {
				ItemStack itemWithMeta = deserializeLegacyItemWithMeta(teamId, slot, normalized);
				if (itemWithMeta != null) {
					return itemWithMeta;
				}
				return downgradeLegacyEchestItem(rawItemMap);
			}
			return ItemStack.deserialize(normalized);
		} catch (RuntimeException e) {
			return downgradeLegacyEchestItem(rawItemMap);
		}
	}

	private ItemStack deserializeLegacyItemWithMeta(String teamId, int slot, Map<String, Object> normalizedItem) {
		if (hasUnsafeLegacyMeta(normalizedItem)) {
			return null;
		}

		Logger configurationSerializationLogger = Logger.getLogger(ConfigurationSerialization.class.getName());
		synchronized (CONFIGURATION_SERIALIZATION_LOG_LOCK) {
			Level previousLevel = configurationSerializationLogger.getLevel();
			try {
				configurationSerializationLogger.setLevel(Level.OFF);

				YamlConfiguration itemConfig = new YamlConfiguration();
				itemConfig.set("item", normalizedItem);
				itemConfig.loadFromString(itemConfig.saveToString());
				return itemConfig.getItemStack("item");
			} catch (Throwable t) {
				logger.warning("Could not deserialize legacy enderchest ItemMeta for team " + teamId
						+ " slot " + slot + " type " + normalizedItem.get("type")
						+ ". Item will be downgraded. ItemMeta: " + normalizedItem.get("meta"));
				return null;
			} finally {
				configurationSerializationLogger.setLevel(previousLevel);
			}
		}
	}

	private boolean hasUnsafeLegacyMeta(Map<String, Object> normalizedItem) {
		Object meta = normalizedItem.get("meta");
		return meta instanceof Map<?, ?> metaMap && hasAnyKey(metaMap,
				"internal",
				"blockMaterial",
				"blockData",
				"blockState",
				"block-state");
	}

	private boolean hasAnyKey(Map<?, ?> map, String... keys) {
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String key = String.valueOf(entry.getKey());
			for (String unsafeKey : keys) {
				if (unsafeKey.equals(key)) {
					return true;
				}
			}
			Object value = entry.getValue();
			if (value instanceof Map<?, ?> child && hasAnyKey(child, keys)) {
				return true;
			}
			if (value instanceof List<?> list) {
				for (Object child : list) {
					if (child instanceof Map<?, ?> childMap && hasAnyKey(childMap, keys)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private ItemStack downgradeLegacyEchestItem(Map<?, ?> rawItemMap) {
		ItemStack basicItem = deserializeBasicLegacyItem(rawItemMap);
		if (basicItem != null) {
			downgradedLegacyEchestItems++;
			return basicItem;
		}
		skippedLegacyEchestItems++;
		return ItemStack.of(Material.AIR);
	}

	private ItemStack deserializeBasicLegacyItem(Map<?, ?> rawItemMap) {
		Object typeValue = getMapValue(rawItemMap, "type");
		if (typeValue == null) {
			return null;
		}

		Material material = Material.matchMaterial(String.valueOf(typeValue));
		if (material == null) {
			return null;
		}

		int amount = 1;
		Object amountValue = getMapValue(rawItemMap, "amount");
		if (amountValue instanceof Number number) {
			amount = Math.max(1, Math.min(number.intValue(), material.getMaxStackSize()));
		}

		return new ItemStack(material, amount);
	}

	private void logLegacyEchestMigrationWarnings() {
		if (downgradedLegacyEchestItems > 0) {
			logger.warning("Legacy enderchest migration downgraded " + downgradedLegacyEchestItems
					+ " item(s) to basic stacks because their old ItemMeta data is not compatible with this server version.");
		}
		if (skippedLegacyEchestItems > 0) {
			logger.warning("Legacy enderchest migration skipped " + skippedLegacyEchestItems
					+ " item(s) because their old ItemStack data is not compatible with this server version.");
		}
	}

	private Map<String, Object> normalizeLegacyItemMap(Map<?, ?> rawMap) {
		Map<String, Object> normalized = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
			String key = String.valueOf(entry.getKey());
			Object value = normalizeLegacyItemValue(key, entry.getValue());
			normalized.put(key, value);
		}
		return normalized;
	}

	private Object normalizeLegacyItemValue(String key, Object value) {
//		if ("SpawnPotentials".equals(key) && !(value instanceof List<?>)) {
//			return Collections.emptyList();
//		}
		if (value instanceof Map<?, ?> map) {
			return normalizeLegacyItemMap(map);
		}
		if (value instanceof List<?> list) {
			List<Object> normalized = new ArrayList<>(list.size());
			for (Object item : list) {
				normalized.add(normalizeLegacyItemValue("", item));
			}
			return normalized;
		}
		return value;
	}

	private LegacyYaml loadLegacyYamlWithoutEchests(File file) throws LegacyStorageMigrationException {
		try {
			String rawYaml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
			YamlConfiguration config = loadYamlWithoutEchests(rawYaml);
			return new LegacyYaml(config, loadRawYamlMap(rawYaml, file));
		} catch (IOException | InvalidConfigurationException e) {
			throw new LegacyStorageMigrationException("Could not load legacy YAML file " + file.getName(), e);
		}
	}

	private YamlConfiguration loadYamlWithoutEchestsOrEmpty(File file) {
		try {
			String rawYaml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
			return loadYamlWithoutEchests(rawYaml);
		} catch (IOException | InvalidConfigurationException | RuntimeException e) {
			logger.warning("Could not load legacy teams.yml while detecting storage type");
//			e.printStackTrace();
			return new YamlConfiguration();
		}
	}

	private YamlConfiguration loadYamlWithoutEchests(String rawYaml) throws InvalidConfigurationException {
		YamlConfiguration config = new YamlConfiguration();
		config.loadFromString(stripYamlSectionsNamed(rawYaml, "echest"));
		return config;
	}

	private Map<?, ?> loadRawYamlMap(String rawYaml, File file) {
		try {
			Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(rawYaml);
			if (loaded instanceof Map<?, ?> map) {
				return map;
			}
		} catch (RuntimeException e) {
			logger.warning("Could not parse raw legacy YAML for enderchest migration in " + file.getName());
//			e.printStackTrace();
		}
		return Collections.emptyMap();
	}

	private String stripYamlSectionsNamed(String rawYaml, String sectionName) {
		StringBuilder stripped = new StringBuilder(rawYaml.length());
		int skippedIndent = -1;

		for (String line : rawYaml.split("\\R", -1)) {
			String trimmed = line.trim();
			int indent = indentation(line);
			if (skippedIndent >= 0) {
				if (!trimmed.isEmpty() && !trimmed.startsWith("#") && indent <= skippedIndent) {
					skippedIndent = -1;
				} else {
					continue;
				}
			}

			if (isYamlKeyNamed(line, sectionName)) {
				skippedIndent = indent;
				continue;
			}

			stripped.append(line).append('\n');
		}
		return stripped.toString();
	}

	private boolean isYamlKeyNamed(String line, String expectedKey) {
		String trimmed = line.stripLeading();
		if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
			return false;
		}

		int separator = trimmed.indexOf(':');
		if (separator <= 0) {
			return false;
		}

		String key = trimmed.substring(0, separator).trim();
		if ((key.startsWith("'") && key.endsWith("'")) || (key.startsWith("\"") && key.endsWith("\""))) {
			key = key.substring(1, key.length() - 1);
		}
		return expectedKey.equals(key);
	}

	private int indentation(String line) {
		int spaces = 0;
		while (spaces < line.length() && line.charAt(spaces) == ' ') {
			spaces++;
		}
		return spaces;
	}

	private Map<?, ?> getMap(Map<?, ?> map, String... path) {
		Object value = map;
		for (String key : path) {
			if (!(value instanceof Map<?, ?> currentMap)) {
				return null;
			}
			value = getMapValue(currentMap, key);
		}
		return value instanceof Map<?, ?> result ? result : null;
	}

	private Object getMapValue(Map<?, ?> map, String key) {
		if (map.containsKey(key)) {
			return map.get(key);
		}
		try {
			int numericKey = Integer.parseInt(key);
			if (map.containsKey(numericKey)) {
				return map.get(numericKey);
			}
		} catch (NumberFormatException ignored) {
			// Keys like team UUIDs are expected to be non-numeric.
		}
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (key.equals(String.valueOf(entry.getKey()))) {
				return entry.getValue();
			}
		}
		return null;
	}

	private void insertTeam(String teamId, String name, String description, boolean open, int score, double money,
	                        String home, String color, byte[] echest, int level, String tag, boolean pvp,
	                        boolean anchor) {
		database.executeStatement("INSERT OR IGNORE INTO %s (teamID, name, description, open, score, money, home, color, echest, level, tag, pvp, anchor) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
						.formatted(TableName.TEAM),
				teamId, name, description, open, score, money, home, color, echest, level, tag, pvp, anchor);
	}

	private void importPlayer(UUID teamId, String player, List<String> anchoredPlayers) {
		String[] split = player.split(",", 3);
		if (split.length < 2) {
			logger.warning("Could not import legacy player entry `" + player + "` for team " + teamId);
			return;
		}

		PlayerRank rank = PlayerRank.getRank(split[1]);
		if (rank == null) {
			rank = PlayerRank.DEFAULT;
		}

		String title = split.length > 2 ? split[2] : null;
		boolean anchor = anchoredPlayers.contains(split[0]);
		database.executeStatement("INSERT OR IGNORE INTO %s (playerUUID, teamID, playerRank, title, anchor) VALUES (?, ?, ?, ?, ?)"
						.formatted(TableName.PLAYERS),
				split[0], teamId.toString(), rank.value, title, anchor);
	}

	private void insertBan(UUID teamId, String playerUuid) {
		database.executeStatement("INSERT OR IGNORE INTO %s (playerUUID, teamID) VALUES (?, ?)"
				.formatted(TableName.BANS), playerUuid, teamId.toString());
	}

	private void insertWarp(UUID teamId, String warpInfo) {
		database.executeStatement("INSERT OR IGNORE INTO %s (teamID, warpInfo) VALUES (?, ?)"
				.formatted(TableName.WARPS), teamId.toString(), warpInfo);
	}

	private void insertChestClaim(UUID teamId, String location) {
		database.executeStatement("INSERT OR IGNORE INTO %s (teamID, chestLoc) VALUES (?, ?)"
				.formatted(TableName.CHESTCLAIMS), teamId.toString(), location);
	}

	private void importRelations(List<Relation> allies, List<Relation> allyRequests, Set<String> importedTeamIds) {
		int skippedAllies = 0;
		for (Relation ally : deduplicate(allies)) {
			if (!isImportedRelation(ally, importedTeamIds)) {
				skippedAllies++;
				continue;
			}
			database.executeStatement("INSERT OR IGNORE INTO %s (team1ID, team2ID) VALUES (?, ?)"
					.formatted(TableName.ALLIES), ally.first(), ally.second());
		}
		int skippedAllyRequests = 0;
		for (Relation allyRequest : deduplicate(allyRequests)) {
			if (!isImportedRelation(allyRequest, importedTeamIds)) {
				skippedAllyRequests++;
				continue;
			}
			database.executeStatement("INSERT OR IGNORE INTO %s (receivingTeamID, requestingTeamID) VALUES (?, ?)"
					.formatted(TableName.ALLYREQUESTS), allyRequest.first(), allyRequest.second());
		}
		if (skippedAllies > 0) {
			logger.warning("Skipped " + skippedAllies + " legacy ally relation(s) pointing to teams that were not imported.");
		}
		if (skippedAllyRequests > 0) {
			logger.warning("Skipped " + skippedAllyRequests + " legacy ally request(s) pointing to teams that were not imported.");
		}
	}

	private boolean isImportedRelation(Relation relation, Set<String> importedTeamIds) {
		return importedTeamIds.contains(relation.first()) && importedTeamIds.contains(relation.second());
	}

	private List<Relation> deduplicate(List<Relation> relations) {
		Set<String> seen = new HashSet<>();
		List<Relation> deduplicated = new ArrayList<>();
		for (Relation relation : relations) {
			if (relation.first() == null || relation.second() == null) {
				continue;
			}
			String key = relation.first() + "\0" + relation.second();
			if (seen.add(key)) {
				deduplicated.add(relation);
			}
		}
		return deduplicated;
	}

	private void importMeta(UUID teamId, ConfigurationSection meta) {
		if (meta == null) {
			return;
		}
		for (String key : meta.getKeys(false)) {
			database.executeStatement("INSERT OR IGNORE INTO %s (teamID, metaKey, metaValue) VALUES (?, ?, ?)"
							.formatted(TableName.TEAM_META),
					teamId.toString(), key, meta.getString(key));
		}
	}

	private void importTeamsFileData() throws LegacyStorageMigrationException {
		File teamsFile = getTeamsFile();
		if (!teamsFile.exists()) {
			return;
		}
		importTeamsFileData(loadLegacyYamlWithoutEchests(teamsFile).config());
	}

	private void importTeamsFileData(YamlConfiguration teamsConfig) throws LegacyStorageMigrationException {
		List<String> holos = teamsConfig.getStringList(HOLOPATH);
		if (!holos.isEmpty()) {
			setPluginData(HOLOPATH, String.join("\n", holos));
		}

		for (String chestClaim : teamsConfig.getStringList("chestClaims")) {
			String[] split = chestClaim.split(";", 2);
			if (split.length != 2) {
				logger.warning("Could not import legacy chest claim `" + chestClaim + "`");
				continue;
			}
			insertChestClaim(parseTeamId(split[1]), split[0]);
		}
	}

	private void setPluginData(String key, String value) {
		database.executeStatement("INSERT INTO %s (dataKey, dataValue) VALUES (?, ?) ON CONFLICT(dataKey) DO UPDATE SET dataValue = excluded.dataValue"
				.formatted(TableName.PLUGIN_DATA), key, value);
	}

	private UUID parseTeamId(String teamId) throws LegacyStorageMigrationException {
		try {
			return UUID.fromString(teamId);
		} catch (IllegalArgumentException e) {
			throw new LegacyStorageMigrationException("Invalid legacy team UUID: " + teamId, e);
		}
	}

	private File getTeamsFile() {
		return new File(dataFolder, TEAMS_FILE);
	}

	private File[] getYamlFiles(File folder) {
		File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
		return files == null ? new File[0] : files;
	}

	private String legacyTable(String prefix, String table) {
		return prefix + table;
	}

	private String mysqlIdentifier(String identifier) {
		return "`" + identifier.replace("`", "``") + "`";
	}

	private enum LegacySource {
		NONE("none"),
		FLATFILE("flatfile"),
		SEPARATED_YAML("YAML"),
		MYSQL("MySQL");

		private final String displayName;

		LegacySource(String displayName) {
			this.displayName = displayName;
		}
	}

	private record Relation(String first, String second) {
	}

	private record LegacyYaml(YamlConfiguration config, Map<?, ?> rawRoot) {
	}

	private static class ColumnLookup {

		private final Set<String> columns = new HashSet<>();

		ColumnLookup(ResultSet resultSet) throws SQLException {
			ResultSetMetaData metaData = resultSet.getMetaData();
			for (int i = 1; i <= metaData.getColumnCount(); i++) {
				columns.add(metaData.getColumnLabel(i).toLowerCase(Locale.ROOT));
			}
		}

		Object getObject(ResultSet resultSet, String column) throws SQLException {
			return hasColumn(column) ? resultSet.getObject(column) : null;
		}

		String getString(ResultSet resultSet, String column, String defaultValue) throws SQLException {
			return hasColumn(column) ? resultSet.getString(column) : defaultValue;
		}

		int getInt(ResultSet resultSet, String column, int defaultValue) throws SQLException {
			return hasColumn(column) ? resultSet.getInt(column) : defaultValue;
		}

		double getDouble(ResultSet resultSet, String column, double defaultValue) throws SQLException {
			return hasColumn(column) ? resultSet.getDouble(column) : defaultValue;
		}

		boolean getBoolean(ResultSet resultSet, String column, boolean defaultValue) throws SQLException {
			return hasColumn(column) ? resultSet.getBoolean(column) : defaultValue;
		}

		private boolean hasColumn(String column) {
			return columns.contains(column.toLowerCase(Locale.ROOT));
		}
	}
}
