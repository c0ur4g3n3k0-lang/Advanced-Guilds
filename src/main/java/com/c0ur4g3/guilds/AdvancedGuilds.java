package com.c0ur4g3.guilds;

import com.c0ur4g3.guilds.api.GuildAPI;
import com.c0ur4g3.guilds.commands.GuildAdminCommand;
import com.c0ur4g3.guilds.commands.GuildCommand;
import com.c0ur4g3.guilds.database.DatabaseManager;
import com.c0ur4g3.guilds.gui.GuildGuiFactory;
import com.c0ur4g3.guilds.guild.GuildManager;
import com.c0ur4g3.guilds.integrations.GuildExpansion;
import com.c0ur4g3.guilds.integrations.VaultHook;
import com.c0ur4g3.guilds.listeners.GuildListener;
import org.bukkit.plugin.java.JavaPlugin;

public class AdvancedGuilds extends JavaPlugin {

    private static AdvancedGuilds instance;
    private DatabaseManager databaseManager;
    private GuildManager guildManager;
    private VaultHook vaultHook;
    private GuildGuiFactory guiFactory;
    private GuildAPI guildAPI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.vaultHook = new VaultHook(getLogger());
        this.vaultHook.setupEconomy();

        this.databaseManager = new DatabaseManager(getLogger());
        this.databaseManager.init(getConfig().getConfigurationSection("database"));

        this.guildManager = new GuildManager(this);
        this.guildManager.loadAll();

        this.guiFactory = new GuildGuiFactory(this);
        this.guildAPI = new GuildAPI(this);

        var guildCmd = new GuildCommand(this);
        getCommand("guild").setExecutor(guildCmd);
        getCommand("guild").setTabCompleter(guildCmd);

        var adminCmd = new GuildAdminCommand(this);
        getCommand("guilds").setExecutor(adminCmd);
        getCommand("guilds").setTabCompleter(adminCmd);

        getServer().getPluginManager().registerEvents(new GuildListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GuildExpansion(this).register();
            getLogger().info("[AdvancedGuilds] PlaceholderAPI интеграция активирована.");
        }

        getLogger().info("[AdvancedGuilds] Плагин успешно включён! Версия " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("[AdvancedGuilds] Плагин отключён.");
    }

    public static AdvancedGuilds getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public GuildManager getGuildManager() {
        return guildManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public GuildGuiFactory getGuiFactory() {
        return guiFactory;
    }

    public GuildAPI getGuildAPI() {
        return guildAPI;
    }
}