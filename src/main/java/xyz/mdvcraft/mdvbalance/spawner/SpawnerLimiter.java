package xyz.mdvcraft.mdvbalance.spawner;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import xyz.mdvcraft.mdvbalance.MDVBalance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpawnerLimiter implements Listener {
    private final MDVBalance plugin;
    private final NamespacedKey originKey;

    private final Map<SpawnerKey, Set<UUID>> activeBySpawner = new HashMap<>();
    private final Map<UUID, SpawnerKey> originByEntity = new HashMap<>();

    public SpawnerLimiter(MDVBalance plugin) {
        this.plugin = plugin;
        this.originKey = new NamespacedKey(plugin, "vanilla_spawner_origin");
    }

    public void enable() {
        Bukkit.getScheduler().runTask(plugin, this::bootstrapLoadedEntities);
    }

    public void reload() {
        if (!enabled()) {
            activeBySpawner.clear();
            originByEntity.clear();
            return;
        }
        bootstrapLoadedEntities();
    }

    public void shutdown() {
        activeBySpawner.clear();
        originByEntity.clear();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("modules.spawners", true)
                && plugin.getConfig().getBoolean("spawners.enabled", true);
    }

    public int trackedSpawnerCount() {
        return activeBySpawner.size();
    }

    public int trackedEntityCount() {
        return originByEntity.size();
    }

    public int maxAlive() {
        return Math.max(1, plugin.getConfig().getInt("spawners.max-alive-per-spawner", 3));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!enabled()) return;
        CreatureSpawner spawner = event.getSpawner();
        if (spawner == null) return; // Minecart spawner: fuera del alcance de v1.0.0.
        if (plugin.getConfig().getBoolean("spawners.only-living-entities", true)
                && !(event.getEntity() instanceof LivingEntity)) return;

        SpawnerKey key = SpawnerKey.of(spawner);
        Set<UUID> active = activeBySpawner.computeIfAbsent(key, ignored -> new HashSet<>());
        UUID entityId = event.getEntity().getUniqueId();

        if (active.size() >= maxAlive()) {
            event.setCancelled(true);
            plugin.spawnerDebug("Spawn cancelado en " + key + " porque ya tiene " + active.size() + "/" + maxAlive());
            return;
        }

        // Reservamos el slot ahora mismo para que un spawnCount vanilla >1 no pueda
        // colar varias entidades en el mismo tick. Si otro plugin cancela después,
        // la reserva se limpia al siguiente tick.
        track(event.getEntity(), key, true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || !event.getEntity().isValid()) {
                untrack(event.getEntity());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!enabled()) return;
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD) {
            // Un chunk descargado NO libera el slot: la entidad sigue existiendo
            // guardada en ese chunk y debe seguir contando aunque esté lejos.
            return;
        }
        untrack(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!enabled()) return;
        for (Entity entity : event.getEntities()) restoreFromTag(entity);
    }

    private void bootstrapLoadedEntities() {
        if (!enabled()) return;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) restoreFromTag(entity);
        }
    }

    private void track(Entity entity, SpawnerKey key, boolean writeTag) {
        UUID uuid = entity.getUniqueId();
        SpawnerKey previous = originByEntity.put(uuid, key);
        if (previous != null && !previous.equals(key)) {
            Set<UUID> old = activeBySpawner.get(previous);
            if (old != null) {
                old.remove(uuid);
                if (old.isEmpty()) activeBySpawner.remove(previous);
            }
        }
        activeBySpawner.computeIfAbsent(key, ignored -> new HashSet<>()).add(uuid);

        if (writeTag && plugin.getConfig().getBoolean("spawners.persistent-origin-tag", true)) {
            entity.getPersistentDataContainer().set(originKey, PersistentDataType.STRING, key.serialize());
        }
    }

    private void untrack(Entity entity) {
        if (entity == null) return;
        UUID uuid = entity.getUniqueId();
        SpawnerKey key = originByEntity.remove(uuid);
        if (key == null) key = readOrigin(entity.getPersistentDataContainer());
        if (key == null) return;

        Set<UUID> active = activeBySpawner.get(key);
        if (active != null) {
            active.remove(uuid);
            if (active.isEmpty()) activeBySpawner.remove(key);
        }
    }

    private void restoreFromTag(Entity entity) {
        if (entity == null) return;
        if (originByEntity.containsKey(entity.getUniqueId())) return;
        SpawnerKey key = readOrigin(entity.getPersistentDataContainer());
        if (key != null) track(entity, key, false);
    }

    private SpawnerKey readOrigin(PersistentDataContainer pdc) {
        String raw = pdc.get(originKey, PersistentDataType.STRING);
        return SpawnerKey.parse(raw);
    }

    private record SpawnerKey(UUID worldId, int x, int y, int z) {
        static SpawnerKey of(CreatureSpawner spawner) {
            return new SpawnerKey(spawner.getWorld().getUID(), spawner.getX(), spawner.getY(), spawner.getZ());
        }

        String serialize() {
            return worldId + ";" + x + ";" + y + ";" + z;
        }

        static SpawnerKey parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String[] parts = raw.split(";", -1);
            if (parts.length != 4) return null;
            try {
                return new SpawnerKey(
                        UUID.fromString(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                );
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        @Override
        public String toString() {
            return worldId + "@" + x + "," + y + "," + z;
        }
    }
}
