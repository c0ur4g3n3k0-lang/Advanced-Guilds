package com.c0ur4g3.guilds.database;

import com.c0ur4g3.guilds.guild.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private HikariDataSource dataSource;
    private final Logger logger;

    public DatabaseManager(Logger logger) {
        this.logger = logger;
    }

    public void init(ConfigurationSection config) {
        boolean useMySQL = config.getBoolean("mysql", false);
        HikariConfig hikariConfig = new HikariConfig();

        if (useMySQL) {
            String host = config.getString("host", "localhost");
            int port = config.getInt("port", 3306);
            String name = config.getString("name", "advancedguilds");
            String user = config.getString("user", "root");
            String password = config.getString("password", "");

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useSSL=false&characterEncoding=utf8&autoReconnect=true");
            hikariConfig.setUsername(user);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            hikariConfig.setJdbcUrl("jdbc:sqlite:plugins/AdvancedGuilds/database.db");
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
        }

        int poolSize = config.getInt("pool-size", 10);
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30_000);
        hikariConfig.setIdleTimeout(600_000);
        hikariConfig.setMaxLifetime(1_800_000);
        hikariConfig.setPoolName("AdvancedGuilds-Pool");

        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikariConfig);
        logger.info("[AdvancedGuilds] Пул соединений HikariCP инициализирован ("
                + (useMySQL ? "MySQL" : "SQLite") + ")");

        createTables();
    }

    private void createTables() {
        String guildsTable = """
                CREATE TABLE IF NOT EXISTS advanced_guilds (
                    id VARCHAR(36) NOT NULL PRIMARY KEY,
                    name VARCHAR(16) NOT NULL UNIQUE,
                    tag VARCHAR(32),
                    owner VARCHAR(36) NOT NULL,
                    balance DOUBLE NOT NULL DEFAULT 0.0,
                    friendly_fire TINYINT NOT NULL DEFAULT 0
                )
                """;

        String membersTable = """
                CREATE TABLE IF NOT EXISTS advanced_guilds_members (
                    player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    rank_name VARCHAR(32) NOT NULL,
                    FOREIGN KEY (guild_id) REFERENCES advanced_guilds(id) ON DELETE CASCADE
                )
                """;

        String ranksTable = """
                CREATE TABLE IF NOT EXISTS advanced_guilds_ranks (
                    guild_id VARCHAR(36) NOT NULL,
                    rank_name VARCHAR(32) NOT NULL,
                    display_name VARCHAR(64) NOT NULL,
                    priority INT NOT NULL DEFAULT 1,
                    permissions TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY (guild_id, rank_name),
                    FOREIGN KEY (guild_id) REFERENCES advanced_guilds(id) ON DELETE CASCADE
                )
                """;

        // Таблица союзников/врагов
        String relationsTable = """
                CREATE TABLE IF NOT EXISTS advanced_guilds_relations (
                    guild_id1 VARCHAR(36) NOT NULL,
                    guild_id2 VARCHAR(36) NOT NULL,
                    relation_type VARCHAR(10) NOT NULL,
                    PRIMARY KEY (guild_id1, guild_id2),
                    FOREIGN KEY (guild_id1) REFERENCES advanced_guilds(id) ON DELETE CASCADE,
                    FOREIGN KEY (guild_id2) REFERENCES advanced_guilds(id) ON DELETE CASCADE
                )
                """;

        try (Connection conn = dataSource.getConnection()) {
            if (conn.getMetaData().getURL().startsWith("jdbc:sqlite")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(guildsTable);
                stmt.execute(membersTable);
                stmt.execute(ranksTable);
                stmt.execute(relationsTable);
            }
            logger.info("[AdvancedGuilds] Таблицы базы данных готовы.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при создании таблиц!", e);
        }
    }

    public CompletableFuture<List<Guild>> loadAllGuilds() {
        return CompletableFuture.supplyAsync(() -> {
            List<Guild> guilds = new ArrayList<>();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM advanced_guilds")) {

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    String name = rs.getString("name");
                    String tag = rs.getString("tag");
                    UUID owner = UUID.fromString(rs.getString("owner"));
                    double balance = rs.getDouble("balance");
                    boolean friendlyFire = rs.getInt("friendly_fire") == 1;

                    guilds.add(new Guild(id, name, tag, owner, balance, friendlyFire));
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при загрузке гильдий!", e);
                return guilds;
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM advanced_guilds_members WHERE guild_id = ?")) {

                for (Guild guild : guilds) {
                    stmt.setString(1, guild.getId().toString());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                        String rankName = rs.getString("rank_name");
                        guild.addMember(new GuildMember(playerUuid, guild.getId(), rankName));
                    }
                    stmt.clearParameters();
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при загрузке участников!", e);
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM advanced_guilds_ranks WHERE guild_id = ?")) {

                for (Guild guild : guilds) {
                    stmt.setString(1, guild.getId().toString());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        String rankKey = rs.getString("rank_name");
                        String displayName = rs.getString("display_name");
                        int priority = rs.getInt("priority");
                        Set<GuildPermission> perms = GuildRank.deserializePermissions(
                                rs.getString("permissions"));
                        guild.addRank(new GuildRank(rankKey, displayName, priority, perms));
                    }
                    stmt.clearParameters();
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при загрузке рангов!", e);
            }

            // Загрузка отношений
            loadRelationsIntoGuilds(guilds);

            logger.info("[AdvancedGuilds] Загружено гильдий из БД: " + guilds.size());
            return guilds;
        });
    }

    public CompletableFuture<Void> saveGuild(Guild guild) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement stmt = conn.prepareStatement("""
                            REPLACE INTO advanced_guilds (id, name, tag, owner, balance, friendly_fire)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """)) {
                        stmt.setString(1, guild.getId().toString());
                        stmt.setString(2, guild.getName());
                        stmt.setString(3, guild.getTag());
                        stmt.setString(4, guild.getOwner().toString());
                        stmt.setDouble(5, guild.getBalance());
                        stmt.setInt(6, guild.isFriendlyFire() ? 1 : 0);
                        stmt.executeUpdate();
                    }

                    try (PreparedStatement del = conn.prepareStatement(
                            "DELETE FROM advanced_guilds_members WHERE guild_id = ?")) {
                        del.setString(1, guild.getId().toString());
                        del.executeUpdate();
                    }
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO advanced_guilds_members (player_uuid, guild_id, rank_name) VALUES (?, ?, ?)")) {
                        for (GuildMember member : guild.getAllMembers()) {
                            ins.setString(1, member.getPlayerUuid().toString());
                            ins.setString(2, guild.getId().toString());
                            ins.setString(3, member.getRankKey());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }

                    try (PreparedStatement del = conn.prepareStatement(
                            "DELETE FROM advanced_guilds_ranks WHERE guild_id = ?")) {
                        del.setString(1, guild.getId().toString());
                        del.executeUpdate();
                    }
                    try (PreparedStatement ins = conn.prepareStatement("""
                            INSERT INTO advanced_guilds_ranks (guild_id, rank_name, display_name, priority, permissions)
                            VALUES (?, ?, ?, ?, ?)
                            """)) {
                        for (GuildRank rank : guild.getAllRanks()) {
                            ins.setString(1, guild.getId().toString());
                            ins.setString(2, rank.getKey());
                            ins.setString(3, rank.getDisplayName());
                            ins.setInt(4, rank.getPriority());
                            ins.setString(5, rank.serializePermissions());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }

                    saveRelationsInternal(conn, guild);

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при сохранении гильдии "
                        + guild.getName() + "!", e);
            }
        });
    }

    public CompletableFuture<Void> updateMemberRank(GuildMember member) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE advanced_guilds_members SET rank_name = ? WHERE player_uuid = ?")) {
                stmt.setString(1, member.getRankKey());
                stmt.setString(2, member.getPlayerUuid().toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при обновлении ранга участника!", e);
            }
        });
    }

    public CompletableFuture<Void> saveRelations(Guild guild) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    saveRelationsInternal(conn, guild);
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при сохранении отношений гильдии "
                        + guild.getName() + "!", e);
            }
        });
    }

    public CompletableFuture<Map<UUID, List<RelationEntry>>> loadAllRelations() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, List<RelationEntry>> result = new HashMap<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM advanced_guilds_relations");
                 ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                boolean newSchema = hasColumn(meta, "guild_id1");
                while (rs.next()) {
                    UUID guildId1 = UUID.fromString(rs.getString(newSchema ? "guild_id1" : "guild_id"));
                    UUID guildId2 = UUID.fromString(rs.getString(newSchema ? "guild_id2" : "target_guild_id"));
                    String type = rs.getString(newSchema ? "relation_type" : "type");
                    result.computeIfAbsent(guildId1, k -> new ArrayList<>())
                            .add(new RelationEntry(guildId2, type));
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при загрузке отношений!", e);
            }
            return result;
        });
    }

    private void loadRelationsIntoGuilds(List<Guild> guilds) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM advanced_guilds_relations")) {
            Map<UUID, Guild> guildById = new HashMap<>();
            for (Guild guild : guilds) {
                guildById.put(guild.getId(), guild);
            }

            ResultSet rs = stmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            boolean newSchema = hasColumn(meta, "guild_id1");

            while (rs.next()) {
                UUID guildId1 = UUID.fromString(rs.getString(newSchema ? "guild_id1" : "guild_id"));
                UUID guildId2 = UUID.fromString(rs.getString(newSchema ? "guild_id2" : "target_guild_id"));
                String type = rs.getString(newSchema ? "relation_type" : "type");
                Guild guild = guildById.get(guildId1);
                if (guild == null) continue;

                if (isAllyType(type)) {
                    guild.getAllies().add(guildId2);
                } else if (isEnemyType(type)) {
                    guild.getEnemies().add(guildId2);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при загрузке отношений!", e);
        }
    }

    private void saveRelationsInternal(Connection conn, Guild guild) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM advanced_guilds_relations WHERE guild_id1 = ?")) {
            del.setString(1, guild.getId().toString());
            del.executeUpdate();
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO advanced_guilds_relations (guild_id1, guild_id2, relation_type) VALUES (?, ?, ?)")) {
            for (UUID allyId : guild.getAllies()) {
                ins.setString(1, guild.getId().toString());
                ins.setString(2, allyId.toString());
                ins.setString(3, "ALLY");
                ins.addBatch();
            }
            for (UUID enemyId : guild.getEnemies()) {
                ins.setString(1, guild.getId().toString());
                ins.setString(2, enemyId.toString());
                ins.setString(3, "ENEMY");
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private boolean hasColumn(ResultSetMetaData meta, String column) throws SQLException {
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(meta.getColumnName(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllyType(String type) {
        return "ALLY".equalsIgnoreCase(type) || "ally".equalsIgnoreCase(type);
    }

    private boolean isEnemyType(String type) {
        return "ENEMY".equalsIgnoreCase(type) || "enemy".equalsIgnoreCase(type);
    }

    public record RelationEntry(UUID targetGuildId, String relationType) {}

    public CompletableFuture<Void> updateBalance(Guild guild) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE advanced_guilds SET balance = ? WHERE id = ?")) {
                stmt.setDouble(1, guild.getBalance());
                stmt.setString(2, guild.getId().toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при обновлении баланса!", e);
            }
        });
    }

    public CompletableFuture<Void> updateSettings(Guild guild) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE advanced_guilds SET tag = ?, friendly_fire = ? WHERE id = ?")) {
                stmt.setString(1, guild.getTag());
                stmt.setInt(2, guild.isFriendlyFire() ? 1 : 0);
                stmt.setString(3, guild.getId().toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при обновлении настроек!", e);
            }
        });
    }

    public CompletableFuture<Void> deleteGuild(UUID guildId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM advanced_guilds WHERE id = ?")) {
                stmt.setString(1, guildId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "[AdvancedGuilds] Ошибка при удалении гильдии!", e);
            }
        });
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[AdvancedGuilds] Пул соединений HikariCP закрыт.");
        }
    }
}