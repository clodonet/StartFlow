package net.clodo.startflow;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class RewardStorage {
    private final Database db;

    RewardStorage(Database db) {
        this.db = db;
    }

    CompletableFuture<Boolean> claimReward(UUID uuid, String key) {
        return db.supply(() -> db.transaction(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("INSERT OR IGNORE INTO rewards(uuid, key) VALUES(?, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, key);
                return statement.executeUpdate() == 1;
            }
        }));
    }

    CompletableFuture<Storage.DailyRecord> getDaily(UUID uuid) {
        return db.supply(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("SELECT last_day, streak, best FROM daily WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return new Storage.DailyRecord(result.getInt("streak"), result.getInt("best"), result.getString("last_day"));
                    }
                    return new Storage.DailyRecord(0, 0, null);
                }
            }
        });
    }

    CompletableFuture<Storage.DailyClaim> claimDaily(UUID uuid, String today, int resetAfterDays) {
        return db.supply(() -> db.transaction(() -> {
            LocalDate todayDate = LocalDate.parse(today);
            try (PreparedStatement select = db.connection().prepareStatement("SELECT last_day, streak, best FROM daily WHERE uuid = ?")) {
                select.setString(1, uuid.toString());
                try (ResultSet result = select.executeQuery()) {
                    if (result.next()) {
                        String lastDay = result.getString("last_day");
                        int streak = result.getInt("streak");
                        int best = result.getInt("best");
                        Storage.DailyRecord previous = new Storage.DailyRecord(streak, best, lastDay);
                        LocalDate lastDate = LocalDate.parse(lastDay);
                        if (lastDate.isEqual(todayDate) || lastDate.isAfter(todayDate)) {
                            return new Storage.DailyClaim(streak, best, false, previous);
                        }
                        long days = ChronoUnit.DAYS.between(lastDate, todayDate);
                        int newStreak = days <= resetAfterDays ? streak + 1 : 1;
                        int newBest = Math.max(best, newStreak);
                        try (PreparedStatement update = db.connection().prepareStatement("UPDATE daily SET last_day = ?, streak = ?, best = ? WHERE uuid = ? AND last_day = ?")) {
                            update.setString(1, today);
                            update.setInt(2, newStreak);
                            update.setInt(3, newBest);
                            update.setString(4, uuid.toString());
                            update.setString(5, lastDay);
                            if (update.executeUpdate() == 1) {
                                return new Storage.DailyClaim(newStreak, newBest, true, previous);
                            }
                            return new Storage.DailyClaim(streak, best, false, previous);
                        }
                    }
                }
            }
            try (PreparedStatement insert = db.connection().prepareStatement("INSERT OR IGNORE INTO daily(uuid, last_day, streak, best) VALUES(?, ?, 1, 1)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, today);
                if (insert.executeUpdate() == 1) {
                    return new Storage.DailyClaim(1, 1, true, new Storage.DailyRecord(0, 0, null));
                }
            }
            try (PreparedStatement select = db.connection().prepareStatement("SELECT last_day, streak, best FROM daily WHERE uuid = ?")) {
                select.setString(1, uuid.toString());
                try (ResultSet result = select.executeQuery()) {
                    if (result.next()) {
                        return new Storage.DailyClaim(result.getInt("streak"), result.getInt("best"), false,
                                new Storage.DailyRecord(result.getInt("streak"), result.getInt("best"), result.getString("last_day")));
                    }
                }
            }
            return new Storage.DailyClaim(0, 0, false, new Storage.DailyRecord(0, 0, null));
        }));
    }

    CompletableFuture<Void> rollbackDailyClaim(UUID uuid, String today, Storage.DailyClaim claim) {
        return db.run(() -> db.transaction(() -> {
            Storage.DailyRecord previous = claim.previous();
            if (previous.lastDay() == null) {
                try (PreparedStatement delete = db.connection().prepareStatement("DELETE FROM daily WHERE uuid = ? AND last_day = ? AND streak = ? AND best = ?")) {
                    delete.setString(1, uuid.toString());
                    delete.setString(2, today);
                    delete.setInt(3, claim.streak());
                    delete.setInt(4, claim.best());
                    delete.executeUpdate();
                }
            } else {
                try (PreparedStatement update = db.connection().prepareStatement("UPDATE daily SET last_day = ?, streak = ?, best = ? WHERE uuid = ? AND last_day = ? AND streak = ? AND best = ?")) {
                    update.setString(1, previous.lastDay());
                    update.setInt(2, previous.streak());
                    update.setInt(3, previous.best());
                    update.setString(4, uuid.toString());
                    update.setString(5, today);
                    update.setInt(6, claim.streak());
                    update.setInt(7, claim.best());
                    update.executeUpdate();
                }
            }
            return null;
        }));
    }

    CompletableFuture<Void> resetDaily(UUID uuid) {
        return db.run(() -> db.transaction(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("DELETE FROM daily WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    CompletableFuture<Long> getPlayTime(UUID uuid) {
        return db.supply(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("SELECT seconds FROM playtime WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong("seconds") : 0L;
                }
            }
        });
    }

    CompletableFuture<Void> savePlayTime(UUID uuid, long seconds) {
        return db.run(() -> db.transaction(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("INSERT OR REPLACE INTO playtime(uuid, seconds) VALUES(?, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setLong(2, seconds);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    CompletableFuture<Void> saveAllPlayTime(Map<UUID, Long> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return db.run(() -> db.transaction(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("INSERT OR REPLACE INTO playtime(uuid, seconds) VALUES(?, ?)")) {
                for (Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
                    statement.setString(1, entry.getKey().toString());
                    statement.setLong(2, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }));
    }

    CompletableFuture<Void> resetPlayTime(UUID uuid) {
        return db.run(() -> db.transaction(() -> {
            try (PreparedStatement deletePlaytime = db.connection().prepareStatement("DELETE FROM playtime WHERE uuid = ?");
                 PreparedStatement deleteRewards = db.connection().prepareStatement("DELETE FROM rewards WHERE uuid = ? AND key LIKE 'playtime:%'")) {
                deletePlaytime.setString(1, uuid.toString());
                deletePlaytime.executeUpdate();
                deleteRewards.setString(1, uuid.toString());
                deleteRewards.executeUpdate();
            }
            return null;
        }));
    }

    CompletableFuture<Boolean> hasAcceptedRules(UUID uuid) {
        return db.supply(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("SELECT 1 FROM rules_accepted WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    CompletableFuture<Boolean> acceptRules(UUID uuid) {
        return db.supply(() -> db.transaction(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("INSERT OR IGNORE INTO rules_accepted(uuid, time) VALUES(?, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setLong(2, Instant.now().toEpochMilli());
                return statement.executeUpdate() == 1;
            }
        }));
    }

    CompletableFuture<Void> resetRules(UUID uuid) {
        return db.run(() -> db.transaction(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("DELETE FROM rules_accepted WHERE uuid = ?");
                 PreparedStatement reward = db.connection().prepareStatement("DELETE FROM rewards WHERE uuid = ? AND key = 'rules'")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
                reward.setString(1, uuid.toString());
                reward.executeUpdate();
            }
            return null;
        }));
    }

    CompletableFuture<Void> resetKit(UUID uuid) {
        return db.run(() -> db.transaction(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("DELETE FROM rewards WHERE uuid = ? AND key = 'kit'")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
            return null;
        }));
    }
}
