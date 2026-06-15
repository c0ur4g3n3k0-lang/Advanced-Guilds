package com.c0ur4g3.guilds.guild;

import java.util.UUID;

public class GuildMember {

    private final UUID playerUuid;
    private final UUID guildId;
    private String rankKey;

    public GuildMember(UUID playerUuid, UUID guildId, String rankKey) {
        this.playerUuid = playerUuid;
        this.guildId = guildId;
        this.rankKey = rankKey;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public UUID getGuildId() { return guildId; }
    public String getRankKey() { return rankKey; }
    public void setRankKey(String rankKey) { this.rankKey = rankKey; }
}