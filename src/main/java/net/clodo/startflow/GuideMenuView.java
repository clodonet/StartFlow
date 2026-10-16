package net.clodo.startflow;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class GuideMenuView implements InventoryHolder {
    private final UUID player;
    private final Map<Integer, Step> stepsBySlot = new HashMap<>();
    private String currentStepId;
    private Inventory inventory;

    GuideMenuView(UUID player) {
        this.player = player;
    }

    UUID player() {
        return player;
    }

    Step stepAt(int slot) {
        return stepsBySlot.get(slot);
    }

    boolean isCurrent(Step step) {
        return step != null && step.id().equals(currentStepId);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void build(Player target, GuideSettings settings, ProgressService.Progress progress, Messages messages) {
        inventory = Bukkit.createInventory(this, 45, messages.message(target, "menu-title", Map.of()));

        Set<String> completed = progress.completedSteps();
        Step currentStep = settings.firstIncomplete(completed);
        currentStepId = currentStep != null ? currentStep.id() : null;

        int total = (int) settings.steps().stream().filter(Step::enabled).count();
        int completedCount = (int) settings.steps().stream().filter(step -> step.enabled() && completed.contains(step.id())).count();

        for (int slot = 0; slot < 45; slot++) {
            inventory.setItem(slot, framePane(slot));
        }

        inventory.setItem(4, headerItem(target, completedCount, total, messages));

        List<Step> stepList = settings.steps();
        int perRow = stepList.size() <= 5 ? 5 : 9;
        int firstRowCount = Math.min(stepList.size(), perRow);
        if (firstRowCount > 0) {
            layoutRow(target, 18, stepList.subList(0, firstRowCount), completed, messages);
        }
        if (stepList.size() > perRow) {
            int secondRowCount = Math.min(stepList.size() - perRow, 9);
            layoutRow(target, 27, stepList.subList(perRow, perRow + secondRowCount), completed, messages);
        }

        inventory.setItem(36, item(Material.BARRIER, messages.message(target, "menu-skip", Map.of()), List.of(messages.message(target, "menu-skip-lore", Map.of()))));
        inventory.setItem(40, item(Material.BOOK, messages.message(target, "menu-info", Map.of()), List.of(messages.message(target, "menu-info-lore", Map.of()))));
        inventory.setItem(44, item(Material.OAK_DOOR, messages.message(target, "menu-close", Map.of()), List.of(messages.message(target, "menu-close-lore", Map.of()))));
    }

    private void layoutRow(Player target, int startSlot, List<Step> rowSteps, Set<String> completed, Messages messages) {
        int count = rowSteps.size();
        if (count == 0) return;
        boolean withConnectors = (2 * count - 1 <= 9);
        int span = withConnectors ? (2 * count - 1) : count;
        int offset = (9 - span) / 2;

        for (int i = 0; i < count; i++) {
            Step step = rowSteps.get(i);
            int slot = startSlot + offset + (withConnectors ? (2 * i) : i);
            inventory.setItem(slot, stepItem(target, step, completed, messages));
            stepsBySlot.put(slot, step);

            if (withConnectors && i < count - 1) {
                int connectorSlot = slot + 1;
                boolean stepDone = step.enabled() && completed.contains(step.id());
                inventory.setItem(connectorSlot, connectorPane(stepDone));
            }
        }
    }

    private ItemStack framePane(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        Material material = ((row + col) % 2 == 0) ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        return item(material, Component.empty(), List.of());
    }

    private ItemStack connectorPane(boolean completed) {
        Material material = completed ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        return item(material, Component.empty(), List.of());
    }

    private ItemStack headerItem(Player target, int completed, int total, Messages messages) {
        boolean allComplete = total > 0 && completed >= total;
        Material material = allComplete ? Material.NETHER_STAR : Material.COMPASS;

        String nameKey = allComplete ? "menu-header-complete" : "menu-progress";
        Component name = messages.message(target, nameKey, Map.of(
                "current", Integer.toString(completed),
                "total", Integer.toString(total)
        ));

        int barLength = 10;
        int filled = total == 0 ? 0 : Math.min(barLength, Math.round((float) completed * barLength / total));
        int percent = total == 0 ? 0 : Math.min(100, Math.round((float) completed * 100 / total));

        StringBuilder bar = new StringBuilder();
        bar.append("<green>");
        for (int i = 0; i < filled; i++) bar.append('▰');
        bar.append("</green><dark_gray>");
        for (int i = filled; i < barLength; i++) bar.append('▱');
        bar.append("</dark_gray>");

        Component barComponent = messages.message(target, "menu-header-bar",
                Map.of("percent", Integer.toString(percent)),
                Map.of("bar", bar.toString()));

        String statusKey = allComplete ? "menu-header-status-complete" : (completed == 0 ? "menu-header-status-start" : "menu-header-status-progress");
        Component status = messages.message(target, statusKey, Map.of());

        List<Component> lore = List.of(barComponent, status);
        ItemStack stack = item(material, name, lore);
        if (allComplete) {
            ItemMeta meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack stepItem(Player target, Step step, Set<String> completed, Messages messages) {
        if (!step.enabled()) {
            List<Component> lore = new ArrayList<>(wrap(target, messages.text(target, step.descriptionKey()), messages));
            lore.add(messages.message(target, "menu-status-disabled", Map.of()));
            return item(Material.BARRIER,
                    messages.message(target, "menu-step-disabled", Map.of("step", messages.text(target, step.displayNameKey()))),
                    lore);
        }

        boolean done = completed.contains(step.id());
        boolean current = isCurrent(step);
        List<Component> lore = new ArrayList<>();

        if (current) {
            lore.add(messages.message(target, "menu-status-current", Map.of()));
            lore.addAll(wrap(target, messages.text(target, step.descriptionKey()), messages));
            lore.add(messages.message(target, "menu-step-hint-current", Map.of("hint", messages.text(target, step.hintKey()))));
        } else if (done) {
            lore.addAll(wrap(target, messages.text(target, step.descriptionKey()), messages));
            lore.add(messages.message(target, "menu-status-complete", Map.of()));
        } else {
            lore.addAll(wrap(target, messages.text(target, step.descriptionKey()), messages));
            lore.add(messages.message(target, "menu-status-locked", Map.of()));
        }

        String nameKey = done ? "menu-step-complete" : current ? "menu-step-current" : "menu-step-locked";
        ItemStack stack = item(step.icon(),
                messages.message(target, nameKey, Map.of("step", messages.text(target, step.displayNameKey()))),
                lore);
        if (current) {
            ItemMeta meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<Component> wrap(Player target, String text, Messages messages) {
        if (text == null || text.isBlank()) return List.of();
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= 40) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(messages.message(target, "menu-step-lore", Map.of("description", line)));
        }
        return components;
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(new ArrayList<>(lore));
        stack.setItemMeta(meta);
        return stack;
    }
}
