package net.clodo.startflow;

import java.util.List;

record MessageFeatureSettings(
        boolean enabled,
        boolean firstJoinBroadcast,
        String firstJoin,
        String join,
        String quit
) {
    MessageFeatureSettings() {
        this(false, true, "join.first.message", "join.player.message", "quit.player.message");
    }
}

record RulesSettings(
        boolean enabled,
        List<String> linesKeys,
        boolean restrict,
        List<String> acceptRewardCommands,
        List<String> lines,
        java.util.Map<String, List<String>> linesByLanguage
) {
    RulesSettings() {
        this(false, List.of("rules.line.1", "rules.line.2", "rules.line.3", "rules.line.4"), false, List.of(), List.of(), java.util.Map.of());
    }
}

record StarterKitSettings(
        boolean enabled,
        String giveOn,
        String messageKey,
        List<KitItem> items
) {
    StarterKitSettings() {
        this(false, "first-join", "starter-kit.message", List.of());
    }

    record KitItem(String material, int amount, java.util.Map<String, String> names, java.util.Map<String, List<String>> lores) {
        static final String ANY = "*";

        KitItem(String material, int amount) {
            this(material, amount, java.util.Map.of(), java.util.Map.of());
        }

        String name(String language, String fallbackLanguage) {
            String named = names.get(language);
            if (named == null) named = names.get(ANY);
            if (named == null) named = names.get(fallbackLanguage);
            return named;
        }

        List<String> lore(String language, String fallbackLanguage) {
            List<String> lines = lores.get(language);
            if (lines == null) lines = lores.get(ANY);
            if (lines == null) lines = lores.get(fallbackLanguage);
            return lines == null ? List.of() : lines;
        }
    }
}
