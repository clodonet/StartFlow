package net.clodo.startflow;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class StepParser {
    private final StartFlowPlugin plugin;
    private final VaultLink vault;
    private final ClaimLink claim;

    StepParser(StartFlowPlugin plugin, VaultLink vault, ClaimLink claim) {
        this.plugin = plugin;
        this.vault = vault;
        this.claim = claim;
    }

    Step buildStep(Map<?, ?> raw, String id) {
        String displayNameKey = optional(raw, "display-name-key", "step." + id + ".name");
        String descriptionKey = optional(raw, "description-key", "step." + id + ".description");
        String hintKey = optional(raw, "hint-key", "step." + id + ".hint");
        Material icon = icon(raw, id);
        List<String> rewards = parseRewards(raw.get("rewards"), id);
        String rewardMessageKey = optional(raw, "reward-message-key", optional(raw, "reward-message", "reward." + id + ".message"));
        String trigger = optional(raw, "trigger", "").toLowerCase(Locale.ROOT);
        return switch (trigger) {
            case "home" -> new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, 0.0, null, rewards, rewardMessageKey);
            case "command" -> parseCommand(raw, id, displayNameKey, descriptionKey, hintKey, trigger, icon, rewards, rewardMessageKey);
            case "region" -> parseRegion(raw, id, displayNameKey, descriptionKey, hintKey, trigger, icon, rewards, rewardMessageKey);
            case "item" -> parseItem(raw, id, displayNameKey, descriptionKey, hintKey, trigger, icon, rewards, rewardMessageKey);
            case "sale" -> vault.isAvailable() ? parseSale(raw, id, displayNameKey, descriptionKey, hintKey, trigger, icon, rewards, rewardMessageKey) : resolveFallback(raw, id, displayNameKey, descriptionKey, hintKey, trigger, icon, rewards, rewardMessageKey);
            case "claim" -> claim.isAvailable() ? new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, 0.0, null, rewards, rewardMessageKey) : resolveFallback(raw, id, displayNameKey, descriptionKey, hintKey, trigger, icon, rewards, rewardMessageKey);
            case "trade" -> new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, 0.0, null, rewards, rewardMessageKey);
            case "place" -> parsePlace(raw, id, displayNameKey, descriptionKey, hintKey, trigger, icon, rewards, rewardMessageKey);
            default -> {
                plugin.getLogger().warning("Step " + id + " has unknown trigger: " + trigger + ". Disabling step.");
                yield Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
            }
        };
    }

    private Step parseCommand(Map<?, ?> raw, String id, String displayNameKey, String descriptionKey, String hintKey, String trigger, Material icon, List<String> rewards, String rewardMessageKey) {
        Object value = raw.get("command");
        if (!(value instanceof String command) || command.isBlank()) {
            plugin.getLogger().warning("Step " + id + " has invalid command parameter. Disabling step.");
            return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
        }
        return new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, command.trim(), null, null, 0, 0.0, null, rewards, rewardMessageKey);
    }

    private Step parseRegion(Map<?, ?> raw, String id, String displayNameKey, String descriptionKey, String hintKey, String trigger, Material icon, List<String> rewards, String rewardMessageKey) {
        Object worldVal = raw.get("world");
        List<Integer> min = coordinates(raw.get("min"));
        List<Integer> max = coordinates(raw.get("max"));
        if (!(worldVal instanceof String world) || world.isBlank() || min == null || max == null) {
            plugin.getLogger().warning("Step " + id + " has invalid region parameters. Disabling step.");
            return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
        }
        Step.RegionBounds bounds = new Step.RegionBounds(world.trim(),
                Math.min(min.get(0), max.get(0)), Math.min(min.get(1), max.get(1)), Math.min(min.get(2), max.get(2)),
                Math.max(min.get(0), max.get(0)), Math.max(min.get(1), max.get(1)), Math.max(min.get(2), max.get(2)));
        return new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", bounds, null, 0, 0.0, null, rewards, rewardMessageKey);
    }

    private Step parseItem(Map<?, ?> raw, String id, String displayNameKey, String descriptionKey, String hintKey, String trigger, Material icon, List<String> rewards, String rewardMessageKey) {
        Material material = raw.get("material") instanceof String name ? Material.matchMaterial(name.trim()) : null;
        int amount = raw.get("amount") instanceof Number number ? number.intValue() : 0;
        if (material == null || !material.isItem() || amount < 1) {
            plugin.getLogger().warning("Step " + id + " has invalid item parameters. Disabling step.");
            return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
        }
        return new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, material, amount, 0.0, null, rewards, rewardMessageKey);
    }

    private Step parseSale(Map<?, ?> raw, String id, String displayNameKey, String descriptionKey, String hintKey, String trigger, Material icon, List<String> rewards, String rewardMessageKey) {
        if (!vault.isAvailable()) return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
        Object gainVal = raw.get("min-gain");
        double minGain = 1.0;
        if (gainVal instanceof Number number) {
            minGain = number.doubleValue();
        } else if (gainVal instanceof String text) {
            try {
                minGain = Double.parseDouble(text.trim());
            } catch (NumberFormatException error) {
                plugin.getLogger().warning("Step " + id + " has invalid min-gain: " + text + ". Disabling step.");
                return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
            }
        }
        if (minGain <= 0.0) {
            plugin.getLogger().warning("Step " + id + " min-gain must be greater than zero. Disabling step.");
            return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
        }
        return new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, minGain, null, rewards, rewardMessageKey);
    }

    private Step parsePlace(Map<?, ?> raw, String id, String displayNameKey, String descriptionKey, String hintKey, String trigger, Material icon, List<String> rewards, String rewardMessageKey) {
        Material material = parsePlaceMaterial(raw.get("material"));
        if (material == null) {
            plugin.getLogger().warning("Step " + id + " has invalid place parameters. Disabling step.");
            return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
        }
        return new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, 0.0, material, rewards, rewardMessageKey);
    }

    private Step resolveFallback(Map<?, ?> raw, String id, String displayNameKey, String descriptionKey, String hintKey, String trigger, Material icon, List<String> rewards, String rewardMessageKey) {
        Object fallbackObj = raw.get("fallback");
        if (fallbackObj == null) {
            return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
        }
        if (!(fallbackObj instanceof Map<?, ?> fallbackMap)) {
            plugin.getLogger().warning("Step " + id + " has invalid fallback configuration. Disabling step.");
            return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
        }
        try {
            Step step = parseFallback(fallbackMap, id, displayNameKey, descriptionKey, hintKey, icon, rewards, rewardMessageKey);
            if (step == null) {
                plugin.getLogger().warning("Step " + id + " has invalid fallback configuration. Disabling step.");
                return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
            }
            return step;
        } catch (Exception error) {
            plugin.getLogger().warning("Step " + id + " has invalid fallback configuration. Disabling step.");
            return Step.disabled(id, displayNameKey, descriptionKey, hintKey, trigger, icon);
        }
    }

    private Step parseFallback(Map<?, ?> raw, String id, String defaultNameKey, String defaultDescKey, String defaultHintKey, Material defaultIcon, List<String> defaultRewards, String defaultRewardMessageKey) {
        String displayNameKey = optional(raw, "display-name-key", defaultNameKey);
        String descriptionKey = optional(raw, "description-key", defaultDescKey);
        String hintKey = optional(raw, "hint-key", defaultHintKey);
        List<String> rewards = raw.containsKey("rewards") ? parseRewards(raw.get("rewards"), id) : defaultRewards;
        String rewardMessageKey = optional(raw, "reward-message-key", optional(raw, "reward-message", defaultRewardMessageKey));
        Material icon = defaultIcon;
        if (raw.containsKey("icon")) {
            Object iconVal = raw.get("icon");
            if (!(iconVal instanceof String name) || name.isBlank()) return null;
            Material matched = Material.matchMaterial(name.trim());
            if (matched == null || !matched.isItem()) return null;
            icon = matched;
        }
        String trigger = optional(raw, "trigger", "").toLowerCase(Locale.ROOT);
        return switch (trigger) {
            case "trade" -> new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, 0.0, null, rewards, rewardMessageKey);
            case "place" -> {
                Material material = parsePlaceMaterial(raw.get("material"));
                yield material == null ? null : new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, 0.0, material, rewards, rewardMessageKey);
            }
            case "home" -> new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, 0.0, null, rewards, rewardMessageKey);
            case "command" -> {
                Object value = raw.get("command");
                yield (value instanceof String cmd && !cmd.isBlank())
                        ? new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, cmd.trim(), null, null, 0, 0.0, null, rewards, rewardMessageKey)
                        : null;
            }
            case "claim" -> claim.isAvailable()
                    ? new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, 0.0, null, rewards, rewardMessageKey)
                    : null;
            case "sale" -> {
                if (!vault.isAvailable()) yield null;
                Object gainVal = raw.get("min-gain");
                double minGain = 1.0;
                if (gainVal instanceof Number number) minGain = number.doubleValue();
                else if (gainVal instanceof String text) {
                    try { minGain = Double.parseDouble(text.trim()); }
                    catch (NumberFormatException error) { yield null; }
                }
                yield minGain <= 0.0 ? null : new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, true, "", null, null, 0, minGain, null, rewards, rewardMessageKey);
            }
            default -> null;
        };
    }

    List<String> parseRewards(Object object, String sourceId) {
        if (object == null) return List.of();
        if (!(object instanceof List<?> list)) {
            plugin.getLogger().warning("Invalid rewards for " + sourceId + ": must be a list.");
            return List.of();
        }
        List<String> rewards = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text)) {
                plugin.getLogger().warning("Invalid rewards for " + sourceId + ": entries must be strings.");
                return List.of();
            }
            rewards.add(text);
        }
        return List.copyOf(rewards);
    }

    private Material parsePlaceMaterial(Object value) {
        if (value == null) return Material.CHEST;
        if (!(value instanceof String name) || name.isBlank()) return null;
        Material material = Material.matchMaterial(name.trim());
        if (material == null || !material.isBlock()) return null;
        return material;
    }

    private List<Integer> coordinates(Object object) {
        if (!(object instanceof List<?> list) || list.size() != 3) return null;
        List<Integer> result = new ArrayList<>(3);
        for (Object item : list) {
            if (item instanceof Number number) result.add(number.intValue());
            else if (item instanceof String text) {
                try { result.add(Integer.parseInt(text.trim())); }
                catch (NumberFormatException error) { return null; }
            } else return null;
        }
        return result;
    }

    private Material icon(Map<?, ?> raw, String id) {
        Object value = raw.get("icon");
        if (value == null) return Material.BOOK;
        if (!(value instanceof String text) || text.isBlank()) {
            plugin.getLogger().warning("Invalid icon for step " + id + ": " + value + ". Using BOOK.");
            return Material.BOOK;
        }
        Material material = Material.matchMaterial(text.trim());
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Invalid icon for step " + id + ": " + text + ". Using BOOK.");
            return Material.BOOK;
        }
        return material;
    }

    private String optional(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : fallback;
    }
}
