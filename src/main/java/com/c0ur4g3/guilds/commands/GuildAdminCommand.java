package com.c0ur4g3.guilds.commands;

import com.c0ur4g3.guilds.AdvancedGuilds;
import com.c0ur4g3.guilds.guild.Guild;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GuildAdminCommand implements CommandExecutor, TabCompleter {

    private final AdvancedGuilds plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GuildAdminCommand(AdvancedGuilds plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("advancedguilds.admin")) {
            sender.sendMessage(mm.deserialize("<red>Недостаточно прав.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "delete" -> handleDelete(sender, args);
            case "info" -> handleInfo(sender, args);
            case "setbalance" -> handleSetBalance(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Использование: /guilds delete <название></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByName(args[1]);
        if (guildOpt.isEmpty()) {
            sender.sendMessage(mm.deserialize("<red>Гильдия не найдена.</red>"));
            return;
        }
        plugin.getGuildManager().deleteGuild(guildOpt.get().getId());
        sender.sendMessage(mm.deserialize("<green>Гильдия <yellow>" + args[1] + "</yellow> удалена.</green>"));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Использование: /guilds info <название></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByName(args[1]);
        if (guildOpt.isEmpty()) {
            sender.sendMessage(mm.deserialize("<red>Гильдия не найдена.</red>"));
            return;
        }
        Guild guild = guildOpt.get();
        String ownerName = plugin.getServer().getOfflinePlayer(guild.getOwner()).getName();
        sender.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        sender.sendMessage(mm.deserialize("<gold><bold>[ADMIN] " + guild.getName() + "</bold></gold>"));
        sender.sendMessage(mm.deserialize("<gray>Владелец:</gray> <yellow>" + (ownerName != null ? ownerName : "???") + "</yellow>"));
        sender.sendMessage(mm.deserialize("<gray>Участников:</gray> <yellow>" + guild.getMemberCount() + "</yellow>"));
        sender.sendMessage(mm.deserialize("<gray>Баланс:</gray> <gold>" + plugin.getVaultHook().format(guild.getBalance()) + "</gold>"));
        sender.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
    }

    private void handleSetBalance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Использование: /guilds setbalance <название> <сумма></red>"));
            return;
        }
        Optional<Guild> guildOpt = plugin.getGuildManager().getGuildByName(args[1]);
        if (guildOpt.isEmpty()) {
            sender.sendMessage(mm.deserialize("<red>Гильдия не найдена.</red>"));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<red>Некорректная сумма.</red>"));
            return;
        }
        Guild guild = guildOpt.get();
        guild.setBalance(amount);
        plugin.getDatabaseManager().updateBalance(guild);
        sender.sendMessage(mm.deserialize("<green>Баланс установлен: <gold>" + plugin.getVaultHook().format(amount) + "</gold></green>"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<dark_gray>━━━ <gold>AdvancedGuilds Admin</gold> ━━━</dark_gray>"));
        sender.sendMessage(mm.deserialize("<gold>/guilds delete <название></gold> <gray>— Удалить гильдию</gray>"));
        sender.sendMessage(mm.deserialize("<gold>/guilds info <название></gold> <gray>— Информация</gray>"));
        sender.sendMessage(mm.deserialize("<gold>/guilds setbalance <название> <сумма></gold> <gray>— Установить баланс</gray>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("advancedguilds.admin")) return List.of();
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("delete", "info", "setbalance"));
            return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            plugin.getGuildManager().getAllGuilds().forEach(g -> completions.add(g.getName()));
            return completions.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}