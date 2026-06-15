package com.c0ur4g3.guilds.integrations;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.util.logging.Logger;

public class VaultHook {

    private Economy economy;
    private final Logger logger;

    public VaultHook(Logger logger) {
        this.logger = logger;
    }

    public boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.warning("[AdvancedGuilds] Vault не найден! Функции экономики недоступны.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            logger.warning("[AdvancedGuilds] Провайдер экономики не найден! Установите EssentialsX или аналог.");
            return false;
        }

        this.economy = rsp.getProvider();
        logger.info("[AdvancedGuilds] Vault Economy успешно подключён: " + economy.getName());
        return true;
    }

    public boolean hasEnough(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.getBalance(player) >= amount;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        if (!hasEnough(player, amount)) return false;

        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            logger.warning("[AdvancedGuilds] Ошибка снятия средств у " + player.getName()
                    + ": " + response.errorMessage);
            return false;
        }
        return true;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null) return false;

        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            logger.warning("[AdvancedGuilds] Ошибка начисления средств игроку " + player.getName()
                    + ": " + response.errorMessage);
            return false;
        }
        return true;
    }

    public double getBalance(OfflinePlayer player) {
        if (economy == null) return 0.0;
        return economy.getBalance(player);
    }

    public String format(double amount) {
        if (economy == null) return String.format("%.2f", amount);
        return economy.format(amount);
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }
}