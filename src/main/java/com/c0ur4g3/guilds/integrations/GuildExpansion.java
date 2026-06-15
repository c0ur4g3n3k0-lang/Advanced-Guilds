package com.c0ur4g3.guilds.integrations;

import com.c0ur4g3.guilds.AdvancedGuilds;
import com.c0ur4g3.guilds.guild.Guild;
import com.c0ur4g3.guilds.guild.GuildRank;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class GuildExpansion extends PlaceholderExpansion {

    private final AdvancedGuilds plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacyAmpersand();

    public GuildExpansion(AdvancedGuilds plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "guilds";
    }

    @Override
    public @NotNull String getAuthor() {
        return "c0ur4g3";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        String noRank = plugin.getConfig().getString("placeholders.no-rank", "Нет");

        return switch (params.toLowerCase()) {
            case "name" -> guildOpt.map(Guild::getName).orElse("");

            case "tag" -> guildOpt
                    .map(g -> {
                        String tag = g.getTag();
                        if (tag == null || tag.isBlank()) {
                            return "";
                        }
                        return legacySerializer.serialize(mm.deserialize(tag));
                    })
                    .orElse("");

            case "rank" -> guildOpt
                    .flatMap(g -> g.getRankForMember(player.getUniqueId()))
                    .map(GuildRank::getDisplayName)
                    .orElse(noRank);

            case "balance" -> guildOpt
                    .map(g -> plugin.getVaultHook().format(g.getBalance()))
                    .orElse("");

            case "members_online" -> guildOpt
                    .map(g -> String.valueOf(g.getAllMembers().stream()
                            .filter(m -> plugin.getServer().getPlayer(m.getPlayerUuid()) != null)
                            .count()))
                    .orElse("0");

            case "members_total" -> guildOpt
                    .map(g -> String.valueOf(g.getMemberCount()))
                    .orElse("0");

            default -> null;
        };
    }
}