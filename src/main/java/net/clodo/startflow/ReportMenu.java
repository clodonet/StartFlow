package net.clodo.startflow;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class ReportMenu implements Listener {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final StartFlowPlugin plugin;
    private final Storage storage;
    private final Messages messages;
    private final GuideSettingsLoader settings;
    private final Tasks tasks;
    private final Consumer<Player> backAction;

    ReportMenu(StartFlowPlugin plugin, Storage storage, Messages messages, GuideSettingsLoader settings, Tasks tasks, Consumer<Player> backAction) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.settings = settings;
        this.tasks = tasks;
        this.backAction = backAction;
    }

    void open(Player admin) {
        if (!admin.hasPermission("startflow.admin")) {
            messages.send(admin, "admin-no-permission", Map.of());
            return;
        }
        List<String> enabledStepIds = List.copyOf(settings.settings().enabledStepIds());
        storage.funnelReport(enabledStepIds).whenComplete((report, error) -> tasks.player(admin, () -> {
            if (!admin.hasPermission("startflow.admin")) return;
            if (error != null || report == null) {
                messages.send(admin, "storage-error", Map.of());
                return;
            }
            render(admin, report);
        }));
    }

    private void render(Player admin, Storage.FunnelReport report) {
        ReportView view = new ReportView(admin.getUniqueId());
        Inventory inventory = Bukkit.createInventory(view, 54, messages.message(admin, "report-menu-title", Map.of()));
        view.inventory = inventory;

        List<Storage.FunnelStep> steps = report.steps();
        int stepCount = Math.min(45, steps.size());
        for (int i = 0; i < stepCount; i++) {
            Storage.FunnelStep fs = steps.get(i);
            Step step = settings.settings().step(fs.id());
            Material icon = step != null ? step.icon() : Material.PAPER;
            String stepName = step != null ? messages.text(admin, step.displayNameKey()) : fs.id();

            boolean drop = fs.id().equals(report.biggestDropStep());
            ItemStack stack = new ItemStack(icon);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(messages.message(admin, "report-step-item-name", Map.of("step", stepName)));

            List<Component> lore = new ArrayList<>();
            lore.add(messages.message(admin, "report-step-item-bar",
                    Map.of("percent", Integer.toString(fs.percent())),
                    Map.of("bar", bar(fs.completed(), report.totalPlayers()))));
            lore.add(messages.message(admin, "report-step-item-completed", Map.of(
                    "completed", Integer.toString(fs.completed()),
                    "total", Integer.toString(report.totalPlayers()))));
            lore.add(messages.message(admin, "report-step-item-stuck", Map.of("stuck", Integer.toString(fs.stuck()))));
            if (drop) {
                lore.add(messages.message(admin, "report-step-drop-warning", Map.of()));
                meta.setEnchantmentGlintOverride(true);
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
            inventory.setItem(i, stack);
        }

        inventory.setItem(45, item(Material.OAK_DOOR, messages.message(admin, "admin-back", Map.of()), List.of(messages.message(admin, "admin-back-lore", Map.of()))));

        inventory.setItem(49, item(Material.NETHER_STAR, messages.message(admin, "report-header-name", Map.of()), List.of(
                messages.message(admin, "report-header-total", Map.of("total", Integer.toString(report.totalPlayers()))),
                messages.message(admin, "report-header-started", Map.of("started", Integer.toString(report.started()))),
                messages.message(admin, "report-header-completed", Map.of(
                        "completed", Integer.toString(report.completed()),
                        "percent", Integer.toString(report.completionPercent()))),
                messages.message(admin, "report-header-skipped", Map.of("skipped", Integer.toString(report.skipped()))),
                messages.message(admin, "report-header-bar",
                        Map.of("percent", Integer.toString(report.completionPercent())),
                        Map.of("bar", bar(report.completed(), report.totalPlayers())))
        )));

        inventory.setItem(53, item(Material.PAPER, messages.message(admin, "report-menu-csv", Map.of()), List.of(messages.message(admin, "report-menu-csv-lore", Map.of()))));
        admin.openInventory(inventory);
    }

    void exportCsv(CommandSender sender) {
        if (!sender.hasPermission("startflow.admin")) {
            messages.send(sender, "admin-no-permission", Map.of());
            return;
        }
        List<String> enabledStepIds = List.copyOf(settings.settings().enabledStepIds());
        storage.funnelReport(enabledStepIds).whenComplete((report, error) -> {
            if (error != null || report == null) {
                tasks.sender(sender, () -> messages.send(sender, "storage-error", Map.of()));
                return;
            }
            String timestamp = DATE_FORMAT.format(Instant.now());
            Path reportsDir = plugin.getDataFolder().toPath().resolve("reports");
            Path targetFile = reportsDir.resolve("funnel-" + timestamp + ".csv");
            try {
                Files.createDirectories(reportsDir);
                StringBuilder builder = new StringBuilder();
                builder.append("step,completed,percent,stuck\n");
                for (Storage.FunnelStep fs : report.steps()) {
                    builder.append(fs.id()).append(',')
                            .append(fs.completed()).append(',')
                            .append(fs.percent()).append("%,")
                            .append(fs.stuck()).append('\n');
                }
                builder.append('\n');
                builder.append("total_players,").append(report.totalPlayers()).append('\n');
                builder.append("started,").append(report.started()).append('\n');
                builder.append("completed,").append(report.completed()).append('\n');
                builder.append("skipped,").append(report.skipped()).append('\n');
                builder.append("completion_percent,").append(report.completionPercent()).append("%\n");
                builder.append("biggest_drop,").append(report.biggestDropStep() == null ? "none" : report.biggestDropStep()).append('\n');

                Files.writeString(targetFile, builder.toString(), StandardCharsets.UTF_8);
                tasks.sender(sender, () -> messages.send(sender, "report-csv-success", Map.of("file", targetFile.getFileName().toString())));
            } catch (IOException writeError) {
                plugin.getLogger().warning("Could not export funnel report: " + Errors.concise(writeError));
                tasks.sender(sender, () -> messages.send(sender, "storage-error", Map.of()));
            }
        });
    }

    void sendSummary(CommandSender sender) {
        if (!sender.hasPermission("startflow.admin")) {
            messages.send(sender, "admin-no-permission", Map.of());
            return;
        }
        List<String> enabledStepIds = List.copyOf(settings.settings().enabledStepIds());
        storage.funnelReport(enabledStepIds).whenComplete((report, error) -> tasks.sender(sender, () -> {
            if (!sender.hasPermission("startflow.admin")) return;
            if (error != null || report == null) {
                messages.send(sender, "storage-error", Map.of());
                return;
            }
            String lang = messages.language(sender);
            messages.send(sender, "report-summary-title", Map.of());
            messages.send(sender, "report-summary-totals", Map.of(
                    "total", Integer.toString(report.totalPlayers()),
                    "started", Integer.toString(report.started()),
                    "completed", Integer.toString(report.completed()),
                    "percent", Integer.toString(report.completionPercent()),
                    "skipped", Integer.toString(report.skipped())
            ));
            for (Storage.FunnelStep fs : report.steps()) {
                Step step = settings.settings().step(fs.id());
                String name = step != null ? messages.text(lang, step.displayNameKey()) : fs.id();
                messages.send(sender, "report-summary-step", Map.of(
                        "step", name,
                        "completed", Integer.toString(fs.completed()),
                        "percent", Integer.toString(fs.percent()),
                        "stuck", Integer.toString(fs.stuck())
                ));
            }
            if (report.biggestDropStep() != null) {
                Step step = settings.settings().step(report.biggestDropStep());
                String name = step != null ? messages.text(lang, step.displayNameKey()) : report.biggestDropStep();
                int stuckCount = report.steps().stream().filter(s -> s.id().equals(report.biggestDropStep())).findFirst().map(Storage.FunnelStep::stuck).orElse(0);
                messages.send(sender, "report-summary-drop", Map.of("step", name, "stuck", Integer.toString(stuckCount)));
            } else {
                messages.send(sender, "report-summary-no-drop", Map.of());
            }
        }));
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ReportView view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin) || !view.admin.equals(admin.getUniqueId())) return;
        if (!admin.hasPermission("startflow.admin")) {
            admin.closeInventory();
            messages.send(admin, "admin-no-permission", Map.of());
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (slot == 45) {
            backAction.accept(admin);
        } else if (slot == 53) {
            exportCsv(admin);
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ReportView)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private String bar(int completed, int total) {
        int barLength = 10;
        int filled = total == 0 ? 0 : Math.min(barLength, Math.round((float) completed * barLength / total));
        StringBuilder builder = new StringBuilder();
        builder.append("<green>");
        for (int i = 0; i < filled; i++) builder.append('▰');
        builder.append("</green><dark_gray>");
        for (int i = filled; i < barLength; i++) builder.append('▱');
        builder.append("</dark_gray>");
        return builder.toString();
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(new ArrayList<>(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private static final class ReportView implements InventoryHolder {
        private final UUID admin;
        private Inventory inventory;

        private ReportView(UUID admin) {
            this.admin = admin;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
