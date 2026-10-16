package net.clodo.startflow;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class GuideSettingsParser {
    private final StartFlowPlugin plugin;
    private final StepParser stepParser;

    GuideSettingsParser(StartFlowPlugin plugin, StepParser stepParser) {
        this.plugin = plugin;
        this.stepParser = stepParser;
    }

    GuideSettings parse(FileConfiguration config) {
        String language = config.getString("default-language", "tr").toLowerCase(Locale.ROOT);
        if (!Messages.LANGUAGES.contains(language)) language = "tr";
        DailyRewardSettings dailyReward = parseDailyReward(config);
        PlayTimeSettings playTime = parsePlayTimeReward(config);
        RulesSettings rules = parseRules(config);
        StarterKitSettings starterKit = parseStarterKit(config);
        MessageFeatureSettings joinMessages = parseJoinMessages(config);
        List<String> completionRewards = stepParser.parseRewards(config.get("completion-rewards"), "completion-rewards");
        String completionRewardMessageKey = config.getString("completion-reward-message-key", "reward.completion.message");
        if (completionRewardMessageKey == null || completionRewardMessageKey.isBlank()) {
            completionRewardMessageKey = "reward.completion.message";
        }
        List<Map<?, ?>> rawSteps = config.getMapList("steps");
        if (rawSteps.isEmpty()) {
            return new GuideSettings(language, config.getBoolean("effects", true), config.getBoolean("welcome", true), dailyReward, playTime, rules, starterKit, joinMessages, completionRewards, completionRewardMessageKey, List.of());
        }
        Set<String> ids = new HashSet<>();
        List<Step> steps = new ArrayList<>();
        for (Map<?, ?> raw : rawSteps) {
            if (steps.size() >= 9) break;
            Object rawId = raw.get("id");
            if (!(rawId instanceof String id) || !id.matches("[a-z0-9_-]+") || !ids.add(id)) {
                plugin.getLogger().warning("Skipping step with invalid or duplicate id: " + rawId);
                continue;
            }
            steps.add(stepParser.buildStep(raw, id));
        }
        return new GuideSettings(language, config.getBoolean("effects", true), config.getBoolean("welcome", true), dailyReward, playTime, rules, starterKit, joinMessages, completionRewards, completionRewardMessageKey, List.copyOf(steps));
    }

    private DailyRewardSettings parseDailyReward(FileConfiguration config) {
        boolean enabled = config.getBoolean("daily-reward.enabled", false);
        int resetAfterDays = config.getInt("daily-reward.reset-after-days", 2);
        if (resetAfterDays < 1) resetAfterDays = 2;
        String messageKey = config.getString("daily-reward.message-key", "daily.reward.message");
        if (messageKey == null || messageKey.isBlank()) messageKey = "daily.reward.message";
        String milestoneMessageKey = config.getString("daily-reward.milestone-message-key", "daily.milestone.message");
        if (milestoneMessageKey == null || milestoneMessageKey.isBlank()) milestoneMessageKey = "daily.milestone.message";
        List<String> commands = stepParser.parseRewards(config.get("daily-reward.commands"), "daily-reward.commands");
        Map<Integer, List<String>> milestones = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("daily-reward.milestones");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int day = Integer.parseInt(key);
                    if (day > 0) {
                        List<String> extra = stepParser.parseRewards(section.get(key), "daily-reward.milestones." + key);
                        if (!extra.isEmpty()) {
                            milestones.put(day, extra);
                        }
                    }
                } catch (NumberFormatException error) {
                    plugin.getLogger().warning("Invalid milestone day in config: " + key);
                }
            }
        }
        return new DailyRewardSettings(enabled, resetAfterDays, messageKey, milestoneMessageKey, commands, Map.copyOf(milestones));
    }

    private PlayTimeSettings parsePlayTimeReward(FileConfiguration config) {
        boolean enabled = config.getBoolean("playtime-reward.enabled", false);
        int afkAfterSeconds = config.getInt("playtime-reward.afk-after-seconds", 120);
        if (afkAfterSeconds < 10) afkAfterSeconds = 120;
        List<Map<?, ?>> rawMilestones = config.getMapList("playtime-reward.milestones");
        if (rawMilestones.isEmpty()) {
            return new PlayTimeSettings(enabled, afkAfterSeconds, List.of());
        }
        Set<String> ids = new HashSet<>();
        List<PlayTimeSettings.Milestone> milestones = new ArrayList<>();
        for (Map<?, ?> raw : rawMilestones) {
            if (milestones.size() >= 12) break;
            Object rawId = raw.get("id");
            if (!(rawId instanceof String id) || !id.matches("[a-z0-9_-]+") || !ids.add(id)) {
                plugin.getLogger().warning("Skipping playtime milestone with invalid or duplicate id: " + rawId);
                continue;
            }
            Object rawHours = raw.get("hours");
            double hours = -1.0;
            if (rawHours instanceof Number number) {
                hours = number.doubleValue();
            } else if (rawHours instanceof String text) {
                try {
                    hours = Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                }
            }
            if (hours <= 0.0) {
                plugin.getLogger().warning("Skipping playtime milestone with invalid hours: " + rawHours);
                continue;
            }
            List<String> commands = stepParser.parseRewards(raw.get("commands"), "playtime-reward.milestones." + id);
            String messageKey = raw.get("message-key") instanceof String key && !key.isBlank() ? key : null;
            milestones.add(new PlayTimeSettings.Milestone(id, hours, commands, messageKey));
        }
        milestones.sort(java.util.Comparator.comparingLong(PlayTimeSettings.Milestone::requiredSeconds));
        return new PlayTimeSettings(enabled, afkAfterSeconds, List.copyOf(milestones));
    }

    private RulesSettings parseRules(FileConfiguration config) {
        boolean enabled = config.getBoolean("rules.enabled", false);
        boolean restrict = config.getBoolean("rules.restrict", false);
        List<String> linesKeys = config.getStringList("rules.lines-keys");
        if (linesKeys.isEmpty()) linesKeys = List.of("rules.line.1", "rules.line.2", "rules.line.3", "rules.line.4");
        List<String> rewardCommands = stepParser.parseRewards(config.get("rules.accept-reward-commands"), "rules.accept-reward-commands");
        List<String> lines = config.getStringList("rules.lines").stream().filter(line -> !line.isBlank()).toList();
        java.util.Map<String, List<String>> linesByLanguage = new java.util.HashMap<>();
        org.bukkit.configuration.ConfigurationSection byLanguage = config.getConfigurationSection("rules.lines-by-language");
        if (byLanguage != null) {
            for (String language : byLanguage.getKeys(false)) {
                List<String> localized = byLanguage.getStringList(language).stream().filter(line -> !line.isBlank()).toList();
                if (!localized.isEmpty()) linesByLanguage.put(language.toLowerCase(java.util.Locale.ROOT), localized);
            }
        }
        return new RulesSettings(enabled, linesKeys, restrict, rewardCommands, lines, java.util.Map.copyOf(linesByLanguage));
    }

    private StarterKitSettings parseStarterKit(FileConfiguration config) {
        boolean enabled = config.getBoolean("starter-kit.enabled", false);
        String giveOn = config.getString("starter-kit.give-on", "first-join");
        if (!"rules-accept".equalsIgnoreCase(giveOn)) giveOn = "first-join";
        String messageKey = config.getString("starter-kit.message-key", "starter-kit.message");
        if (messageKey == null || messageKey.isBlank()) messageKey = "starter-kit.message";
        List<Map<?, ?>> rawItems = config.getMapList("starter-kit.items");
        List<StarterKitSettings.KitItem> items = new ArrayList<>();
        for (Map<?, ?> raw : rawItems) {
            if (items.size() >= 27) break;
            Object rawMaterial = raw.get("material");
            if (!(rawMaterial instanceof String matName) || matName.isBlank()) {
                plugin.getLogger().warning("Skipping kit item without valid material: " + rawMaterial);
                continue;
            }
            int amount = 1;
            Object rawAmount = raw.get("amount");
            if (rawAmount instanceof Number number) {
                amount = Math.max(1, number.intValue());
            } else if (rawAmount instanceof String text) {
                try { amount = Math.max(1, Integer.parseInt(text)); } catch (NumberFormatException ignored) { }
            }
            java.util.Map<String, String> names = parseKitNames(raw.get("name"));
            java.util.Map<String, List<String>> lores = parseKitLores(raw.get("lore"));
            items.add(new StarterKitSettings.KitItem(matName, amount, names, lores));
        }
        return new StarterKitSettings(enabled, giveOn, messageKey, List.copyOf(items));
    }

    private static java.util.Map<String, String> parseKitNames(Object raw) {
        java.util.Map<String, String> names = new java.util.HashMap<>();
        if (raw instanceof String text && !text.isBlank()) {
            names.put(StarterKitSettings.KitItem.ANY, text);
        } else if (raw instanceof Map<?, ?> byLanguage) {
            for (Map.Entry<?, ?> entry : byLanguage.entrySet()) {
                if (entry.getKey() != null && entry.getValue() instanceof String text && !text.isBlank()) names.put(entry.getKey().toString().toLowerCase(java.util.Locale.ROOT), text);
            }
        }
        return java.util.Map.copyOf(names);
    }

    private static java.util.Map<String, List<String>> parseKitLores(Object raw) {
        java.util.Map<String, List<String>> lores = new java.util.HashMap<>();
        if (raw instanceof List<?> lines) {
            lores.put(StarterKitSettings.KitItem.ANY, kitLines(lines));
        } else if (raw instanceof Map<?, ?> byLanguage) {
            for (Map.Entry<?, ?> entry : byLanguage.entrySet()) {
                if (entry.getKey() != null && entry.getValue() instanceof List<?> lines) lores.put(entry.getKey().toString().toLowerCase(java.util.Locale.ROOT), kitLines(lines));
            }
        }
        return java.util.Map.copyOf(lores);
    }

    private static List<String> kitLines(List<?> lines) {
        List<String> result = new ArrayList<>();
        for (Object line : lines) { if (line != null) result.add(line.toString()); }
        return List.copyOf(result);
    }

    private MessageFeatureSettings parseJoinMessages(FileConfiguration config) {
        boolean enabled = config.getBoolean("join-messages.enabled", false);
        boolean firstJoinBroadcast = config.getBoolean("join-messages.first-join-broadcast", true);
        String firstJoin = config.getString("join-messages.first-join", "join.first.message");
        String join = config.getString("join-messages.join", "join.player.message");
        String quit = config.getString("join-messages.quit", "quit.player.message");
        return new MessageFeatureSettings(enabled, firstJoinBroadcast, firstJoin, join, quit);
    }
}
