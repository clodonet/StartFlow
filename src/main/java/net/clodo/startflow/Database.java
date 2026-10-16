package net.clodo.startflow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Database {
    private final Path path;
    private final ExecutorService executor;
    private Connection connection;

    Database(Path path) {
        this.path = path;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "StartFlow-SQLite");
            thread.setDaemon(true);
            return thread;
        });
    }

    CompletableFuture<Void> initialize() {
        return run(() -> {
            connect();
            migrate();
        });
    }

    Connection connection() {
        return connection;
    }

    void close() {
        executor.execute(() -> {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        });
        executor.shutdown();
    }

    void ensurePlayer(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO players(uuid, first_join) VALUES(?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }

    Set<String> completedSteps(UUID uuid) throws SQLException {
        Set<String> steps = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT step FROM progress WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    steps.add(results.getString("step"));
                }
            }
        }
        return steps;
    }

    CompletableFuture<Void> run(DatabaseAction action) {
        return CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }, executor);
    }

    <T> CompletableFuture<T> supply(DatabaseQuery<T> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.get();
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }, executor);
    }

    <T> T transaction(DatabaseQuery<T> action) throws Exception {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T value = action.get();
            connection.commit();
            return value;
        } catch (Exception error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void connect() throws Exception {
        if (connection != null && !connection.isClosed()) return;
        Files.createDirectories(path.getParent());
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void migrate() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY)");
        }
        if (schemaVersion() < 1) transaction(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, first_join INTEGER NOT NULL, started INTEGER, completed INTEGER, skipped INTEGER)");
                statement.execute("CREATE TABLE IF NOT EXISTS progress (uuid TEXT NOT NULL, step TEXT NOT NULL, time INTEGER NOT NULL, PRIMARY KEY(uuid, step))");
                statement.execute("CREATE TABLE IF NOT EXISTS rewards (uuid TEXT NOT NULL, key TEXT NOT NULL, PRIMARY KEY(uuid, key))");
                statement.execute("CREATE TABLE IF NOT EXISTS sessions (uuid TEXT NOT NULL, day TEXT NOT NULL, PRIMARY KEY(uuid, day))");
                statement.execute("CREATE TABLE IF NOT EXISTS events (uuid TEXT NOT NULL, step TEXT, event TEXT NOT NULL, time INTEGER NOT NULL)");
                statement.execute("INSERT INTO schema_version(version) VALUES(1)");
            }
            return null;
        });
        if (schemaVersion() < 2) transaction(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE players ADD COLUMN welcomed INTEGER");
                statement.execute("INSERT INTO schema_version(version) VALUES(2)");
            }
            return null;
        });
        if (schemaVersion() < 3) transaction(() -> {
            try (Statement statement = connection.createStatement()) {
                boolean hasRewards = false;
                boolean hasKey = false;
                try (ResultSet results = statement.executeQuery("PRAGMA table_info(rewards)")) {
                    while (results.next()) {
                        hasRewards = true;
                        if ("key".equalsIgnoreCase(results.getString("name"))) hasKey = true;
                    }
                }
                if (!hasRewards) {
                    statement.execute("CREATE TABLE rewards (uuid TEXT NOT NULL, key TEXT NOT NULL, PRIMARY KEY(uuid, key))");
                } else if (!hasKey) {
                    statement.execute("CREATE TABLE rewards_new (uuid TEXT NOT NULL, key TEXT NOT NULL, PRIMARY KEY(uuid, key))");
                    statement.execute("INSERT OR IGNORE INTO rewards_new(uuid, key) SELECT uuid, reward_id FROM rewards");
                    statement.execute("DROP TABLE rewards");
                    statement.execute("ALTER TABLE rewards_new RENAME TO rewards");
                }
                statement.execute("INSERT INTO schema_version(version) VALUES(3)");
            }
            return null;
        });
        if (schemaVersion() < 4) transaction(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS daily (uuid TEXT PRIMARY KEY, last_day TEXT NOT NULL, streak INTEGER NOT NULL, best INTEGER NOT NULL)");
                statement.execute("INSERT INTO schema_version(version) VALUES(4)");
            }
            return null;
        });
        if (schemaVersion() < 5) transaction(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS playtime (uuid TEXT PRIMARY KEY, seconds INTEGER NOT NULL)");
                statement.execute("INSERT INTO schema_version(version) VALUES(5)");
            }
            return null;
        });
        if (schemaVersion() < 6) transaction(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS rules_accepted (uuid TEXT PRIMARY KEY, time INTEGER NOT NULL)");
                statement.execute("INSERT INTO schema_version(version) VALUES(6)");
            }
            return null;
        });
    }

    private int schemaVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT MAX(version) AS version FROM schema_version")) {
            return results.next() ? results.getInt("version") : 0;
        }
    }

    @FunctionalInterface
    interface DatabaseAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface DatabaseQuery<T> {
        T get() throws Exception;
    }
}
