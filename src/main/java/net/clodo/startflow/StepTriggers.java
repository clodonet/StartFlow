package net.clodo.startflow;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import io.papermc.paper.event.player.PlayerPurchaseEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class StepTriggers implements Listener {
    private final StartFlowPlugin plugin;
    private final ProgressService progress;
    private final Supplier<GuideSettings> settings;
    private final VaultLink vault;
    private final ClaimLink claim;
    private final Tasks tasks;
    private final Map<UUID, Set<String>> completedCache = new ConcurrentHashMap<>();
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPayment = new ConcurrentHashMap<>();
    private volatile Map<String, List<Step>> triggersByType = Map.of();
    private Tasks.TaskRef balanceTask;

    StepTriggers(StartFlowPlugin plugin, ProgressService progress, Supplier<GuideSettings> settings, VaultLink vault, ClaimLink claim, Tasks tasks) {
        this.plugin = plugin;
        this.progress = progress;
        this.settings = settings;
        this.vault = vault;
        this.claim = claim;
        this.tasks = tasks;
        refresh();
        claim.onClaimCreated(this::onClaimCreated);
        balanceTask = tasks.globalRepeating(this::pollBalances, 100L, 100L);
    }

    void refresh() {
        Map<String, List<Step>> map = new HashMap<>();
        for (Step step : settings.get().steps()) if (step.enabled()) map.computeIfAbsent(step.trigger(), key -> new ArrayList<>()).add(step);
        this.triggersByType = Map.copyOf(map);
    }

    void invalidate(UUID uuid) {
        completedCache.remove(uuid);
        balances.remove(uuid);
        lastPayment.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            progress.getProgress(uuid).thenAccept(value -> {
                if (player.isOnline()) completedCache.put(uuid, new HashSet<>(value.completedSteps()));
            });
            if (vault.isAvailable()) balances.put(uuid, vault.balance(player));
        }
    }

    void close() {
        if (balanceTask != null) balanceTask.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getCause() == PlayerSetSpawnEvent.Cause.BED || event.getCause() == PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR) trigger(event.getPlayer(), "home");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPurchase(PlayerPurchaseEvent event) { trigger(event.getPlayer(), "trade"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) { trigger(event.getPlayer(), "trade"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        List<Step> steps = triggersByType.get("place");
        if (steps == null || steps.isEmpty()) return;
        Player player = event.getPlayer();
        Material placed = event.getBlockPlaced().getType();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.placeMaterial() == null || step.placeMaterial() == placed) complete(player, step.id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage().startsWith("/") ? event.getMessage().substring(1).trim() : event.getMessage().trim();
        String simpleLabel = StepCommandMatcher.commandLabel(raw);
        if (simpleLabel.equals("pay") || simpleLabel.equals("eco")) {
            lastPayment.put(player.getUniqueId(), System.currentTimeMillis());
            StepCommandMatcher.recordRecipient(simpleLabel, raw, lastPayment);
        }
        String label = raw.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (Bukkit.getCommandMap().getCommand(label) == null) return;
        List<Step> steps = triggersByType.get("command");
        if (steps == null || steps.isEmpty()) return;
        String lowerMessage = raw.toLowerCase(Locale.ROOT);
        String[] tokens = lowerMessage.split("\\s+");
        boolean rulesInvocation = Words.command(label).equals("kurallar") || (tokens.length >= 2 && Words.command(tokens[1]).equals("kurallar"));
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            String target = step.command().startsWith("/") ? step.command().substring(1).trim() : step.command().trim();
            if (rulesInvocation && !Words.command(target.split("\\s+", 2)[0].toLowerCase(Locale.ROOT)).equals("kurallar")) continue;
            if (StepCommandMatcher.matchesCommand(lowerMessage, label, target)) complete(player, step.id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String raw = event.getCommand().startsWith("/") ? event.getCommand().substring(1).trim() : event.getCommand().trim();
        String simpleLabel = StepCommandMatcher.commandLabel(raw);
        if (simpleLabel.equals("pay") || simpleLabel.equals("eco")) StepCommandMatcher.recordRecipient(simpleLabel, raw, lastPayment);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        List<Step> steps = triggersByType.get("region");
        if (steps == null || steps.isEmpty()) return;
        Player player = event.getPlayer();
        Set<String> completed = completedCache.get(player.getUniqueId());
        if (completed != null && allCompleted(steps, completed)) return;
        String worldName = to.getWorld().getName();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.region() != null && step.region().contains(worldName, to.getBlockX(), to.getBlockY(), to.getBlockZ())) complete(player, step.id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) scheduleInventoryCheck(player); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) { if (event.getWhoClicked() instanceof Player player) scheduleInventoryCheck(player); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        progress.getProgress(uuid).thenAccept(value -> completedCache.put(uuid, new HashSet<>(value.completedSteps())));
        if (vault.isAvailable()) balances.put(uuid, vault.balance(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { invalidate(event.getPlayer().getUniqueId()); }

    private void scheduleInventoryCheck(Player player) {
        List<Step> steps = triggersByType.get("item");
        if (steps == null || steps.isEmpty()) return;
        tasks.player(player, () -> checkItems(player, steps));
    }

    private void checkItems(Player player, List<Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.itemMaterial() != null && countItems(player, step.itemMaterial()) >= step.itemAmount()) complete(player, step.id());
        }
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) if (item != null && item.getType() == material) count += item.getAmount();
        return count;
    }

    private void pollBalances() {
        if (!vault.isAvailable()) return;
        List<Step> steps = triggersByType.get("sale");
        if (steps == null || steps.isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            tasks.player(player, () -> checkPlayerBalance(player, steps));
        }
    }

    private void checkPlayerBalance(Player player, List<Step> steps) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        Set<String> completed = completedCache.get(uuid);
        if (completed != null && allCompleted(steps, completed)) return;
        double current = vault.balance(player);
        Double previous = balances.put(uuid, current);
        if (previous == null) return;
        double gain = current - previous;
        if (gain <= 0.0) return;
        long lastPay = lastPayment.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() - lastPay < 3000L) return;
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (gain >= step.minGain()) complete(player, step.id());
        }
    }

    private void onClaimCreated(UUID owner) {
        Player player = Bukkit.getPlayer(owner);
        if (player != null && player.isOnline()) trigger(player, "claim");
    }

    private void trigger(Player player, String triggerType) {
        List<Step> steps = triggersByType.get(triggerType);
        if (steps == null || steps.isEmpty()) return;
        for (int i = 0; i < steps.size(); i++) complete(player, steps.get(i).id());
    }

    private void complete(Player player, String stepId) {
        UUID uuid = player.getUniqueId();
        Set<String> completed = completedCache.get(uuid);
        if (completed != null && completed.contains(stepId)) return;
        progress.completeStep(uuid, stepId, false).thenAccept(inserted -> {
            if (inserted) completedCache.computeIfAbsent(uuid, key -> new HashSet<>()).add(stepId);
        });
    }

    private boolean allCompleted(List<Step> steps, Set<String> completed) {
        for (int i = 0; i < steps.size(); i++) if (!completed.contains(steps.get(i).id())) return false;
        return true;
    }
}
