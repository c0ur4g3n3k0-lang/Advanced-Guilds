package com.c0ur4g3.guilds.guild;

import com.c0ur4g3.guilds.AdvancedGuilds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class GuildManager {

    private final AdvancedGuilds plugin;
    private final Logger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Guild> guildCache = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerGuildIndex = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();
    private final Set<UUID> guildChatMode = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> awaitingInput = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingAllianceRequests = new ConcurrentHashMap<>();
    private static final String[] DEFAULT_RANK_KEYS = {"leader", "officer", "veteran", "recruit"};
    private static final Map<String, String> DEFAULT_KEYS_BY_NAME = Map.of(
            "Лидер", "leader",
            "Офицер", "officer",
            "Ветеран", "veteran",
            "Рекрут", "recruit"
    );

    public GuildManager(AdvancedGuilds plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void transferOwnership(Guild guild, UUID newOwnerUuid) {
        UUID oldOwner = guild.getOwner();
        guild.setOwner(newOwnerUuid);

        String leaderKey = guild.getHighestRank().map(GuildRank::getKey).orElse("leader");
        String officerKey = guild.getAllRanks().stream()
                .sorted(Comparator.comparingInt(GuildRank::getPriority).reversed())
                .skip(1)
                .findFirst()
                .map(GuildRank::getKey)
                .orElse("officer");

        guild.getMember(oldOwner).ifPresent(m -> {
            if (!leaderKey.equals(m.getRankKey())) {
                m.setRankKey(officerKey);
            }
        });
        guild.getMember(newOwnerUuid).ifPresent(m -> m.setRankKey(leaderKey));

        plugin.getDatabaseManager().saveGuild(guild);
    }

    public void loadAll() {
        plugin.getDatabaseManager().loadAllGuilds().thenAccept(guilds -> {
            for (Guild guild : guilds) {
                guildCache.put(guild.getId(), guild);
                for (GuildMember member : guild.getAllMembers()) {
                    playerGuildIndex.put(member.getPlayerUuid(), guild.getId());
                }
            }
            logger.info("[AdvancedGuilds] Кэш гильдий загружен: " + guilds.size() + " гильдий.");
        }).exceptionally(ex -> {
            logger.severe("[AdvancedGuilds] Критическая ошибка загрузки гильдий: " + ex.getMessage());
            return null;
        });
    }

    // ===========================================================
    //  CRUD операции
    // ===========================================================

    public Guild createGuild(String name, Player owner) {
        UUID guildId = UUID.randomUUID();
        Guild guild = new Guild(guildId, name, "[" + name.substring(0, Math.min(name.length(), 3)).toUpperCase() + "]",
                owner.getUniqueId(), 0.0, false);

        setupDefaultRanks(guild);

        String leaderRankKey = guild.getHighestRank()
                .map(GuildRank::getKey)
                .orElse("leader");
        GuildMember ownerMember = new GuildMember(owner.getUniqueId(), guildId, leaderRankKey);
        guild.addMember(ownerMember);

        guildCache.put(guildId, guild);
        playerGuildIndex.put(owner.getUniqueId(), guildId);

        plugin.getDatabaseManager().saveGuild(guild).exceptionally(ex -> {
            logger.severe("[AdvancedGuilds] Ошибка сохранения новой гильдии: " + ex.getMessage());
            return null;
        });

        return guild;
    }

    @SuppressWarnings("unchecked")
    private void setupDefaultRanks(Guild guild) {
        List<Map<?, ?>> ranksList = plugin.getConfig().getMapList("default-ranks");
        if (ranksList == null || ranksList.isEmpty()) {
            // Фоллбэк: минимальный набор рангов
            guild.addRank(new GuildRank("leader", "Лидер", 100,
                    Set.of(GuildPermission.values())));
            guild.addRank(new GuildRank("officer", "Офицер", 75,
                    Set.of(GuildPermission.INVITE, GuildPermission.KICK, GuildPermission.CHAT_ACCESS)));
            guild.addRank(new GuildRank("veteran", "Ветеран", 25,
                    Set.of(GuildPermission.CHAT_ACCESS)));
            guild.addRank(new GuildRank("recruit", "Рекрут", 1,
                    Set.of(GuildPermission.CHAT_ACCESS)));
            return;
        }

        for (int i = 0; i < ranksList.size(); i++) {
            Map<?, ?> rawMap = ranksList.get(i);
            Map<String, Object> rankData = (Map<String, Object>) rawMap;
            String name = (String) rankData.get("name");
            Object keyObj = rankData.get("key");
            String key = resolveRankKey(name, keyObj, i);
            Object priorityObj = rankData.getOrDefault("priority", 1);
            int priority = (priorityObj instanceof Number num) ? num.intValue() : 1;

            Object permsObj = rankData.getOrDefault("permissions", List.of("CHAT_ACCESS"));
            List<String> permList;
            if (permsObj instanceof List<?>) {
                permList = new ArrayList<>();
                for (Object o : (List<?>) permsObj) {
                    if (o instanceof String s) permList.add(s);
                }
            } else {
                permList = List.of("CHAT_ACCESS");
            }

            Set<GuildPermission> perms = new HashSet<>();
            for (String permStr : permList) {
                try {
                    perms.add(GuildPermission.valueOf(permStr));
                } catch (IllegalArgumentException ignored) {}
            }

            guild.addRank(new GuildRank(key, name != null ? name : key, priority, perms));
        }
    }

    private String resolveRankKey(String name, Object keyObj, int index) {
        if (keyObj instanceof String key && !key.isBlank()) {
            return key;
        }
        if (name != null && DEFAULT_KEYS_BY_NAME.containsKey(name)) {
            return DEFAULT_KEYS_BY_NAME.get(name);
        }
        if (index >= 0 && index < DEFAULT_RANK_KEYS.length) {
            return DEFAULT_RANK_KEYS[index];
        }
        return name != null ? name.toLowerCase().replace(" ", "_") : "rank_" + index;
    }

    public void deleteGuild(UUID guildId) {
        Guild guild = guildCache.remove(guildId);
        if (guild == null) return;

        for (GuildMember member : guild.getAllMembers()) {
            playerGuildIndex.remove(member.getPlayerUuid());
            guildChatMode.remove(member.getPlayerUuid());
        }

        plugin.getDatabaseManager().deleteGuild(guildId).exceptionally(ex -> {
            logger.severe("[AdvancedGuilds] Ошибка удаления гильдии: " + ex.getMessage());
            return null;
        });
    }

    public void addMember(Guild guild, UUID playerUuid) {
        GuildRank lowestRank = guild.getAllRanks().stream()
                .min(Comparator.comparingInt(GuildRank::getPriority))
                .orElse(new GuildRank("recruit", "Рекрут", 1, Set.of(GuildPermission.CHAT_ACCESS)));

        GuildMember member = new GuildMember(playerUuid, guild.getId(), lowestRank.getKey());
        guild.addMember(member);
        playerGuildIndex.put(playerUuid, guild.getId());
        pendingInvites.remove(playerUuid);
        plugin.getDatabaseManager().saveGuild(guild);
    }

    public void removeMember(Guild guild, UUID playerUuid) {
        guild.removeMember(playerUuid);
        playerGuildIndex.remove(playerUuid);
        guildChatMode.remove(playerUuid);

        if (guild.getMemberCount() == 0) {
            deleteGuild(guild.getId());
            return;
        }

        plugin.getDatabaseManager().saveGuild(guild);
    }

    // ===========================================================
    //  ПОИСК ПО КЭШУ
    // ===========================================================

    public Optional<Guild> getGuildByPlayer(UUID playerUuid) {
        UUID guildId = playerGuildIndex.get(playerUuid);
        if (guildId == null) return Optional.empty();
        return Optional.ofNullable(guildCache.get(guildId));
    }

    public Optional<Guild> getGuildByName(String name) {
        return guildCache.values().stream()
                .filter(g -> g.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public Optional<Guild> getGuildById(UUID id) {
        return Optional.ofNullable(guildCache.get(id));
    }

    public Collection<Guild> getAllGuilds() {
        return Collections.unmodifiableCollection(guildCache.values());
    }

    public boolean nameExists(String name) {
        return guildCache.values().stream()
                .anyMatch(g -> g.getName().equalsIgnoreCase(name));
    }

    // ===========================================================
    //  ПРИГЛАШЕНИЯ
    // ===========================================================

    public void addInvite(UUID playerUuid, UUID guildId) {
        pendingInvites.put(playerUuid, guildId);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                pendingInvites.remove(playerUuid), 60 * 20L);
    }

    public Optional<UUID> getInvite(UUID playerUuid) {
        return Optional.ofNullable(pendingInvites.get(playerUuid));
    }

    public void removeInvite(UUID playerUuid) {
        pendingInvites.remove(playerUuid);
    }

    // ===========================================================
    //  ГИЛЬДИЙСКИЙ ЧАТ
    // ===========================================================

    public void toggleGuildChat(UUID playerUuid) {
        if (guildChatMode.contains(playerUuid)) {
            guildChatMode.remove(playerUuid);
        } else {
            guildChatMode.add(playerUuid);
        }
    }

    public boolean isInGuildChat(UUID playerUuid) {
        return guildChatMode.contains(playerUuid);
    }

    // ===========================================================
    //  ОЖИДАНИЕ ВВОДА
    // ===========================================================

    public void setAwaitingInput(UUID playerUuid, String actionKey) {
        awaitingInput.put(playerUuid, actionKey);
    }

    public Optional<String> getAwaitingInput(UUID playerUuid) {
        return Optional.ofNullable(awaitingInput.get(playerUuid));
    }

    public void clearAwaitingInput(UUID playerUuid) {
        awaitingInput.remove(playerUuid);
    }

    public boolean isAwaitingInput(UUID playerUuid) {
        return awaitingInput.containsKey(playerUuid);
    }

    // ===========================================================
    //  ОТНОШЕНИЯ МЕЖДУ ГИЛЬДИЯМИ
    // ===========================================================

    public void sendAllianceRequest(Guild from, Guild to) {
        pendingAllianceRequests.put(from.getId(), to.getId());
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> pendingAllianceRequests.remove(from.getId(), to.getId()), 60 * 20L);
    }

    public Optional<UUID> getPendingAllianceTarget(UUID inviterGuildId) {
        return Optional.ofNullable(pendingAllianceRequests.get(inviterGuildId));
    }

    public void removeAllianceRequest(UUID inviterGuildId) {
        pendingAllianceRequests.remove(inviterGuildId);
    }

    public void acceptAlliance(Guild accepter, Guild requester) {
        pendingAllianceRequests.remove(requester.getId());
        accepter.getAllies().add(requester.getId());
        requester.getAllies().add(accepter.getId());
        accepter.getEnemies().remove(requester.getId());
        requester.getEnemies().remove(accepter.getId());
        plugin.getDatabaseManager().saveRelations(accepter);
        plugin.getDatabaseManager().saveRelations(requester);
        notifyBothGuilds(accepter, requester, "relation-ally-established");
    }

    public void declareEnemy(Guild from, Guild to) {
        boolean wasAlly = from.getAllies().contains(to.getId());
        from.getAllies().remove(to.getId());
        to.getAllies().remove(from.getId());
        from.getEnemies().add(to.getId());
        plugin.getDatabaseManager().saveRelations(from);
        plugin.getDatabaseManager().saveRelations(to);

        if (wasAlly) {
            notifyBothGuilds(from, to, "relation-ally-broken");
        }
        broadcastRelationMessage(to, from, "relation-enemy-declared");
        broadcastRelationMessage(from, to, "relation-enemy-declared-source");
    }

    public void setNeutral(Guild from, Guild to) {
        from.getAllies().remove(to.getId());
        to.getAllies().remove(from.getId());
        from.getEnemies().remove(to.getId());
        to.getEnemies().remove(from.getId());
        plugin.getDatabaseManager().saveRelations(from);
        plugin.getDatabaseManager().saveRelations(to);
        notifyBothGuilds(from, to, "relation-neutral-set");
    }

    public void broadcastToGuild(Guild guild, Component message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (GuildMember member : guild.getAllMembers()) {
                Player online = Bukkit.getPlayer(member.getPlayerUuid());
                if (online != null && online.isOnline()) {
                    online.sendMessage(message);
                }
            }
        });
    }

    private void notifyBothGuilds(Guild first, Guild second, String messageKey) {
        broadcastRelationMessage(first, second, messageKey);
        broadcastRelationMessage(second, first, messageKey);
    }

    private void broadcastRelationMessage(Guild recipient, Guild otherGuild, String messageKey) {
        String tag = resolveGuildTag(otherGuild);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String body = plugin.getConfig().getString("messages." + messageKey, "")
                .replace("<tag>", tag);
        Component component = mm.deserialize(prefix + body);
        broadcastToGuild(recipient, component);
    }

    private String resolveGuildTag(Guild guild) {
        String tag = guild.getTag();
        if (tag != null && !tag.isBlank()) {
            return tag;
        }
        return "<gray>[" + guild.getName() + "]</gray>";
    }
}