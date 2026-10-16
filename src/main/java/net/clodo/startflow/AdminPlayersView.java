package net.clodo.startflow;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class AdminPlayersView {
    private final ProgressService progress;
    private final Messages messages;
    private final GuideSettingsLoader settings;
    private final Tasks tasks;

    AdminPlayersView(ProgressService progress, Messages messages, GuideSettingsLoader settings, Tasks tasks) {
        this.progress = progress;
        this.messages = messages;
        this.settings = settings;
        this.tasks = tasks;
    }

    void open(Player admin, int page) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int startIndex = page * 45;
        if (startIndex >= online.size() && page > 0) {
            open(admin, page - 1);
            return;
        }
        int endIndex = Math.min(startIndex + 45, online.size());
        List<Player> slice = online.subList(startIndex, endIndex);
        Map<UUID, ProgressService.Progress> values = new ConcurrentHashMap<>();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Player target : slice) {
            futures.add(progress.getProgress(target.getUniqueId())
                    .exceptionally(err -> new ProgressService.Progress(Set.of(), false))
                    .thenAccept(p -> values.put(target.getUniqueId(), p)));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((unused, error) -> tasks.player(admin, () -> {
            if (!admin.hasPermission("startflow.admin")) return;
            render(admin, page, slice, values, online.size());
        }));
    }

    private void render(Player admin, int page, List<Player> slice, Map<UUID, ProgressService.Progress> values, int totalPlayers) {
        List<UUID> uuids = slice.stream().map(Player::getUniqueId).toList();
        List<String> names = slice.stream().map(Player::getName).toList();
        AdminView view = new AdminView(admin.getUniqueId(), AdminScreen.PLAYERS, page, null, null, Set.of(), uuids, names);
        Inventory inventory = Bukkit.createInventory(view, 54, messages.message(admin, "admin-players-title", Map.of("page", Integer.toString(page + 1))));
        view.inventory = inventory;
        int totalSteps = settings.settings().enabledStepIds().size();
        for (int i = 0; i < slice.size(); i++) {
            Player target = slice.get(i);
            ProgressService.Progress p = values.get(target.getUniqueId());
            int completed = p == null ? 0 : (int) p.completedSteps().stream().filter(settings.settings().enabledStepIds()::contains).count();
            boolean skipped = p != null && p.skipped();
            String statusKey = skipped ? "admin-player-status-skipped" : (totalSteps > 0 && completed >= totalSteps ? "admin-player-status-completed" : "admin-player-status-active");
            inventory.setItem(i, playerHead(target, messages.message(admin, "admin-player-name", Map.of("player", target.getName())), List.of(
                    messages.message(admin, "admin-player-progress", Map.of("current", Integer.toString(completed), "total", Integer.toString(totalSteps))),
                    messages.message(admin, statusKey, Map.of()),
                    messages.message(admin, "admin-player-click-hint", Map.of())
            )));
        }
        if (page > 0) {
            inventory.setItem(45, item(Material.ARROW, messages.message(admin, "admin-page-previous", Map.of()), List.of(messages.message(admin, "admin-page-previous-lore", Map.of()))));
        }
        inventory.setItem(49, item(Material.OAK_DOOR, messages.message(admin, "admin-back", Map.of()), List.of(messages.message(admin, "admin-back-lore", Map.of()))));
        if ((page + 1) * 45 < totalPlayers) {
            inventory.setItem(53, item(Material.ARROW, messages.message(admin, "admin-page-next", Map.of()), List.of(messages.message(admin, "admin-page-next-lore", Map.of()))));
        }
        admin.openInventory(inventory);
    }

    void click(Player admin, AdminView view, int slot, AdminMenu menu, AdminStepsView stepsView) {
        if (slot == 45 && view.page > 0) {
            open(admin, view.page - 1);
            return;
        }
        if (slot == 49) {
            menu.open(admin);
            return;
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (slot == 53 && (view.page + 1) * 45 < online.size()) {
            open(admin, view.page + 1);
            return;
        }
        if (slot >= 0 && slot < view.playerUuids.size()) {
            stepsView.open(admin, view.playerUuids.get(slot), view.playerNames.get(slot), view.page);
        }
    }

    static ItemStack playerHead(Player player, Component name, List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setPlayerProfile(player.getPlayerProfile());
        meta.displayName(name);
        meta.lore(new ArrayList<>(lore));
        head.setItemMeta(meta);
        return head;
    }

    static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(new ArrayList<>(lore));
        stack.setItemMeta(meta);
        return stack;
    }
}
