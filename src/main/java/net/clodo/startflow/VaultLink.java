package net.clodo.startflow;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

final class VaultLink {
    private final StartFlowPlugin plugin;
    private Object economy;
    private Method balanceMethod;
    private boolean available;

    VaultLink(StartFlowPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    void retry() {
        if (!available) {
            initialize();
        }
    }

    boolean isAvailable() {
        return available;
    }

    double balance(OfflinePlayer player) {
        if (!available || economy == null || balanceMethod == null) {
            return 0.0;
        }
        try {
            Object argument = balanceMethod.getParameterTypes()[0] == String.class ? player.getName() : player;
            Object result = balanceMethod.invoke(economy, argument);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
        } catch (Exception error) {
            plugin.getLogger().warning("Could not read Vault balance for " + player.getName() + ": " + Errors.concise(error));
        }
        return 0.0;
    }

    private void initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            plugin.getLogger().info("Vault not found; sale trigger is disabled.");
            return;
        }
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration == null) {
                plugin.getLogger().warning("Vault economy service is not registered; sale trigger is disabled.");
                return;
            }
            economy = registration.getProvider();
            if (economy == null) {
                plugin.getLogger().warning("Vault economy provider is null; sale trigger is disabled.");
                return;
            }
            try {
                balanceMethod = economyClass.getMethod("getBalance", OfflinePlayer.class);
            } catch (NoSuchMethodException ignored) {
                balanceMethod = economyClass.getMethod("getBalance", String.class);
            }
            available = true;
        } catch (Exception error) {
            plugin.getLogger().warning("Could not hook into Vault: " + Errors.concise(error) + "; sale trigger is disabled.");
            available = false;
        }
    }
}
