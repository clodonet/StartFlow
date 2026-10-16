package net.clodo.startflow;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

final class UpdateCheck implements Listener {
    private static final String API_URL = "https://api.modrinth.com/v2/project/startflow/version?loaders=%5B%22paper%22%5D";
    private static final String PROJECT_URL = "https://modrinth.com/project/startflow";
    private static final int MAX_RESPONSE_SIZE = 256 * 1024;

    private final StartFlowPlugin plugin;
    private final Messages messages;
    private final Tasks tasks;
    private final HttpClient client;

    private volatile String latestVersion;
    private volatile boolean updateAvailable;
    private Tasks.TaskRef scheduledCheck;

    UpdateCheck(StartFlowPlugin plugin, Messages messages, Tasks tasks) {
        this.plugin = plugin;
        this.messages = messages;
        this.tasks = tasks;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    void start() {
        if (!enabled()) return;
        tasks.async(this::check);
        scheduledCheck = tasks.asyncRepeating(this::check, 12, 12, TimeUnit.HOURS);
    }

    void close() {
        if (scheduledCheck != null) {
            scheduledCheck.cancel();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (updateAvailable && player.hasPermission("startflow.admin")) {
            notifyPlayer(player);
        }
    }

    private void check() {
        if (!enabled()) return;
        try {
            String currentVersion = plugin.getPluginMeta().getVersion();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("User-Agent", "StartFlow/" + currentVersion + " (clodo.net)")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                plugin.getLogger().fine("Update check returned status code: " + response.statusCode());
                return;
            }
            OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
            if (contentLength.isPresent() && contentLength.getAsLong() > MAX_RESPONSE_SIZE) {
                plugin.getLogger().warning("Update check response exceeded size limit of 256 KiB");
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = response.body()) {
                byte[] chunk = new byte[8192];
                int total = 0;
                int read;
                while ((read = in.read(chunk)) != -1) {
                    total += read;
                    if (total > MAX_RESPONSE_SIZE) {
                        plugin.getLogger().warning("Update check response exceeded size limit of 256 KiB");
                        return;
                    }
                    buffer.write(chunk, 0, read);
                }
            }
            String body = buffer.toString(StandardCharsets.UTF_8);
            JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
            for (JsonElement element : versions) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String versionType = object.has("version_type") ? object.get("version_type").getAsString() : "";
                if (!"release".equalsIgnoreCase(versionType)) continue;
                String releaseVersion = object.has("version_number") ? object.get("version_number").getAsString() : null;
                if (releaseVersion != null) {
                    if (isNewer(releaseVersion, currentVersion)) {
                        boolean firstDiscovery = !updateAvailable;
                        latestVersion = releaseVersion;
                        updateAvailable = true;
                        if (firstDiscovery) {
                            plugin.getLogger().info("A new StartFlow version is available: " + releaseVersion + " (current: " + currentVersion + ")");
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (player.hasPermission("startflow.admin")) {
                                    notifyPlayer(player);
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception error) {
            plugin.getLogger().fine("Update check could not complete: " + error.getMessage());
        }
    }

    private boolean enabled() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) return true;
        try {
            return YamlConfiguration.loadConfiguration(file).getBoolean("update-check", true);
        } catch (Exception error) {
            return true;
        }
    }

    private void notifyPlayer(Player player) {
        tasks.player(player, () -> {
            if (!player.isOnline() || !player.hasPermission("startflow.admin")) return;
            player.sendMessage(messages.message(player, "update-available", Map.of("version", latestVersion, "url", PROJECT_URL)).clickEvent(ClickEvent.openUrl(PROJECT_URL)));
        });
    }

    private static boolean isNewer(String remote, String current) {
        int[] remoteParts = parseVersion(remote);
        int[] currentParts = parseVersion(current);
        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int r = i < remoteParts.length ? remoteParts[i] : 0;
            int c = i < currentParts.length ? currentParts[i] : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        if (version == null) return new int[0];
        String clean = version.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) {
            clean = clean.substring(1);
        }
        int dash = clean.indexOf('-');
        if (dash >= 0) clean = clean.substring(0, dash);
        String[] parts = clean.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].replaceAll("\\D+", ""));
            } catch (NumberFormatException ignored) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }
}
