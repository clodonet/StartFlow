package net.clodo.startflow;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class ReportStorage {
    private final Database db;

    ReportStorage(Database db) {
        this.db = db;
    }

    CompletableFuture<Storage.FunnelReport> funnelReport(List<String> enabledStepIds) {
        return db.supply(() -> {
            int totalPlayers = 0;
            int startedCount = 0;
            int completedCount = 0;
            int skippedCount = 0;
            try (Statement statement = db.connection().createStatement();
                 ResultSet results = statement.executeQuery("SELECT COUNT(*) AS total, COUNT(started) AS started, COUNT(completed) AS completed, COUNT(skipped) AS skipped FROM players")) {
                if (results.next()) {
                    totalPlayers = results.getInt("total");
                    startedCount = results.getInt("started");
                    completedCount = results.getInt("completed");
                    skippedCount = results.getInt("skipped");
                }
            }

            Map<String, Integer> stepCompletionCounts = new HashMap<>();
            try (Statement statement = db.connection().createStatement();
                 ResultSet results = statement.executeQuery("SELECT step, COUNT(DISTINCT uuid) AS count FROM progress GROUP BY step")) {
                while (results.next()) {
                    stepCompletionCounts.put(results.getString("step"), results.getInt("count"));
                }
            }

            Set<UUID> candidatePlayers = new HashSet<>();
            try (Statement statement = db.connection().createStatement();
                 ResultSet results = statement.executeQuery("SELECT uuid FROM players WHERE completed IS NULL AND skipped IS NULL")) {
                while (results.next()) {
                    try {
                        candidatePlayers.add(UUID.fromString(results.getString("uuid")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            Map<UUID, Set<String>> candidateProgress = new HashMap<>();
            if (!candidatePlayers.isEmpty()) {
                try (Statement statement = db.connection().createStatement();
                     ResultSet results = statement.executeQuery("SELECT uuid, step FROM progress")) {
                    while (results.next()) {
                        try {
                            UUID uuid = UUID.fromString(results.getString("uuid"));
                            if (candidatePlayers.contains(uuid)) {
                                candidateProgress.computeIfAbsent(uuid, k -> new HashSet<>()).add(results.getString("step"));
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }

            List<Storage.FunnelStep> steps = new ArrayList<>();
            String biggestDropStep = null;
            int maxStuck = -1;

            for (int i = 0; i < enabledStepIds.size(); i++) {
                String stepId = enabledStepIds.get(i);
                int completed = stepCompletionCounts.getOrDefault(stepId, 0);
                int percent = totalPlayers == 0 ? 0 : Math.min(100, Math.round((float) completed * 100 / totalPlayers));

                int stuck = 0;
                if (i == 0) {
                    for (UUID uuid : candidatePlayers) {
                        Set<String> done = candidateProgress.getOrDefault(uuid, Set.of());
                        if (!done.contains(stepId)) {
                            stuck++;
                        }
                    }
                } else {
                    String previousStepId = enabledStepIds.get(i - 1);
                    for (UUID uuid : candidatePlayers) {
                        Set<String> done = candidateProgress.getOrDefault(uuid, Set.of());
                        if (done.contains(previousStepId) && !done.contains(stepId)) {
                            stuck++;
                        }
                    }
                }

                steps.add(new Storage.FunnelStep(stepId, completed, percent, stuck));
                if (stuck > maxStuck) {
                    maxStuck = stuck;
                    biggestDropStep = stepId;
                }
            }

            if (maxStuck <= 0) {
                biggestDropStep = null;
            }

            return new Storage.FunnelReport(totalPlayers, startedCount, completedCount, skippedCount, List.copyOf(steps), biggestDropStep);
        });
    }
}
