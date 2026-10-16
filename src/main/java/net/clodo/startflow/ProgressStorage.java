package net.clodo.startflow;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class ProgressStorage {
    private final Database db;

    ProgressStorage(Database db) {
        this.db = db;
    }

    CompletableFuture<Boolean> recordJoinAndClaimWelcome(UUID uuid, String day, boolean enabled) {
        return db.supply(() -> db.transaction(() -> {
            db.ensurePlayer(uuid);
            try (PreparedStatement session = db.connection().prepareStatement("INSERT OR IGNORE INTO sessions(uuid, day) VALUES(?, ?)");
                 PreparedStatement player = db.connection().prepareStatement("SELECT skipped, welcomed FROM players WHERE uuid = ?");
                 PreparedStatement steps = db.connection().prepareStatement("SELECT 1 FROM progress WHERE uuid = ? LIMIT 1")) {
                session.setString(1, uuid.toString());
                session.setString(2, day);
                session.executeUpdate();
                player.setString(1, uuid.toString());
                steps.setString(1, uuid.toString());
                try (ResultSet profile = player.executeQuery(); ResultSet progress = steps.executeQuery()) {
                    boolean welcome = enabled && profile.next() && profile.getObject("skipped") == null && profile.getObject("welcomed") == null && !progress.next();
                    if (welcome) {
                        try (PreparedStatement update = db.connection().prepareStatement("UPDATE players SET welcomed = ? WHERE uuid = ?")) {
                            update.setLong(1, Instant.now().toEpochMilli());
                            update.setString(2, uuid.toString());
                            update.executeUpdate();
                        }
                    }
                    return welcome;
                }
            }
        }));
    }

    CompletableFuture<ProgressService.Progress> getProgress(UUID uuid) {
        return db.supply(() -> {
            db.ensurePlayer(uuid);
            Set<String> completed = db.completedSteps(uuid);
            try (PreparedStatement statement = db.connection().prepareStatement("SELECT skipped FROM players WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return new ProgressService.Progress(Set.copyOf(completed), result.next() && result.getObject("skipped") != null);
                }
            }
        });
    }

    CompletableFuture<Boolean> completeStep(UUID uuid, String stepId, Set<String> expected) {
        return db.supply(() -> db.transaction(() -> {
            db.ensurePlayer(uuid);
            long now = Instant.now().toEpochMilli();
            boolean inserted;
            try (PreparedStatement statement = db.connection().prepareStatement("INSERT OR IGNORE INTO progress(uuid, step, time) VALUES(?, ?, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, stepId);
                statement.setLong(3, now);
                inserted = statement.executeUpdate() == 1;
            }
            try (PreparedStatement statement = db.connection().prepareStatement("UPDATE players SET started = COALESCE(started, ?) WHERE uuid = ?")) {
                statement.setLong(1, now);
                statement.setString(2, uuid.toString());
                statement.executeUpdate();
            }
            if (!expected.isEmpty() && db.completedSteps(uuid).containsAll(expected)) {
                try (PreparedStatement statement = db.connection().prepareStatement("UPDATE players SET completed = COALESCE(completed, ?) WHERE uuid = ?")) {
                    statement.setLong(1, now);
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                }
            }
            if (inserted) {
                try (PreparedStatement statement = db.connection().prepareStatement("INSERT INTO events(uuid, step, event, time) VALUES(?, ?, ?, ?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, stepId);
                    statement.setString(3, "complete");
                    statement.setLong(4, now);
                    statement.executeUpdate();
                }
            }
            return inserted;
        }));
    }

    CompletableFuture<Void> skip(UUID uuid) {
        return db.run(() -> db.transaction(() -> {
            db.ensurePlayer(uuid);
            long now = Instant.now().toEpochMilli();
            try (PreparedStatement statement = db.connection().prepareStatement("UPDATE players SET started = COALESCE(started, ?), skipped = COALESCE(skipped, ?) WHERE uuid = ?")) {
                statement.setLong(1, now);
                statement.setLong(2, now);
                statement.setString(3, uuid.toString());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    CompletableFuture<Void> reset(UUID uuid) {
        return db.run(() -> db.transaction(() -> {
            db.ensurePlayer(uuid);
            try (PreparedStatement steps = db.connection().prepareStatement("DELETE FROM progress WHERE uuid = ?");
                 PreparedStatement player = db.connection().prepareStatement("UPDATE players SET started = NULL, completed = NULL, skipped = NULL WHERE uuid = ?")) {
                steps.setString(1, uuid.toString());
                steps.executeUpdate();
                player.setString(1, uuid.toString());
                player.executeUpdate();
            }
            return null;
        }));
    }

    CompletableFuture<Void> resetStep(UUID uuid, String stepId) {
        return db.run(() -> db.transaction(() -> {
            db.ensurePlayer(uuid);
            try (PreparedStatement steps = db.connection().prepareStatement("DELETE FROM progress WHERE uuid = ? AND step = ?");
                 PreparedStatement player = db.connection().prepareStatement("UPDATE players SET completed = NULL WHERE uuid = ?")) {
                steps.setString(1, uuid.toString());
                steps.setString(2, stepId);
                steps.executeUpdate();
                player.setString(1, uuid.toString());
                player.executeUpdate();
            }
            return null;
        }));
    }
}
