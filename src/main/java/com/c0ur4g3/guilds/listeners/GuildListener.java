package com.c0ur4g3.guilds.listeners;

import com.c0ur4g3.guilds.AdvancedGuilds;
import com.c0ur4g3.guilds.guild.Guild;
import com.c0ur4g3.guilds.guild.GuildPermission;
import com.c0ur4g3.guilds.guild.GuildRank;
import com.c0ur4g3.guilds.gui.GuildInventoryHolder;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class GuildListener implements Listener {

    private final AdvancedGuilds plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Pattern namePattern;
    private final Pattern tagPattern;

    public GuildListener(AdvancedGuilds plugin) {
        this.plugin = plugin;
        String nameRegex = plugin.getConfig().getString("guild.name.regex",
                "^[a-zA-Z0-9а-яА-Я_]+$");
        String tagRegex = plugin.getConfig().getString("guild.tag.regex",
                "^[a-zA-Z0-9а-яА-Я_]+$");
        this.namePattern = Pattern.compile(nameRegex);
        this.tagPattern = Pattern.compile(tagRegex);
    }

    // ==========================================
    //  GUI — Обработка кликов
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuildInventoryHolder holder)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getRawSlot() >= event.getInventory().getSize()) return;

        holder.handleClick(event);
    }

    // ==========================================
    //  ЧАТ — Перехват ввода и гильдийский чат
    // ==========================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());

        Optional<String> awaitingOpt = plugin.getGuildManager().getAwaitingInput(uuid);
        if (awaitingOpt.isPresent()) {
            event.setCancelled(true);
            String actionKey = awaitingOpt.get();
            plugin.getGuildManager().clearAwaitingInput(uuid);

            if (rawMessage.equalsIgnoreCase("отмена") || rawMessage.equalsIgnoreCase("cancel")) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(mm.deserialize(
                                plugin.getConfig().getString("messages.prefix", "") +
                                        plugin.getConfig().getString("messages.action-cancelled",
                                                "<red>Действие отменено.</red>"))));
                return;
            }

            if (actionKey.equals("create_guild")) {
                handleCreateGuildInput(player, rawMessage);
            } else if (actionKey.startsWith("change_tag:")) {
                String guildIdStr = actionKey.substring("change_tag:".length());
                handleChangeTagInput(player, guildIdStr, rawMessage);
            } else if (actionKey.startsWith("change_name:")) {
                String guildIdStr = actionKey.substring("change_name:".length());
                handleChangeNameInput(player, guildIdStr, rawMessage);
            } else if (actionKey.startsWith("change_tag_color:")) {
                String guildIdStr = actionKey.substring("change_tag_color:".length());
                handleChangeTagColorInput(player, guildIdStr, rawMessage);
            }
            return;
        }

        if (plugin.getGuildManager().isInGuildChat(uuid)) {
            event.setCancelled(true);
            Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(uuid);
            if (guildOpt.isEmpty()) {
                plugin.getGuildManager().toggleGuildChat(uuid);
                return;
            }
            Guild guild = guildOpt.get();
            broadcastGuildChat(player, guild, rawMessage);
        }
    }

    private void handleCreateGuildInput(Player player, String input) {
        int minLen = plugin.getConfig().getInt("guild.name.min-length", 3);
        int maxLen = plugin.getConfig().getInt("guild.name.max-length", 16);

        Bukkit.getScheduler().runTask(plugin, () -> {
            String prefix = plugin.getConfig().getString("messages.prefix", "");

            // Валидация длины
            if (input.length() < minLen || input.length() > maxLen) {
                player.sendMessage(mm.deserialize(prefix +
                        plugin.getConfig().getString("messages.invalid-name",
                                "<red>Недопустимое название.</red>")));
                return;
            }
            if (!namePattern.matcher(input).matches()) {
                player.sendMessage(mm.deserialize(prefix +
                        plugin.getConfig().getString("messages.invalid-name",
                                "<red>Недопустимое название.</red>")));
                return;
            }
            for (String forbidden : plugin.getConfig().getStringList("guild.forbidden-words")) {
                if (input.toLowerCase().contains(forbidden.toLowerCase())) {
                    player.sendMessage(mm.deserialize(prefix +
                            plugin.getConfig().getString("messages.invalid-name",
                                    "<red>Недопустимое название.</red>")));
                    return;
                }
            }
            if (plugin.getGuildManager().nameExists(input)) {
                player.sendMessage(mm.deserialize(prefix +
                        plugin.getConfig().getString("messages.name-taken",
                                "<red>Гильдия с таким названием уже существует.</red>")));
                return;
            }
            if (plugin.getGuildManager().getGuildByPlayer(player.getUniqueId()).isPresent()) {
                player.sendMessage(mm.deserialize(prefix +
                        plugin.getConfig().getString("messages.already-in-guild", "")));
                return;
            }
            double cost = plugin.getConfig().getDouble("guild.creation-cost", 5000.0);
            if (!plugin.getVaultHook().withdraw(player, cost)) {
                String msg = plugin.getConfig().getString("messages.insufficient-funds", "")
                        .replace("<amount>", plugin.getVaultHook().format(cost));
                player.sendMessage(mm.deserialize(prefix + msg));
                return;
            }
            plugin.getGuildManager().createGuild(input, player);
            String msg = plugin.getConfig().getString("messages.guild-created", "")
                    .replace("<name>", input);
            player.sendMessage(mm.deserialize(prefix + msg));
        });
    }

    private void handleChangeTagInput(Player player, String guildIdStr, String input) {
        int minLen = plugin.getConfig().getInt("guild.tag.min-length", 2);
        int maxLen = plugin.getConfig().getInt("guild.tag.max-length", 5);

        Bukkit.getScheduler().runTask(plugin, () -> {
            String prefix = plugin.getConfig().getString("messages.prefix", "");

            if (input.length() < minLen || input.length() > maxLen || !tagPattern.matcher(input).matches()) {
                player.sendMessage(mm.deserialize(prefix +
                        plugin.getConfig().getString("messages.invalid-tag",
                                "<red>Недопустимый тег.</red>")));
                return;
            }
            try {
                UUID guildId = UUID.fromString(guildIdStr);
                plugin.getGuildManager().getGuildById(guildId).ifPresent(guild -> {
                    if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)) {
                        player.sendMessage(mm.deserialize(prefix +
                                plugin.getConfig().getString("messages.no-permission-rank", "")));
                        return;
                    }
                    String formattedTag = "[" + input.toUpperCase() + "]";
                    guild.setTag(formattedTag);
                    plugin.getDatabaseManager().updateSettings(guild);
                    String msg = plugin.getConfig().getString("messages.tag-changed", "")
                            .replace("<tag>", formattedTag);
                    player.sendMessage(mm.deserialize(prefix + msg));
                });
            } catch (IllegalArgumentException e) {
                player.sendMessage(mm.deserialize(prefix + "<red>Ошибка: гильдия не найдена.</red>"));
            }
        });
    }

    private void broadcastGuildChat(Player sender, Guild guild, String message) {
        String format = plugin.getConfig().getString("guild.chat.format",
                "<dark_gray>[Гильдия]</dark_gray> <player>: <message>");
        String rankDisplay = guild.getRankForMember(sender.getUniqueId())
                .map(GuildRank::getDisplayName)
                .orElse("Участник");
        String formatted = format
                .replace("<rank>", rankDisplay)
                .replace("<player>", sender.getName())
                .replace("<message>", message);

        Bukkit.getScheduler().runTask(plugin, () -> {
            guild.getAllMembers().stream()
                    .filter(m -> guild.memberHasPermission(m.getPlayerUuid(), GuildPermission.CHAT_ACCESS))
                    .map(m -> Bukkit.getPlayer(m.getPlayerUuid()))
                    .filter(p -> p != null && p.isOnline())
                    .forEach(p -> p.sendMessage(mm.deserialize(formatted)));
        });
    }

    private void handleChangeNameInput(Player player, String guildIdStr, String input) {
        int minLen = plugin.getConfig().getInt("guild.name.min-length", 3);
        int maxLen = plugin.getConfig().getInt("guild.name.max-length", 16);

        Bukkit.getScheduler().runTask(plugin, () -> {
            String prefix = plugin.getConfig().getString("messages.prefix", "");

            if (input.length() < minLen || input.length() > maxLen || !namePattern.matcher(input).matches()) {
                player.sendMessage(mm.deserialize(prefix +
                        plugin.getConfig().getString("messages.invalid-name", "<red>Недопустимое название.</red>")));
                return;
            }
            for (String forbidden : plugin.getConfig().getStringList("guild.forbidden-words")) {
                if (input.toLowerCase().contains(forbidden.toLowerCase())) {
                    player.sendMessage(mm.deserialize(prefix +
                            plugin.getConfig().getString("messages.invalid-name", "")));
                    return;
                }
            }
            if (plugin.getGuildManager().nameExists(input)) {
                player.sendMessage(mm.deserialize(prefix +
                        plugin.getConfig().getString("messages.name-taken", "")));
                return;
            }

            try {
                UUID guildId = UUID.fromString(guildIdStr);
                plugin.getGuildManager().getGuildById(guildId).ifPresent(guild -> {
                    if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)) {
                        player.sendMessage(mm.deserialize(prefix +
                                plugin.getConfig().getString("messages.no-permission-rank", "")));
                        return;
                    }
                    String oldName = guild.getName();
                    guild.setName(input);
                    plugin.getDatabaseManager().saveGuild(guild);
                    player.sendMessage(mm.deserialize(prefix +
                            "<green>Название гильдии изменено с <yellow>" + oldName + "</yellow> на <yellow>" + input + "</yellow>.</green>"));
                });
            } catch (IllegalArgumentException e) {
                player.sendMessage(mm.deserialize(prefix + "<red>Ошибка: гильдия не найдена.</red>"));
            }
        });
    }

    private void handleChangeTagColorInput(Player player, String guildIdStr, String input) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String prefix = plugin.getConfig().getString("messages.prefix", "");

            if (!input.matches("^#[0-9A-Fa-f]{6}$")) {
                player.sendMessage(mm.deserialize(prefix + "<red>Некорректный Hex-код. Используйте формат #RRGGBB.</red>"));
                return;
            }

            try {
                UUID guildId = UUID.fromString(guildIdStr);
                plugin.getGuildManager().getGuildById(guildId).ifPresent(guild -> {
                    if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)) {
                        player.sendMessage(mm.deserialize(prefix +
                                plugin.getConfig().getString("messages.no-permission-rank", "")));
                        return;
                    }
                    String currentTag = guild.getTag();
                    if (currentTag == null || currentTag.isBlank()) {
                        player.sendMessage(mm.deserialize(prefix + "<red>Сначала установите тег гильдии.</red>"));
                        return;
                    }
                    String plainTag = currentTag.replaceAll("<.*?>", "");
                    String coloredTag = "<color:" + input + ">" + plainTag + "</color>";
                    guild.setTag(coloredTag);
                    plugin.getDatabaseManager().updateSettings(guild);
                    player.sendMessage(mm.deserialize(prefix + "<green>Цвет тега изменён. Новый тег: " + coloredTag + "</green>"));
                });
            } catch (IllegalArgumentException e) {
                player.sendMessage(mm.deserialize(prefix + "<red>Ошибка: гильдия не найдена.</red>"));
            }
        });
    }

    // ==========================================
    //  PvP — Friendly Fire защита
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (!(damager instanceof Player attackerPlayer) || !(victim instanceof Player victimPlayer)) return;

        Optional<Guild> attackerGuild = plugin.getGuildManager().getGuildByPlayer(attackerPlayer.getUniqueId());
        if (attackerGuild.isEmpty()) return;

        Optional<Guild> victimGuild = plugin.getGuildManager().getGuildByPlayer(victimPlayer.getUniqueId());
        if (victimGuild.isEmpty()) return;

        Guild ag = attackerGuild.get();
        Guild vg = victimGuild.get();

        if (ag.getId().equals(vg.getId()) && !ag.isFriendlyFire()) {
            event.setCancelled(true);
            return;
        }

        if (ag.getAllies().contains(vg.getId()) && vg.getAllies().contains(ag.getId())
                && !ag.isFriendlyFire()) {
            event.setCancelled(true);
        }
    }

    // ==========================================
    //  ВЫХОД ИГРОКА — Очистка кэша
    // ==========================================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getGuildManager().clearAwaitingInput(uuid);
        if (plugin.getGuildManager().isInGuildChat(uuid)) {
            plugin.getGuildManager().toggleGuildChat(uuid);
        }
        plugin.getGuildManager().removeInvite(uuid);
    }
}