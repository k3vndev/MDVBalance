package xyz.mdvcraft.mdvbalance.tutorial;

import xyz.mdvcraft.mdvbalance.MDVBalance;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public final class TutorialStorage {
    private final MDVBalance plugin;
    private Connection connection;

    public TutorialStorage(MDVBalance plugin) {
        this.plugin = plugin;
    }

    public void open() throws SQLException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta de MDVBalance.");
        }
        File dbFile = new File(plugin.getDataFolder(), "tutorial.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("CREATE TABLE IF NOT EXISTS tutorial_progress (" +
                    "uuid TEXT PRIMARY KEY," +
                    "player_name TEXT NOT NULL," +
                    "step INTEGER NOT NULL," +
                    "completed INTEGER NOT NULL," +
                    "started_at INTEGER NOT NULL," +
                    "objective_started_at INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL" +
                    ")");
        }
    }

    public Optional<TutorialProgress> load(UUID uuid) {
        if (connection == null || uuid == null) return Optional.empty();
        String sql = "SELECT player_name, step, completed, started_at, objective_started_at, updated_at " +
                "FROM tutorial_progress WHERE uuid=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new TutorialProgress(
                        uuid,
                        rs.getString("player_name"),
                        rs.getInt("step"),
                        rs.getInt("completed") != 0,
                        rs.getLong("started_at"),
                        rs.getLong("objective_started_at"),
                        rs.getLong("updated_at")
                ));
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe("No se pudo leer tutorial_progress para " + uuid + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    public void save(TutorialProgress progress) {
        if (connection == null || progress == null) return;
        String sql = "INSERT INTO tutorial_progress " +
                "(uuid, player_name, step, completed, started_at, objective_started_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "player_name=excluded.player_name, step=excluded.step, completed=excluded.completed, " +
                "started_at=excluded.started_at, objective_started_at=excluded.objective_started_at, " +
                "updated_at=excluded.updated_at";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, progress.uuid().toString());
            ps.setString(2, progress.playerName() == null ? "" : progress.playerName());
            ps.setInt(3, progress.step());
            ps.setInt(4, progress.completed() ? 1 : 0);
            ps.setLong(5, progress.startedAt());
            ps.setLong(6, progress.objectiveStartedAt());
            ps.setLong(7, progress.updatedAt());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("No se pudo guardar tutorial_progress para " + progress.uuid() + ": " + ex.getMessage());
        }
    }

    public void delete(UUID uuid) {
        if (connection == null || uuid == null) return;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM tutorial_progress WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("No se pudo borrar tutorial_progress para " + uuid + ": " + ex.getMessage());
        }
    }

    public void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Error cerrando tutorial.db: " + ex.getMessage());
        } finally {
            connection = null;
        }
    }
}
