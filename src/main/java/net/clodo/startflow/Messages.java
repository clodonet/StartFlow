package net.clodo.startflow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

final class Messages {
    static final List<String> LANGUAGES = List.of("tr", "en", "de", "es", "ru", "pt");

    private final StartFlowPlugin plugin;
    private final Supplier<GuideSettings> settings;
    private volatile Map<String, Map<String, String>> bundles = Map.of();

    Messages(StartFlowPlugin plugin, Supplier<GuideSettings> settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    void reload() {
        Map<String, Map<String, String>> loaded = new HashMap<>();
        for (String lang : LANGUAGES) {
            loaded.put(lang, load(lang));
        }
        bundles = Map.copyOf(loaded);
    }

    Component message(Player player, String key, Map<String, String> values) {
        return message(language(player), key, values, Map.of());
    }

    Component message(Player player, String key, Map<String, String> values, Map<String, String> parsedValues) {
        return message(language(player), key, values, parsedValues);
    }

    Component message(String language, String key, Map<String, String> values) {
        return message(language, key, values, Map.of());
    }

    Component message(String language, String key, Map<String, String> values, Map<String, String> parsedValues) {
        List<TagResolver> resolvers = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            resolvers.add(Placeholder.unparsed(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, String> entry : parsedValues.entrySet()) {
            resolvers.add(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }
        resolvers.add(Placeholder.parsed("prefix", text(language, "prefix")));
        return MiniMessage.miniMessage().deserialize(text(language, key), TagResolver.resolver(resolvers));
    }

    String text(Player player, String key) {
        return text(language(player), key);
    }

    String text(String language, String key) {
        Map<String, String> selected = bundles.getOrDefault(language, Map.of());
        Map<String, String> english = bundles.getOrDefault("en", Map.of());
        return selected.getOrDefault(key, english.getOrDefault(key, ""));
    }

    boolean has(Player player, String key) {
        return has(language(player), key);
    }

    boolean has(String language, String key) {
        if (key == null || key.isBlank()) return false;
        String val = text(language, key);
        return val != null && !val.isBlank();
    }

    void send(CommandSender sender, String key, Map<String, String> values) {
        send(sender, key, values, Map.of());
    }

    void send(CommandSender sender, String key, Map<String, String> values, Map<String, String> parsedValues) {
        if (sender instanceof Player player) {
            player.sendMessage(message(player, key, values, parsedValues));
        } else {
            sender.sendMessage(message(settings.get().defaultLanguage(), key, values, parsedValues));
        }
    }

    String language(Player player) {
        if (player == null) {
            return settings.get().defaultLanguage();
        }
        Locale locale = player.locale();
        String code = locale != null && locale.getLanguage() != null ? locale.getLanguage().toLowerCase(Locale.ROOT) : "";
        if (code.length() >= 2) {
            code = code.substring(0, 2);
        }
        return LANGUAGES.contains(code) ? code : settings.get().defaultLanguage();
    }

    String language(CommandSender sender) {
        if (sender instanceof Player player) {
            return language(player);
        }
        return settings.get().defaultLanguage();
    }

    private Map<String, String> load(String language) {
        try {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages/" + language + ".yml"));
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, Object> entry : configuration.getValues(true).entrySet()) {
                if (entry.getValue() instanceof String value) {
                    values.put(entry.getKey(), value);
                }
            }
            return Map.copyOf(values);
        } catch (Exception error) {
            plugin.getLogger().warning("Could not load messages/" + language + ".yml: " + Errors.concise(error));
            return Map.of();
        }
    }
}
