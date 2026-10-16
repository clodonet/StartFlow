package net.clodo.startflow;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AdminMenu implements Listener {
    private final StartFlowPlugin plugin;
    private final Messages messages;
    private final GuideSettingsLoader settings;
    private final StepTriggers triggers;
    private final Tasks tasks;
    private final AdminPlayersView playersView;
    private final AdminStepsView stepsView;
    private ReportMenu reportMenu;

    AdminMenu(StartFlowPlugin plugin, ProgressService progress, Messages messages, GuideSettingsLoader settings, StepTriggers triggers, Tasks tasks) {
        this.plugin = plugin;
        this.messages = messages;
        this.settings = settings;
        this.triggers = triggers;
        this.tasks = tasks;
        this.playersView = new AdminPlayersView(progress, messages, settings, tasks);
        this.stepsView = new AdminStepsView(progress, messages, settings, triggers, tasks);
    }

    void setReportMenu(ReportMenu reportMenu) {
        this.reportMenu = reportMenu;
    }

    void open(Player admin) {
        AdminView view = new AdminView(admin.getUniqueId(), AdminScreen.MAIN, 0, null, null, Set.of(), List.of(), List.of());
        Inventory inventory = Bukkit.createInventory(view, 27, messages.message(admin, "admin-menu-title", Map.of()));
        view.inventory = inventory;
        inventory.setItem(11, AdminPlayersView.playerHead(admin, messages.message(admin, "admin-menu-players", Map.of()), List.of(messages.message(admin, "admin-menu-players-lore", Map.of()))));
        inventory.setItem(12, AdminPlayersView.item(Material.SPYGLASS, messages.message(admin, "admin-menu-report", Map.of()), List.of(messages.message(admin, "admin-menu-report-lore", Map.of()))));
        inventory.setItem(13, AdminPlayersView.item(Material.CLOCK, messages.message(admin, "admin-menu-reload", Map.of()), List.of(messages.message(admin, "admin-menu-reload-lore", Map.of()))));
        inventory.setItem(15, AdminPlayersView.item(Material.BOOK, messages.message(admin, "admin-menu-about", Map.of()), List.of(messages.message(admin, "admin-menu-about-lore", Map.of()))));
        inventory.setItem(26, AdminPlayersView.item(Material.OAK_DOOR, messages.message(admin, "menu-close", Map.of()), List.of(messages.message(admin, "menu-close-lore", Map.of()))));
        admin.openInventory(inventory);
    }

    void openPlayers(Player admin, int page) {
        playersView.open(admin, page);
    }

    void openSteps(Player admin, UUID targetUuid, String targetName, int returnPage) {
        stepsView.open(admin, targetUuid, targetName, returnPage);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AdminView view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin) || !view.admin.equals(admin.getUniqueId())) return;
        if (!admin.hasPermission("startflow.admin")) {
            admin.closeInventory();
            messages.send(admin, "admin-no-permission", Map.of());
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        switch (view.screen) {
            case MAIN -> clickMain(admin, event.getRawSlot());
            case PLAYERS -> playersView.click(admin, view, event.getRawSlot(), this, stepsView);
            case STEPS -> stepsView.click(admin, view, event, playersView);
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AdminView)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void clickMain(Player admin, int slot) {
        if (slot == 11) {
            playersView.open(admin, 0);
        } else if (slot == 12 && reportMenu != null) {
            reportMenu.open(admin);
        } else if (slot == 13) {
            settings.reload(messages::reload).whenComplete((unused, error) -> tasks.global(() -> {
                if (error == null) triggers.refresh();
                tasks.player(admin, () -> messages.send(admin, error == null ? "reload-success" : "storage-error", Map.of()));
            }));
            admin.closeInventory();
        } else if (slot == 15) {
            messages.send(admin, "about", Map.of("version", plugin.getPluginMeta().getVersion()));
            admin.closeInventory();
        } else if (slot == 26) {
            admin.closeInventory();
        }
    }
}
