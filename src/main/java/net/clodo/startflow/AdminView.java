package net.clodo.startflow;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class AdminView implements InventoryHolder {
    final UUID admin;
    final AdminScreen screen;
    final int page;
    final UUID targetPlayer;
    final String targetName;
    final Set<String> completedSteps;
    final List<UUID> playerUuids;
    final List<String> playerNames;
    Inventory inventory;

    AdminView(UUID admin, AdminScreen screen, int page, UUID targetPlayer, String targetName,
              Set<String> completedSteps, List<UUID> playerUuids, List<String> playerNames) {
        this.admin = admin;
        this.screen = screen;
        this.page = page;
        this.targetPlayer = targetPlayer;
        this.targetName = targetName;
        this.completedSteps = completedSteps;
        this.playerUuids = playerUuids;
        this.playerNames = playerNames;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
