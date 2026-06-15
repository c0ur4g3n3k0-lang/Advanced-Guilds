package com.c0ur4g3.guilds.commands;

import com.c0ur4g3.guilds.AdvancedGuilds;
import com.c0ur4g3.guilds.guild.Guild;
import com.c0ur4g3.guilds.guild.GuildMember;
import com.c0ur4g3.guilds.guild.GuildPermission;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GuildCommand implements CommandExecutor, TabCompleter {

    private final AdvancedGuilds plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GuildCommand(AdvancedGuilds plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Эта команда только для игроков.");
            return true;
        }

        String prefix = plugin.getConfig().getString("messages.prefix", "");

        if (args.length == 0) {
            Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
            if (guildOpt.isPresent()) {
                plugin.getGuiFactory().openGuildMenu(player, guildOpt.get());
            } else {
                plugin.getGuiFactory().openCreateMenu(player);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args, prefix);
            case "invite" -> handleInvite(player, args, prefix);
            case "join" -> handleJoin(player, args, prefix);
            case "leave" -> handleLeave(player, prefix);
            case "kick" -> handleKick(player, args, prefix);
            case "deposit" -> handleDeposit(player, args, prefix);
            case "withdraw" -> handleWithdraw(player, args, prefix);
            case "chat" -> handleChat(player, args, prefix);
            case "info" -> handleInfo(player, args, prefix);
            case "disband" -> handleDisband(player, prefix);
            case "transfer" -> handleTransfer(player, args, prefix);
            case "pvp" -> handlePvp(player, prefix);
            case "ally" -> handleAlly(player, args, prefix);
            case "allyaccept" -> handleAllyAccept(player, args, prefix);
            case "enemy" -> handleEnemy(player, args, prefix);
            case "neutral" -> handleNeutral(player, args, prefix);
            case "help" -> sendHelp(player, prefix);
            default -> player.sendMessage(mm.deserialize(prefix +
                    "<red>Неизвестная подкоманда. Используйте <yellow>/guild help</yellow>.</red>"));
        }
        return true;
    }

    private void handleCreate(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild create <название></red>"));
            return;
        }
        if (plugin.getGuildManager().getGuildByPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.already-in-guild", "")));
            return;
        }

        String name = args[1];
        int minLen = plugin.getConfig().getInt("guild.name.min-length", 3);
        int maxLen = plugin.getConfig().getInt("guild.name.max-length", 16);

        if (name.length() < minLen || name.length() > maxLen ||
                !name.matches(plugin.getConfig().getString("guild.name.regex",
                        "^[a-zA-Z0-9а-яА-Я_]+$"))) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.invalid-name", "")));
            return;
        }
        for (String forbidden : plugin.getConfig().getStringList("guild.forbidden-words")) {
            if (name.toLowerCase().contains(forbidden.toLowerCase())) {
                player.sendMessage(mm.deserialize(prefix +
                        plugin.getConfig().getString("messages.invalid-name", "")));
                return;
            }
        }
        if (plugin.getGuildManager().nameExists(name)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.name-taken", "")));
            return;
        }

        double cost = plugin.getConfig().getDouble("guild.creation-cost", 5000.0);
        if (!plugin.getVaultHook().withdraw(player, cost)) {
            String msg = plugin.getConfig().getString("messages.insufficient-funds", "")
                    .replace("<amount>", plugin.getVaultHook().format(cost));
            player.sendMessage(mm.deserialize(prefix + msg));
            return;
        }

        plugin.getGuildManager().createGuild(name, player);
        String msg = plugin.getConfig().getString("messages.guild-created", "")
                .replace("<name>", name);
        player.sendMessage(mm.deserialize(prefix + msg));
    }

    private void handleInvite(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild invite <ник></red>"));
            return;
        }

        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.INVITE)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-permission-rank", "")));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            String msg = plugin.getConfig().getString("messages.player-not-found", "")
                    .replace("<player>", args[1]);
            player.sendMessage(mm.deserialize(prefix + msg));
            return;
        }
        if (plugin.getGuildManager().getGuildByPlayer(target.getUniqueId()).isPresent()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.player-already-member", "")));
            return;
        }

        int maxMembers = plugin.getConfig().getInt("guild.max-members", 50);
        if (guild.getMemberCount() >= maxMembers) {
            player.sendMessage(mm.deserialize(prefix + "<red>Гильдия достигла максимального числа участников (" + maxMembers + ")!</red>"));
            return;
        }

        plugin.getGuildManager().addInvite(target.getUniqueId(), guild.getId());

        String sentMsg = plugin.getConfig().getString("messages.invite-sent", "")
                .replace("<player>", target.getName());
        player.sendMessage(mm.deserialize(prefix + sentMsg));

        String receivedMsg = plugin.getConfig().getString("messages.invite-received", "")
                .replace("<guild>", guild.getName());
        target.sendMessage(mm.deserialize(prefix + receivedMsg));
    }

    private void handleJoin(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild join <название></red>"));
            return;
        }
        if (plugin.getGuildManager().getGuildByPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.already-in-guild", "")));
            return;
        }

        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByName(args[1]);
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix + "<red>Гильдия с таким названием не найдена.</red>"));
            return;
        }
        Guild guild = guildOpt.get();

        Optional<UUID> inviteOpt = plugin.getGuildManager().getInvite(player.getUniqueId());
        if (inviteOpt.isEmpty() || !inviteOpt.get().equals(guild.getId())) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.not-invited", "")));
            return;
        }

        plugin.getGuildManager().addMember(guild, player.getUniqueId());

        String joinedMsg = plugin.getConfig().getString("messages.player-joined", "")
                .replace("<player>", player.getName());
        guild.getAllMembers().stream()
                .map(m -> Bukkit.getPlayer(m.getPlayerUuid()))
                .filter(p -> p != null && p.isOnline())
                .forEach(p -> p.sendMessage(mm.deserialize(prefix + joinedMsg)));
    }

    private void handleLeave(Player player, String prefix) {
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (guild.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize(prefix +
                    "<red>Вы лидер гильдии. Используйте <yellow>/guild disband</yellow> для роспуска.</red>"));
            return;
        }

        String guildName = guild.getName();
        plugin.getGuildManager().removeMember(guild, player.getUniqueId());
        String msg = plugin.getConfig().getString("messages.guild-left", "")
                .replace("<name>", guildName);
        player.sendMessage(mm.deserialize(prefix + msg));
    }

    private void handleKick(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild kick <ник></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.KICK)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-permission-rank", "")));
            return;
        }

        Optional<UUID> targetUuidOpt = guild.getAllMembers().stream()
                .filter(m -> {
                    Player p = Bukkit.getPlayer(m.getPlayerUuid());
                    if (p != null) return p.getName().equalsIgnoreCase(args[1]);
                    String offlineName = Bukkit.getOfflinePlayer(m.getPlayerUuid()).getName();
                    return offlineName != null && offlineName.equalsIgnoreCase(args[1]);
                })
                .map(m -> m.getPlayerUuid())
                .findFirst();

        if (targetUuidOpt.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.player-not-found", "")
                    .replace("<player>", args[1]);
            player.sendMessage(mm.deserialize(prefix + msg));
            return;
        }
        UUID targetUuid = targetUuidOpt.get();

        if (targetUuid.equals(guild.getOwner())) {
            player.sendMessage(mm.deserialize(prefix + "<red>Нельзя исключить лидера гильдии.</red>"));
            return;
        }

        if (!guild.canManageMember(player.getUniqueId(), targetUuid)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.cannot-manage-member", "")));
            return;
        }

        plugin.getGuildManager().removeMember(guild, targetUuid);

        String kickedMsg = plugin.getConfig().getString("messages.guild-kicked", "")
                .replace("<player>", args[1]);
        player.sendMessage(mm.deserialize(prefix + kickedMsg));

        Player targetOnline = Bukkit.getPlayer(targetUuid);
        if (targetOnline != null) {
            String targetMsg = plugin.getConfig().getString("messages.guild-kicked-target", "")
                    .replace("<name>", guild.getName());
            targetOnline.sendMessage(mm.deserialize(prefix + targetMsg));
        }
    }

    private void handleDeposit(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild deposit <сумма></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.invalid-amount", "<red>Некорректная сумма.</red>")));
            return;
        }

        if (!plugin.getVaultHook().hasEnough(player, amount)) {
            String msg = plugin.getConfig().getString("messages.insufficient-funds", "")
                    .replace("<amount>", plugin.getVaultHook().format(amount));
            player.sendMessage(mm.deserialize(prefix + msg));
            return;
        }

        Guild guild = guildOpt.get();
        plugin.getVaultHook().withdraw(player, amount);
        guild.deposit(amount);
        plugin.getDatabaseManager().updateBalance(guild);

        String msg = plugin.getConfig().getString("messages.deposited", "")
                .replace("<amount>", plugin.getVaultHook().format(amount));
        player.sendMessage(mm.deserialize(prefix + msg));
    }

    private void handleWithdraw(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild withdraw <сумма></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.BANK_WITHDRAW)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-permission-rank", "")));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.invalid-amount", "")));
            return;
        }

        if (!guild.withdraw(amount)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.bank-insufficient", "")));
            return;
        }

        plugin.getVaultHook().deposit(player, amount);
        plugin.getDatabaseManager().updateBalance(guild);

        String msg = plugin.getConfig().getString("messages.withdrawn", "")
                .replace("<amount>", plugin.getVaultHook().format(amount));
        player.sendMessage(mm.deserialize(prefix + msg));
    }

    private void handleChat(Player player, String[] args, String prefix) {
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.CHAT_ACCESS)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-permission-rank", "")));
            return;
        }

        if (args.length == 1) {
            plugin.getGuildManager().toggleGuildChat(player.getUniqueId());
            boolean nowEnabled = plugin.getGuildManager().isInGuildChat(player.getUniqueId());
            String msgKey = nowEnabled ? "messages.chat-enabled" : "messages.chat-disabled";
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString(msgKey, "")));
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(args[i]);
            }
            String message = sb.toString();
            String format = plugin.getConfig().getString("guild.chat.format",
                    "<dark_gray>[Гильдия]</dark_gray> <player>: <message>");
            String rankDisplay = guild.getRankForMember(player.getUniqueId())
                    .map(r -> r.getDisplayName())
                    .orElse("Участник");
            String formatted = format
                    .replace("<rank>", rankDisplay)
                    .replace("<player>", player.getName())
                    .replace("<message>", message);

            guild.getAllMembers().stream()
                    .filter(m -> guild.memberHasPermission(m.getPlayerUuid(), GuildPermission.CHAT_ACCESS))
                    .map(m -> Bukkit.getPlayer(m.getPlayerUuid()))
                    .filter(p -> p != null && p.isOnline())
                    .forEach(p -> p.sendMessage(mm.deserialize(formatted)));
        }
    }

    private void handleInfo(Player player, String[] args, String prefix) {
        Guild guild;
        if (args.length < 2) {
            Optional<Guild> own = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
            if (own.isEmpty()) {
                player.sendMessage(mm.deserialize(prefix +
                        plugin.getConfig().getString("messages.no-guild", "")));
                return;
            }
            guild = own.get();
        } else {
            Optional<Guild> found = plugin.getGuildManager().getGuildByName(args[1]);
            if (found.isEmpty()) {
                player.sendMessage(mm.deserialize(prefix + "<red>Гильдия не найдена.</red>"));
                return;
            }
            guild = found.get();
        }

        long online = guild.getAllMembers().stream()
                .filter(m -> Bukkit.getPlayer(m.getPlayerUuid()) != null)
                .count();

        player.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        player.sendMessage(mm.deserialize("<gold><bold>Гильдия: " + guild.getName() + "</bold></gold>"));
        player.sendMessage(mm.deserialize("<gray>Тег:</gray> " + (guild.getTag() != null ? guild.getTag() : "[—]")));
        player.sendMessage(mm.deserialize("<gray>Участники:</gray> <green>" + online + "</green><gray>/</gray><yellow>" + guild.getMemberCount() + "</yellow>"));
        player.sendMessage(mm.deserialize("<gray>Казна:</gray> <gold>" + plugin.getVaultHook().format(guild.getBalance()) + "</gold>"));
        player.sendMessage(mm.deserialize("<gray>Дружественный огонь:</gray> " + (guild.isFriendlyFire() ? "<green>Включён</green>" : "<red>Выключен</red>")));
        player.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
    }

    private void handleDisband(Player player, String prefix) {
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();
        if (!guild.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.not-leader", "")));
            return;
        }
        String name = guild.getName();
        plugin.getGuildManager().deleteGuild(guild.getId());
        String msg = plugin.getConfig().getString("messages.guild-deleted", "")
                .replace("<name>", name);
        player.sendMessage(mm.deserialize(prefix + msg));
    }

    private void handleTransfer(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild transfer <ник></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();
        if (!guild.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.not-leader", "")));
            return;
        }

        Optional<GuildMember> targetOpt = guild.getAllMembers().stream()
                .filter(m -> {
                    Player p = Bukkit.getPlayer(m.getPlayerUuid());
                    if (p != null) return p.getName().equalsIgnoreCase(args[1]);
                    String offlineName = Bukkit.getOfflinePlayer(m.getPlayerUuid()).getName();
                    return offlineName != null && offlineName.equalsIgnoreCase(args[1]);
                })
                .findFirst();

        if (targetOpt.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.player-not-found", "")
                    .replace("<player>", args[1]);
            player.sendMessage(mm.deserialize(prefix + msg));
            return;
        }

        UUID targetUuid = targetOpt.get().getPlayerUuid();
        if (targetUuid.equals(player.getUniqueId())) {
            player.sendMessage(mm.deserialize(prefix + "<red>Вы уже являетесь лидером.</red>"));
            return;
        }

        plugin.getGuildManager().transferOwnership(guild, targetUuid);
        player.sendMessage(mm.deserialize(prefix + "<green>Лидерство передано игроку <yellow>" + args[1] + "</yellow>.</green>"));
    }

    private void handlePvp(Player player, String prefix) {
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-permission-rank", "")));
            return;
        }

        guild.setFriendlyFire(!guild.isFriendlyFire());
        plugin.getDatabaseManager().updateSettings(guild);

        String msgKey = guild.isFriendlyFire() ? "messages.friendly-fire-on" : "messages.friendly-fire-off";
        player.sendMessage(mm.deserialize(prefix + plugin.getConfig().getString(msgKey, "")));
    }

    private void handleAlly(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild ally <название_гильдии></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-permission-rank", "")));
            return;
        }

        Optional<Guild> targetOpt = plugin.getGuildManager().getGuildByName(args[1]);
        if (targetOpt.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.guild-not-found", "")
                    .replace("<guild>", args[1]);
            player.sendMessage(mm.deserialize(prefix + msg));
            return;
        }
        Guild target = targetOpt.get();

        if (target.getId().equals(guild.getId())) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.cannot-relate-self", "")));
            return;
        }
        if (guild.getAllies().contains(target.getId())) {
            player.sendMessage(mm.deserialize(prefix + "<yellow>Вы уже в союзе с этой гильдией.</yellow>"));
            return;
        }

        plugin.getGuildManager().sendAllianceRequest(guild, target);

        String sentMsg = plugin.getConfig().getString("messages.ally-request-sent", "")
                .replace("<guild>", target.getName());
        player.sendMessage(mm.deserialize(prefix + sentMsg));

        target.getAllMembers().stream()
                .filter(m -> target.memberHasPermission(m.getPlayerUuid(), GuildPermission.MANAGE_SETTINGS))
                .map(m -> Bukkit.getPlayer(m.getPlayerUuid()))
                .filter(p -> p != null && p.isOnline())
                .forEach(p -> {
                    String receivedMsg = plugin.getConfig().getString("messages.ally-request-received", "")
                            .replace("<guild>", guild.getName());
                    p.sendMessage(mm.deserialize(prefix + receivedMsg));
                });
    }

    private void handleAllyAccept(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild allyaccept <название_гильдии></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-permission-rank", "")));
            return;
        }

        Optional<Guild> requesterOpt = plugin.getGuildManager().getGuildByName(args[1]);
        if (requesterOpt.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.guild-not-found", "")
                    .replace("<guild>", args[1]);
            player.sendMessage(mm.deserialize(prefix + msg));
            return;
        }
        Guild requester = requesterOpt.get();

        Optional<UUID> pendingTarget = plugin.getGuildManager().getPendingAllianceTarget(requester.getId());
        if (pendingTarget.isEmpty() || !pendingTarget.get().equals(guild.getId())) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.ally-no-request", "")));
            return;
        }

        plugin.getGuildManager().acceptAlliance(guild, requester);

        String formedMsg = plugin.getConfig().getString("messages.ally-formed", "")
                .replace("<guild>", requester.getName());
        player.sendMessage(mm.deserialize(prefix + formedMsg));
    }

    private void handleEnemy(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild enemy <название_гильдии></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-permission-rank", "")));
            return;
        }

        Optional<Guild> targetOpt = plugin.getGuildManager().getGuildByName(args[1]);
        if (targetOpt.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.guild-not-found", "")
                    .replace("<guild>", args[1]);
            player.sendMessage(mm.deserialize(prefix + msg));
            return;
        }
        Guild target = targetOpt.get();

        if (target.getId().equals(guild.getId())) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.cannot-relate-self", "")));
            return;
        }

        plugin.getGuildManager().declareEnemy(guild, target);
    }

    private void handleNeutral(Player player, String[] args, String prefix) {
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(prefix + "<red>Использование: /guild neutral <название_гильдии></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-guild", "")));
            return;
        }
        Guild guild = guildOpt.get();

        if (!guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.no-permission-rank", "")));
            return;
        }

        Optional<Guild> targetOpt = plugin.getGuildManager().getGuildByName(args[1]);
        if (targetOpt.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.guild-not-found", "")
                    .replace("<guild>", args[1]);
            player.sendMessage(mm.deserialize(prefix + msg));
            return;
        }
        Guild target = targetOpt.get();

        if (target.getId().equals(guild.getId())) {
            player.sendMessage(mm.deserialize(prefix +
                    plugin.getConfig().getString("messages.cannot-relate-self", "")));
            return;
        }

        plugin.getGuildManager().setNeutral(guild, target);
    }

    private void sendHelp(Player player, String prefix) {
        player.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━ <gold>AdvancedGuilds</gold> ━━━━━━━━━</dark_gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild</gold> <gray>— Открыть главное меню</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild create <название></gold> <gray>— Создать гильдию</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild invite <ник></gold> <gray>— Пригласить игрока</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild join <название></gold> <gray>— Вступить в гильдию</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild leave</gold> <gray>— Покинуть гильдию</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild kick <ник></gold> <gray>— Исключить игрока</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild deposit <сумма></gold> <gray>— Пополнить казну</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild withdraw <сумма></gold> <gray>— Снять из казны</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild chat [сообщение]</gold> <gray>— Гильдийский чат</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild info [название]</gold> <gray>— Информация о гильдии</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild disband</gold> <gray>— Расформировать гильдию (лидер)</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild transfer <ник></gold> <gray>— Передать лидерство</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild pvp</gold> <gray>— Переключить дружественный огонь</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild ally <гильдия></gold> <gray>— Предложить союз</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild allyaccept <гильдия></gold> <gray>— Принять союз</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild enemy <гильдия></gold> <gray>— Объявить войну</gray>"));
        player.sendMessage(mm.deserialize("<gold>/guild neutral <гильдия></gold> <gray>— Сбросить отношения</gray>"));
        player.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        List<String> completions = new ArrayList<>();
        boolean inGuild = plugin.getGuildManager().getGuildByPlayer(player.getUniqueId()).isPresent();

        if (args.length == 1) {
            if (!inGuild) {
                completions.addAll(List.of("create", "join", "info", "help"));
            } else {
                completions.addAll(List.of("leave", "invite", "kick", "deposit", "withdraw", "chat", "info",
                        "disband", "transfer", "pvp", "ally", "allyaccept", "enemy", "neutral", "help"));
            }
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "invite", "kick" -> {
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !p.equals(player))
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .forEach(completions::add);
                }
                case "join" -> {
                    plugin.getGuildManager().getInvite(player.getUniqueId())
                            .flatMap(gid -> plugin.getGuildManager().getGuildById(gid))
                            .ifPresent(g -> completions.add(g.getName()));
                }
                case "info" -> {
                    plugin.getGuildManager().getAllGuilds().stream()
                            .map(Guild::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .forEach(completions::add);
                }
                case "ally", "allyaccept", "enemy", "neutral" -> {
                    plugin.getGuildManager().getAllGuilds().stream()
                            .map(Guild::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .forEach(completions::add);
                }
                case "deposit", "withdraw" -> {
                    completions.addAll(List.of("100", "500", "1000", "5000"));
                }
                case "transfer" -> {
                    plugin.getGuildManager().getGuildByPlayer(player.getUniqueId()).ifPresent(g ->
                            g.getAllMembers().stream()
                                    .filter(m -> !m.getPlayerUuid().equals(player.getUniqueId()))
                                    .map(m -> Bukkit.getOfflinePlayer(m.getPlayerUuid()).getName())
                                    .filter(n -> n != null && n.toLowerCase().startsWith(args[1].toLowerCase()))
                                    .forEach(completions::add)
                    );
                }
            }
        }

        return completions;
    }
}