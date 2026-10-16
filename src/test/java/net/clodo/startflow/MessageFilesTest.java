package net.clodo.startflow;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageFilesTest {
    private static final List<String> LANGUAGES = List.of("tr", "en", "de", "es", "ru", "pt");
    private static final Pattern TAG = Pattern.compile("</?[A-Za-z][^>]*>");

    @Test
    void messageFilesMatchEnglishKeysOrderAndTags() {
        Map<String, String> english = values("en");
        List<String> englishKeys = new ArrayList<>(english.keySet());

        for (String language : LANGUAGES) {
            Map<String, String> translated = values(language);
            assertEquals(englishKeys, new ArrayList<>(translated.keySet()), language + " keys and order");
            for (String key : englishKeys) {
                String value = translated.get(key);
                assertFalse(value == null || value.isBlank(), language + " has a blank value for " + key);
                assertEquals(tags(english.get(key)), tags(value), language + " tags for " + key);
            }
        }
    }

    private Map<String, String> values(String language) {
        YamlConfiguration file = YamlConfiguration.loadConfiguration(new File("src/main/resources/messages/" + language + ".yml"));
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : file.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String text) {
                values.put(entry.getKey(), text);
            }
        }
        return values;
    }

    private Set<String> tags(String value) {
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = TAG.matcher(value);
        while (matcher.find()) {
            String tag = matcher.group();
            tags.add(tag.startsWith("<click:run_command") ? "<click:run_command>" : tag);
        }
        return tags;
    }
}
