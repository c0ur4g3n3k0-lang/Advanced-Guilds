package com.c0ur4g3.guilds.gui;

import com.c0ur4g3.guilds.AdvancedGuilds;
import com.c0ur4g3.guilds.guild.Guild;
import com.c0ur4g3.guilds.guild.GuildMember;
import com.c0ur4g3.guilds.guild.GuildPermission;
import com.c0ur4g3.guilds.guild.GuildRank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GuildGuiFactory {

    private static final int NEUTRAL_DISPLAY_LIMIT = 5;

    private final AdvancedGuilds plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GuildGuiFactory(AdvancedGuilds plugin) {
        this.plugin = plugin;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));

        if (lore.length > 0) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(mm.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreComponents);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorder(Inventory inv, int size) {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, "<gray> </gray>");
        for (int i = 0; i < size; i++) {
            boolean isBorder = (i < 9) || (i >= size - 9) || (i % 9 == 0) || (i % 9 == 8);
            if (isBorder) inv.setItem(i, filler);
        }
    }

    // ==========================================
    //  ГЛАВНОЕ МЕНЮ (для игрока БЕЗ гильдии)
    // ==========================================

    public void openCreateMenu(Player player) {
        GuildInventoryHolder holder = new GuildInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                mm.deserialize("<dark_gray>» <gold>Создание гильдии</gold> «</dark_gray>"));
        holder.setInventory(inv);
        fillBorder(inv, 27);

        double cost = plugin.getConfig().getDouble("guild.creation-cost", 5000.0);
        boolean canAfford = plugin.getVaultHook().hasEnough(player, cost);

        ItemStack createBtn = createItem(
                canAfford ? Material.NETHER_STAR : Material.BARRIER,
                canAfford ? "<green><bold>Создать гильдию</bold></green>" : "<red><bold>Недостаточно средств</bold></red>",
                "<gray>Стоимость создания:</gray> <gold>" + plugin.getVaultHook().format(cost) + "</gold>",
                "<gray>Ваш баланс:</gray> <yellow>" + plugin.getVaultHook().format(
                        plugin.getVaultHook().getBalance(player)) + "</yellow>",
                "",
                canAfford ? "<green>» Нажмите, чтобы создать «</green>" : "<red>» Пополните баланс «</red>"
        );
        inv.setItem(13, createBtn);

        if (canAfford) {
            holder.setClickHandler(13, event -> {
                event.setCancelled(true);
                player.closeInventory();
                plugin.getGuildManager().setAwaitingInput(player.getUniqueId(), "create_guild");
                player.sendMessage(mm.deserialize(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.enter-guild-name", "<yellow>Введите название гильдии:</yellow>")));

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (plugin.getGuildManager().isAwaitingInput(player.getUniqueId())) {
                        plugin.getGuildManager().clearAwaitingInput(player.getUniqueId());
                        player.sendMessage(mm.deserialize(
                                plugin.getConfig().getString("messages.prefix", "") +
                                        plugin.getConfig().getString("messages.action-timeout",
                                                "<red>Время ввода истекло.</red>")));
                    }
                }, 30 * 20L);
            });
        }

        player.openInventory(inv);
    }

    // ==========================================
    //  ГЛАВНОЕ МЕНЮ (для участника гильдии)
    // ==========================================

    public void openGuildMenu(Player player, Guild guild) {
        GuildInventoryHolder holder = new GuildInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>» <gold>" + guild.getName() + "</gold> «</dark_gray>"));
        holder.setInventory(inv);
        fillBorder(inv, 54);

        inv.setItem(4, createGuildInfoItem(guild));

        int slot = 10;
        for (GuildMember member : guild.getAllMembers()) {
            if (slot > 43) break;
            Player online = Bukkit.getPlayer(member.getPlayerUuid());
            String rankDisplay = guild.getRank(member.getRankKey())
                    .map(GuildRank::getDisplayName)
                    .orElse(member.getRankKey());

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(member.getPlayerUuid()));

            String statusColor = online != null ? "<green>" : "<dark_gray>";
            String statusText = online != null ? "Онлайн" : "Оффлайн";
            String playerName = online != null ? online.getName()
                    : Bukkit.getOfflinePlayer(member.getPlayerUuid()).getName();

            skullMeta.displayName(mm.deserialize(statusColor + (playerName != null ? playerName : "Неизвестный") + "</>" )
                    .decoration(TextDecoration.ITALIC, false));
            skullMeta.lore(List.of(
                    mm.deserialize("<gray>Ранг:</gray> <yellow>" + rankDisplay + "</yellow>").decoration(TextDecoration.ITALIC, false),
                    mm.deserialize("<gray>Статус:</gray> " + statusColor + statusText + "</>").decoration(TextDecoration.ITALIC, false),
                    mm.deserialize("<gray>» Нажмите для управления «</gray>").decoration(TextDecoration.ITALIC, false)
            ));
            skull.setItemMeta(skullMeta);
            inv.setItem(slot, skull);

            final int memberSlot = slot;
            final GuildMember targetMember = member;
            holder.setClickHandler(memberSlot, event -> {
                event.setCancelled(true);
                if (targetMember.getPlayerUuid().equals(player.getUniqueId())) {
                    return;
                }
                boolean canManage = guild.canManageMember(player.getUniqueId(), targetMember.getPlayerUuid())
                        && (guild.memberHasPermission(player.getUniqueId(), GuildPermission.KICK)
                        || guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS));
                if (!canManage) {
                    player.sendMessage(mm.deserialize(
                            plugin.getConfig().getString("messages.prefix", "") +
                                    plugin.getConfig().getString("messages.cannot-manage-member",
                                            "<red>Вы не можете управлять этим участником.</red>")));
                    return;
                }
                openMemberManageMenu(player, guild, targetMember);
            });
            slot++;
        }

        if (guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)) {
            ItemStack settingsBtn = createItem(Material.REDSTONE,
                    "<red><bold>Настройки гильдии</bold></red>",
                    "<gray>» Изменить тег, friendly fire,</gray>",
                    "<gray>  управление рангами</gray>",
                    "",
                    "<yellow>» Нажмите для открытия «</yellow>"
            );
            inv.setItem(49, settingsBtn);
            holder.setClickHandler(49, event -> {
                event.setCancelled(true);
                openSettingsMenu(player, guild);
            });
        }

        boolean isOwner = guild.getOwner().equals(player.getUniqueId());
        ItemStack leaveBtn = createItem(isOwner ? Material.TNT : Material.OAK_DOOR,
                isOwner ? "<dark_red><bold>Расформировать гильдию</bold></dark_red>" : "<red><bold>Покинуть гильдию</bold></red>",
                isOwner ? "<gray>Это удалит гильдию навсегда!</gray>" : "<gray>» Нажмите для выхода «</gray>"
        );
        inv.setItem(45, leaveBtn);
        holder.setClickHandler(45, event -> {
            event.setCancelled(true);
            player.closeInventory();
            if (isOwner) {
                openConfirmDeleteMenu(player, guild);
            } else {
                plugin.getGuildManager().removeMember(guild, player.getUniqueId());
                String msg = plugin.getConfig().getString("messages.guild-left", "<yellow>Вы покинули гильдию.</yellow>")
                        .replace("<name>", guild.getName());
                player.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.prefix", "") + msg));
            }
        });

        player.openInventory(inv);
    }

    public ItemStack createGuildInfoItem(Guild guild) {
        String tag = guild.getTag() != null ? guild.getTag() : "[—]";
        long onlineCount = guild.getAllMembers().stream()
                .filter(m -> Bukkit.getPlayer(m.getPlayerUuid()) != null)
                .count();

        return createItem(Material.GOLD_INGOT,
                "<gold><bold>" + guild.getName() + "</bold></gold>",
                "<gray>Тег:</gray> " + tag,
                "<gray>Участники:</gray> <green>" + onlineCount + "</green><gray>/</gray><yellow>" + guild.getMemberCount() + "</yellow>",
                "<gray>Казна:</gray> <gold>" + plugin.getVaultHook().format(guild.getBalance()) + "</gold>",
                "",
                buildAlliesLine(guild),
                buildEnemiesLine(guild),
                buildNeutralLine(guild)
        );
    }

    private String buildAlliesLine(Guild guild) {
        String label = plugin.getConfig().getString("messages.gui-relations-allies", "<gray>Союзники:</gray>");
        return label + " " + formatGuildTags(guild.getAllies());
    }

    private String buildEnemiesLine(Guild guild) {
        String label = plugin.getConfig().getString("messages.gui-relations-enemies", "<gray>Враги:</gray>");
        return label + " " + formatGuildTags(guild.getEnemies());
    }

    private String buildNeutralLine(Guild guild) {
        String label = plugin.getConfig().getString("messages.gui-relations-neutral", "<gray>Нейтралитет:</gray>");
        String none = plugin.getConfig().getString("messages.gui-relations-none", "<gray>Нет</gray>");
        String moreTemplate = plugin.getConfig().getString("messages.gui-relations-more",
                "<gray> и ещё <white><count></white>...</gray>");

        List<Guild> neutralGuilds = plugin.getGuildManager().getAllGuilds().stream()
                .filter(g -> !g.getId().equals(guild.getId()))
                .filter(g -> !guild.getAllies().contains(g.getId()))
                .filter(g -> !guild.getEnemies().contains(g.getId()))
                .toList();

        if (neutralGuilds.isEmpty()) {
            return label + " " + none;
        }

        int total = neutralGuilds.size();
        int displayCount = Math.min(total, NEUTRAL_DISPLAY_LIMIT);
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < displayCount; i++) {
            tags.add(resolveGuildTag(neutralGuilds.get(i)));
        }

        StringBuilder line = new StringBuilder(label).append(" ").append(String.join("<gray>, </gray>", tags));
        if (total > NEUTRAL_DISPLAY_LIMIT) {
            line.append(moreTemplate.replace("<count>", String.valueOf(total - NEUTRAL_DISPLAY_LIMIT)));
        }
        return line.toString();
    }

    private String formatGuildTags(Set<UUID> guildIds) {
        String none = plugin.getConfig().getString("messages.gui-relations-none", "<gray>Нет</gray>");
        if (guildIds.isEmpty()) {
            return none;
        }

        List<String> tags = new ArrayList<>();
        for (UUID guildId : guildIds) {
            plugin.getGuildManager().getGuildById(guildId)
                    .ifPresent(g -> tags.add(resolveGuildTag(g)));
        }
        return tags.isEmpty() ? none : String.join("<gray>, </gray>", tags);
    }

    private String resolveGuildTag(Guild guild) {
        String tag = guild.getTag();
        if (tag != null && !tag.isBlank()) {
            return tag;
        }
        return "<gray>[" + guild.getName() + "]</gray>";
    }

    // ==========================================
    //  МЕНЮ УПРАВЛЕНИЯ УЧАСТНИКОМ
    // ==========================================

    public void openMemberManageMenu(Player player, Guild guild, GuildMember target) {
        if (!guild.canManageMember(player.getUniqueId(), target.getPlayerUuid())) {
            player.sendMessage(mm.deserialize(
                    plugin.getConfig().getString("messages.prefix", "") +
                            plugin.getConfig().getString("messages.cannot-manage-member",
                                    "<red>Вы не можете управлять этим участником.</red>")));
            return;
        }

        String targetName = Bukkit.getOfflinePlayer(target.getPlayerUuid()).getName();
        String displayName = targetName != null ? targetName : "Неизвестный";
        String rankDisplay = guild.getRank(target.getRankKey())
                .map(GuildRank::getDisplayName)
                .orElse(target.getRankKey());

        GuildInventoryHolder holder = new GuildInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                mm.deserialize("<dark_gray>» <yellow>" + displayName + "</yellow> «</dark_gray>"));
        holder.setInventory(inv);
        fillBorder(inv, 27);

        ItemStack info = createItem(Material.PLAYER_HEAD,
                "<yellow><bold>" + displayName + "</bold></yellow>",
                "<gray>Текущий ранг:</gray> <gold>" + rankDisplay + "</gold>"
        );
        inv.setItem(4, info);

        boolean canChangeRank = guild.memberHasPermission(player.getUniqueId(), GuildPermission.MANAGE_SETTINGS)
                || guild.memberHasPermission(player.getUniqueId(), GuildPermission.KICK);
        boolean canKick = guild.memberHasPermission(player.getUniqueId(), GuildPermission.KICK);

        boolean canPromote = canChangeRank && guild.getAdjacentRank(target.getRankKey(), 1).isPresent();
        boolean canDemote = canChangeRank && guild.getAdjacentRank(target.getRankKey(), -1).isPresent();

        ItemStack promoteBtn = createItem(canPromote ? Material.LIME_WOOL : Material.GRAY_WOOL,
                canPromote ? "<green><bold>Повысить ранг</bold></green>" : "<gray>Повысить ранг</gray>",
                canPromote ? "<gray>» Нажмите для повышения «</gray>" : "<red>Максимальный ранг достигнут</red>"
        );
        inv.setItem(11, promoteBtn);
        if (canPromote) {
            holder.setClickHandler(11, event -> {
                event.setCancelled(true);
                if (!guild.canManageMember(player.getUniqueId(), target.getPlayerUuid())) {
                    sendCannotManage(player);
                    return;
                }
                guild.getAdjacentRank(target.getRankKey(), 1).ifPresent(newRank -> {
                    target.setRankKey(newRank.getKey());
                    plugin.getDatabaseManager().updateMemberRank(target);
                    String msg = plugin.getConfig().getString("messages.rank-promoted", "")
                            .replace("<player>", displayName)
                            .replace("<rank>", newRank.getDisplayName());
                    player.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.prefix", "") + msg));
                    openMemberManageMenu(player, guild, target);
                });
            });
        }

        ItemStack demoteBtn = createItem(canDemote ? Material.ORANGE_WOOL : Material.GRAY_WOOL,
                canDemote ? "<gold><bold>Понизить ранг</bold></gold>" : "<gray>Понизить ранг</gray>",
                canDemote ? "<gray>» Нажмите для понижения «</gray>" : "<red>Минимальный ранг достигнут</red>"
        );
        inv.setItem(13, demoteBtn);
        if (canDemote) {
            holder.setClickHandler(13, event -> {
                event.setCancelled(true);
                if (!guild.canManageMember(player.getUniqueId(), target.getPlayerUuid())) {
                    sendCannotManage(player);
                    return;
                }
                guild.getAdjacentRank(target.getRankKey(), -1).ifPresent(newRank -> {
                    target.setRankKey(newRank.getKey());
                    plugin.getDatabaseManager().updateMemberRank(target);
                    String msg = plugin.getConfig().getString("messages.rank-demoted", "")
                            .replace("<player>", displayName)
                            .replace("<rank>", newRank.getDisplayName());
                    player.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.prefix", "") + msg));
                    openMemberManageMenu(player, guild, target);
                });
            });
        }

        ItemStack kickBtn = createItem(canKick ? Material.BARRIER : Material.GRAY_WOOL,
                canKick ? "<red><bold>Исключить</bold></red>" : "<gray>Исключить</gray>",
                canKick ? "<gray>» Нажмите для исключения «</gray>" : "<red>Недостаточно прав</red>"
        );
        inv.setItem(15, kickBtn);
        if (canKick) {
            holder.setClickHandler(15, event -> {
                event.setCancelled(true);
                if (!guild.canManageMember(player.getUniqueId(), target.getPlayerUuid())) {
                    sendCannotManage(player);
                    return;
                }
                player.closeInventory();
                plugin.getGuildManager().removeMember(guild, target.getPlayerUuid());
                String msg = plugin.getConfig().getString("messages.guild-kicked", "")
                        .replace("<player>", displayName);
                player.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.prefix", "") + msg));

                Player targetOnline = Bukkit.getPlayer(target.getPlayerUuid());
                if (targetOnline != null) {
                    String targetMsg = plugin.getConfig().getString("messages.guild-kicked-target", "")
                            .replace("<name>", guild.getName());
                    targetOnline.sendMessage(mm.deserialize(
                            plugin.getConfig().getString("messages.prefix", "") + targetMsg));
                }
            });
        }

        ItemStack backBtn = createItem(Material.ARROW, "<gray>» Назад</gray>", "<gray>Вернуться к списку участников</gray>");
        inv.setItem(22, backBtn);
        holder.setClickHandler(22, event -> {
            event.setCancelled(true);
            openGuildMenu(player, guild);
        });

        player.openInventory(inv);
    }

    private void sendCannotManage(Player player) {
        player.sendMessage(mm.deserialize(
                plugin.getConfig().getString("messages.prefix", "") +
                        plugin.getConfig().getString("messages.cannot-manage-member",
                                "<red>Вы не можете управлять этим участником.</red>")));
    }

    // ==========================================
    //  МЕНЮ НАСТРОЕК ГИЛЬДИИ
    // ==========================================

    public void openSettingsMenu(Player player, Guild guild) {
        GuildInventoryHolder holder = new GuildInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                mm.deserialize("<dark_gray>» <red>Настройки гильдии</red> «</dark_gray>"));
        holder.setInventory(inv);
        fillBorder(inv, 27);

        ItemStack tagBtn = createItem(Material.NAME_TAG,
                "<yellow><bold>Изменить тег</bold></yellow>",
                "<gray>Текущий тег:</gray> " + (guild.getTag() != null ? guild.getTag() : "[—]"),
                "",
                "<green>» Нажмите для изменения «</green>"
        );
        inv.setItem(10, tagBtn);
        holder.setClickHandler(10, event -> {
            event.setCancelled(true);
            player.closeInventory();
            plugin.getGuildManager().setAwaitingInput(player.getUniqueId(), "change_tag:" + guild.getId());
            player.sendMessage(mm.deserialize(
                    plugin.getConfig().getString("messages.prefix", "") +
                            plugin.getConfig().getString("messages.enter-guild-tag",
                                    "<yellow>Введите новый тег гильдии:</yellow>")));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getGuildManager().isAwaitingInput(player.getUniqueId())) {
                    plugin.getGuildManager().clearAwaitingInput(player.getUniqueId());
                    player.sendMessage(mm.deserialize(
                            plugin.getConfig().getString("messages.prefix", "") +
                                    plugin.getConfig().getString("messages.action-timeout",
                                            "<red>Время ввода истекло.</red>")));
                }
            }, 30 * 20L);
        });

        ItemStack colorBtn = createItem(Material.PAINTING,
                "<light_purple><bold>Цвет тега</bold></light_purple>",
                "<gray>Введите Hex-код для окраски тега.</gray>",
                "<gray>Пример: <gold>#FF0000</gold></gray>",
                "",
                "<green>» Нажмите для ввода «</green>"
        );
        inv.setItem(11, colorBtn);
        holder.setClickHandler(11, event -> {
            event.setCancelled(true);
            player.closeInventory();
            plugin.getGuildManager().setAwaitingInput(player.getUniqueId(), "change_tag_color:" + guild.getId());
            player.sendMessage(mm.deserialize(
                    plugin.getConfig().getString("messages.prefix", "") +
                            "<yellow>Введите Hex-код цвета (например, <gold>#FF0000</gold>) или <red>отмена</red>:</yellow>"));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getGuildManager().isAwaitingInput(player.getUniqueId())) {
                    plugin.getGuildManager().clearAwaitingInput(player.getUniqueId());
                    player.sendMessage(mm.deserialize(
                            plugin.getConfig().getString("messages.prefix", "") +
                                    plugin.getConfig().getString("messages.action-timeout",
                                            "<red>Время ввода истекло.</red>")));
                }
            }, 30 * 20L);
        });

        boolean ff = guild.isFriendlyFire();
        ItemStack ffBtn = createItem(ff ? Material.FLINT_AND_STEEL : Material.SHIELD,
                "<white><bold>Дружественный огонь</bold></white>",
                "<gray>Статус:</gray> " + (ff ? "<green>Включён</green>" : "<red>Выключен</red>"),
                "",
                "<yellow>» Нажмите для переключения «</yellow>"
        );
        inv.setItem(12, ffBtn);
        holder.setClickHandler(12, event -> {
            event.setCancelled(true);
            guild.setFriendlyFire(!guild.isFriendlyFire());
            plugin.getDatabaseManager().updateSettings(guild);
            String msgKey = guild.isFriendlyFire() ? "messages.friendly-fire-on" : "messages.friendly-fire-off";
            player.sendMessage(mm.deserialize(
                    plugin.getConfig().getString("messages.prefix", "") +
                            plugin.getConfig().getString(msgKey, "")));
            openSettingsMenu(player, guild);
        });

        ItemStack ranksBtn = createItem(Material.BOOK,
                "<aqua><bold>Управление рангами</bold></aqua>",
                "<gray>Настройте ранги и права</gray>",
                "<gray>участников вашей гильдии.</gray>",
                "",
                "<green>» Нажмите для открытия «</green>"
        );
        inv.setItem(14, ranksBtn);
        holder.setClickHandler(14, event -> {
            event.setCancelled(true);
            openRanksMenu(player, guild);
        });

        ItemStack nameBtn = createItem(Material.WRITABLE_BOOK,
                "<green><bold>Сменить название</bold></green>",
                "<gray>Текущее название:</gray> <yellow>" + guild.getName() + "</yellow>",
                "",
                "<green>» Нажмите для изменения «</green>"
        );
        inv.setItem(16, nameBtn);
        holder.setClickHandler(16, event -> {
            event.setCancelled(true);
            player.closeInventory();
            plugin.getGuildManager().setAwaitingInput(player.getUniqueId(), "change_name:" + guild.getId());
            player.sendMessage(mm.deserialize(
                    plugin.getConfig().getString("messages.prefix", "") +
                            "<yellow>Введите новое название гильдии в чат (или <red>отмена</red> для отмены):</yellow>"));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getGuildManager().isAwaitingInput(player.getUniqueId())) {
                    plugin.getGuildManager().clearAwaitingInput(player.getUniqueId());
                    player.sendMessage(mm.deserialize(
                            plugin.getConfig().getString("messages.prefix", "") +
                                    plugin.getConfig().getString("messages.action-timeout",
                                            "<red>Время ввода истекло.</red>")));
                }
            }, 30 * 20L);
        });

        ItemStack backBtn = createItem(Material.ARROW, "<gray>» Назад</gray>", "<gray>Вернуться в главное меню</gray>");
        inv.setItem(22, backBtn);
        holder.setClickHandler(22, event -> {
            event.setCancelled(true);
            openGuildMenu(player, guild);
        });

        player.openInventory(inv);
    }

    // ==========================================
    //  МЕНЮ УПРАВЛЕНИЯ РАНГАМИ
    // ==========================================

    public void openRanksMenu(Player player, Guild guild) {
        GuildInventoryHolder holder = new GuildInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>» <aqua>Ранги гильдии</aqua> «</dark_gray>"));
        holder.setInventory(inv);
        fillBorder(inv, 54);

        List<GuildRank> ranks = new ArrayList<>(guild.getAllRanks());
        ranks.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        int slot = 10;
        for (GuildRank rank : ranks) {
            if (slot > 43) break;

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Приоритет:</gray> <yellow>" + rank.getPriority() + "</yellow>");
            lore.add("");
            lore.add("<gray>Права доступа:</gray>");

            for (GuildPermission perm : GuildPermission.values()) {
                boolean has = rank.hasPermission(perm);
                lore.add((has ? "<green>✔ " : "<red>✘ ") + permissionDisplayName(perm) + "</>");
            }
            lore.add("");
            lore.add("<yellow>» Нажмите для редактирования «</yellow>");

            ItemStack rankItem = createItem(Material.PAPER,
                    "<white><bold>" + rank.getDisplayName() + "</bold></white>",
                    lore.toArray(new String[0])
            );
            inv.setItem(slot, rankItem);

            final GuildRank finalRank = rank;
            holder.setClickHandler(slot, event -> {
                event.setCancelled(true);
                openRankEditMenu(player, guild, finalRank);
            });
            slot++;
        }

        ItemStack backBtn = createItem(Material.ARROW, "<gray>» Назад</gray>");
        inv.setItem(49, backBtn);
        holder.setClickHandler(49, event -> {
            event.setCancelled(true);
            openSettingsMenu(player, guild);
        });

        player.openInventory(inv);
    }

    // ==========================================
    //  МЕНЮ РЕДАКТИРОВАНИЯ КОНКРЕТНОГО РАНГА
    // ==========================================

    public void openRankEditMenu(Player player, Guild guild, GuildRank rank) {
        GuildInventoryHolder holder = new GuildInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                mm.deserialize("<dark_gray>» <white>Ранг: " + rank.getDisplayName() + "</white> «</dark_gray>"));
        holder.setInventory(inv);
        fillBorder(inv, 27);

        GuildPermission[] permissions = GuildPermission.values();
        int[] slots = {10, 11, 12, 13, 14};

        for (int i = 0; i < permissions.length && i < slots.length; i++) {
            GuildPermission perm = permissions[i];
            boolean has = rank.hasPermission(perm);

            ItemStack permItem = createItem(
                    has ? Material.LIME_DYE : Material.GRAY_DYE,
                    (has ? "<green><bold>" : "<gray>") + permissionDisplayName(perm) + (has ? "</bold></green>" : "</>"),
                    "<gray>Текущий статус:</gray> " + (has ? "<green>Разрешено</green>" : "<red>Запрещено</red>"),
                    "",
                    "<yellow>» Нажмите для переключения «</yellow>"
            );
            inv.setItem(slots[i], permItem);

            final GuildPermission finalPerm = perm;
            holder.setClickHandler(slots[i], event -> {
                event.setCancelled(true);
                if (rank.hasPermission(finalPerm)) {
                    rank.removePermission(finalPerm);
                } else {
                    rank.addPermission(finalPerm);
                }
                plugin.getDatabaseManager().saveGuild(guild);
                openRankEditMenu(player, guild, rank);
            });
        }

        ItemStack backBtn = createItem(Material.ARROW, "<gray>» Назад к рангам</gray>");
        inv.setItem(22, backBtn);
        holder.setClickHandler(22, event -> {
            event.setCancelled(true);
            openRanksMenu(player, guild);
        });

        player.openInventory(inv);
    }

    // ==========================================
    //  МЕНЮ ПОДТВЕРЖДЕНИЯ УДАЛЕНИЯ
    // ==========================================

    public void openConfirmDeleteMenu(Player player, Guild guild) {
        GuildInventoryHolder holder = new GuildInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                mm.deserialize("<dark_red>» Подтверждение удаления «</dark_red>"));
        holder.setInventory(inv);
        fillBorder(inv, 27);

        ItemStack confirmBtn = createItem(Material.TNT,
                "<dark_red><bold>ПОДТВЕРДИТЬ УДАЛЕНИЕ</bold></dark_red>",
                "<gray>Гильдия <red>" + guild.getName() + "</red> будет удалена.</gray>",
                "<gray>Это действие необратимо!</gray>",
                "",
                "<red>» Нажмите для подтверждения «</red>"
        );
        inv.setItem(11, confirmBtn);
        holder.setClickHandler(11, event -> {
            event.setCancelled(true);
            player.closeInventory();
            String deletedName = guild.getName();
            plugin.getGuildManager().deleteGuild(guild.getId());
            String msg = plugin.getConfig().getString("messages.guild-deleted", "<red>Гильдия удалена.</red>")
                    .replace("<name>", deletedName);
            player.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.prefix", "") + msg));
        });

        ItemStack cancelBtn = createItem(Material.EMERALD,
                "<green><bold>Отменить</bold></green>",
                "<gray>Вернуться в главное меню</gray>"
        );
        inv.setItem(15, cancelBtn);
        holder.setClickHandler(15, event -> {
            event.setCancelled(true);
            openGuildMenu(player, guild);
        });

        player.openInventory(inv);
    }

    private String permissionDisplayName(GuildPermission permission) {
        return switch (permission) {
            case INVITE -> "Приглашать игроков";
            case KICK -> "Исключать игроков";
            case MANAGE_SETTINGS -> "Управление настройками";
            case BANK_WITHDRAW -> "Снятие из казны";
            case CHAT_ACCESS -> "Доступ к чату";
        };
    }
}