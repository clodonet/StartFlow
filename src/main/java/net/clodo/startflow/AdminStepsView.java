package net.clodo.startflow;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class AdminStepsView {
    private final ProgressService progress;
    private final Messages messages;
    private final GuideSettingsLoader settings;
    private final StepTriggers triggers;
    private final Tasks tasks;
    private final Map<UUID, Long> resetConfirmations = new ConcurrentHashMap<>();

    AdminStepsView(ProgressService progress, Messages messages, GuideSettingsLoader settings, StepTriggers triggers, Tasks tasks) {
        this.progress = progress;
        this.messages = messages;
        this.settings = settings;
        this.triggers = triggers;
        this.tasks = tasks;
    }

    void open(Player admin, UUID targetUuid, String targetName, int returnPage) {
        progress.getProgress(targetUuid).whenComplete((progressValue, error) -> tasks.player(admin, () -> {
            if (!admin.hasPermission("startflow.admin")) return;
            if (error != null) {
                messages.send(admin, "storage-error", Map.of());
                return;
            }
            render(admin, targetUuid, targetName, returnPage, progressValue);
        }));
    }

    private void render(Player admin, UUID targetUuid, String targetName, int returnPage, ProgressService.Progress progressValue) {
        AdminView view = new AdminView(admin.getUniqueId(), AdminScreen.STEPS, returnPage, targetUuid, targetName, progressValue.completedSteps(), List.of(), List.of());
        Inventory inventory = Bukkit.createInventory(view, 54, messages.message(admin, "admin-steps-title", Map.of("player", targetName)));
        view.inventory = inventory;
        List<Step> enabledSteps = settings.settings().steps().stream().filter(Step::enabled).toList();
        int count = Math.min(45, enabledSteps.size());
        for (int i = 0; i < count; i++) {
            Step step = enabledSteps.get(i);
            boolean done = progressValue.completedSteps().contains(step.id());
            ItemStack stack = new ItemStack(step.icon());
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(messages.message(admin, done ? "menu-step-complete" : "admin-step-incomplete-name", Map.of("step", messages.text(admin, step.displayNameKey()))));
            meta.lore(List.of(
                    messages.message(admin, "menu-step-lore", Map.of("description", messages.text(admin, step.descriptionKey()))),
                    messages.message(admin, done ? "menu-status-complete" : "admin-status-incomplete", Map.of()),
                    messages.message(admin, done ? "admin-step-click-reset" : "admin-step-click-complete", Map.of())
            ));
            if (done) {
                meta.setEnchantmentGlintOverride(true);
            }
            stack.setItemMeta(meta);
            inventory.setItem(i, stack);
        }
        inventory.setItem(45, AdminPlayersView.item(Material.OAK_DOOR, messages.message(admin, "admin-back", Map.of()), List.of(messages.message(admin, "admin-back-lore", Map.of()))));
        inventory.setItem(49, targetHead(targetUuid, targetName, admin, progressValue, enabledSteps.size()));
        inventory.setItem(53, AdminPlayersView.item(Material.BARRIER, messages.message(admin, "admin-reset-all", Map.of()), List.of(messages.message(admin, "admin-reset-all-lore", Map.of()))));
        admin.openInventory(inventory);
    }

    void click(Player admin, AdminView view, InventoryClickEvent event, AdminPlayersView playersView) {
        if (!event.isLeftClick()) return;
        int slot = event.getRawSlot();
        if (slot == 45) {
            playersView.open(admin, view.page);
            return;
        }
        if (slot == 53) {
            resetAll(admin, view.targetPlayer, view.targetName, view.page);
            return;
        }
        List<Step> enabledSteps = settings.settings().steps().stream().filter(Step::enabled).toList();
        if (slot < 0 || slot >= enabledSteps.size()) return;
        Step step = enabledSteps.get(slot);
        boolean done = view.completedSteps.contains(step.id());
        if (done) {
            progress.resetStep(view.targetPlayer, step.id()).whenComplete((unused, error) -> {
                triggers.invalidate(view.targetPlayer);
                tasks.player(admin, () -> {
                    messages.send(admin, error == null ? "admin-step-reset" : "storage-error", Map.of("player", view.targetName, "step", step.id()));
                    if (error == null) open(admin, view.targetPlayer, view.targetName, view.page);
                });
            });
        } else {
            progress.completeStep(view.targetPlayer, step.id()).whenComplete((inserted, error) -> tasks.player(admin, () -> {
                messages.send(admin, error == null ? "admin-step-complete" : "storage-error", Map.of("player", view.targetName, "step", step.id()));
                if (error == null) open(admin, view.targetPlayer, view.targetName, view.page);
            }));
        }
    }

    private void resetAll(Player admin, UUID targetUuid, String targetName, int returnPage) {
        long now = System.currentTimeMillis();
        Long previous = resetConfirmations.put(admin.getUniqueId(), now);
        if (previous == null || now - previous > 5000) {
            messages.send(admin, "admin-reset-confirm", Map.of("player", targetName));
            return;
        }
        resetConfirmations.remove(admin.getUniqueId());
        progress.reset(targetUuid).whenComplete((unused, error) -> {
            triggers.invalidate(targetUuid);
            tasks.player(admin, () -> {
                messages.send(admin, error == null ? "admin-reset-success" : "storage-error", Map.of("player", targetName));
                if (error == null) open(admin, targetUuid, targetName, returnPage);
            });
        });
    }

    private ItemStack targetHead(UUID targetUuid, String targetName, Player admin, ProgressService.Progress progressValue, int totalSteps) {
        Player target = Bukkit.getPlayer(targetUuid);
        int completed = (int) settings.settings().steps().stream().filter(s -> s.enabled() && progressValue.completedSteps().contains(s.id())).count();
        boolean skipped = progressValue.skipped();
        String statusKey = skipped ? "admin-player-status-skipped" : (totalSteps > 0 && completed >= totalSteps ? "admin-player-status-completed" : "admin-player-status-active");
        List<Component> lore = List.of(
                messages.message(admin, "admin-player-progress", Map.of("current", Integer.toString(completed), "total", Integer.toString(totalSteps))),
                messages.message(admin, statusKey, Map.of())
        );
        if (target != null) return AdminPlayersView.playerHead(target, messages.message(admin, "admin-player-name", Map.of("player", targetName)), lore);
        ItemStack head = AdminPlayersView.item(Material.PLAYER_HEAD, messages.message(admin, "admin-player-name", Map.of("player", targetName)), lore);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetUuid));
        head.setItemMeta(meta);
        return head;
    }
}
