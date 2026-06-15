package com.c0ur4g3.guilds.api;

import com.c0ur4g3.guilds.AdvancedGuilds;
import com.c0ur4g3.guilds.guild.Guild;
import java.util.Optional;
import java.util.UUID;

public class GuildAPI {

    private final AdvancedGuilds plugin;

    public GuildAPI(AdvancedGuilds plugin) {
        this.plugin = plugin;
    }

    public Optional<Guild> getPlayerGuild(UUID playerUuid) {
        return plugin.getGuildManager().getGuildByPlayer(playerUuid);
    }

    public Optional<Guild> getGuildByName(String name) {
        return plugin.getGuildManager().getGuildByName(name);
    }

    public boolean isInGuild(UUID playerUuid) {
        return plugin.getGuildManager().getGuildByPlayer(playerUuid).isPresent();
    }

    public String getPlayerGuildTag(UUID playerUuid) {
        return getPlayerGuild(playerUuid).map(Guild::getTag).orElse("");
    }

    public String getPlayerRankName(UUID playerUuid) {
        return getPlayerGuild(playerUuid)
                .flatMap(g -> g.getRankForMember(playerUuid))
                .map(r -> r.getDisplayName())
                .orElse("");
    }

    public int getOnlineMembers(UUID guildId) {
        return plugin.getGuildManager().getGuildById(guildId)
                .map(g -> (int) g.getAllMembers().stream()
                        .filter(m -> plugin.getServer().getPlayer(m.getPlayerUuid()) != null)
                        .count())
                .orElse(0);
    }
}