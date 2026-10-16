package net.clodo.startflow;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplatesTest {
    private static final Set<String> TRIGGERS = Set.of("home", "sale", "trade", "claim", "place", "region", "item", "command");
    private static final List<String> MESSAGE_KEYS = List.of("display-name-key", "description-key", "hint-key", "reward-message-key");

    @Test
    void bundledTemplatesHaveUniqueStepsKnownMessagesAndSupportedTriggers() {
        Set<String> english = messageKeys("en");
        Set<String> turkish = messageKeys("tr");

        for (String name : Templates.NAMES) {
            YamlConfiguration template = YamlConfiguration.loadConfiguration(new File("src/main/resources/templates/" + name + ".yml"));
            Set<String> ids = new HashSet<>();
            for (Map<?, ?> step : template.getMapList("steps")) {
                String id = String.valueOf(step.get("id"));
                assertTrue(ids.add(id), name + " repeats step id " + id);
                checkStep(name, id, step, english, turkish);
                Object fallback = step.get("fallback");
                if (fallback instanceof Map<?, ?> fallbackStep) {
                    checkStep(name, id + " fallback", fallbackStep, english, turkish);
                }
            }
        }
    }

    private void checkStep(String template, String id, Map<?, ?> step, Set<String> english, Set<String> turkish) {
        for (String field : MESSAGE_KEYS) {
            if (step.get(field) == null && field.equals("reward-message-key")) {
                continue;
            }
            String key = String.valueOf(step.get(field));
            assertTrue(english.contains(key), template + " " + id + " has an unknown English " + field + ": " + key);
            assertTrue(turkish.contains(key), template + " " + id + " has an unknown Turkish " + field + ": " + key);
        }
        String trigger = String.valueOf(step.get("trigger"));
        assertTrue(TRIGGERS.contains(trigger), template + " " + id + " has unsupported trigger " + trigger);
    }

    private Set<String> messageKeys(String language) {
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new File("src/main/resources/messages/" + language + ".yml"));
        return messages.getValues(true).keySet();
    }
}
