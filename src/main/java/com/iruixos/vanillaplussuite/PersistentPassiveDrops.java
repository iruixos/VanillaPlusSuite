package com.iruixos.vanillaplussuite;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Tracks passive animal products only, not dropped inventory items or mob death loot. */
final class PersistentPassiveDrops implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey expiryKey;
    private final Map<UUID, Item> tracked = new HashMap<>();
    private BukkitTask timer;

    PersistentPassiveDrops(JavaPlugin plugin) {
        this.plugin = plugin;
        expiryKey = new NamespacedKey(plugin, "passive_drop_expires_at");
    }

    void restart() {
        if (timer != null) timer.cancel();
        tracked.clear();
        boolean enabled = plugin.getConfig().getBoolean("persistent-passive-drops.enabled", true);
        if (enabled && plugin.getConfig().getBoolean("persistent-passive-drops.indefinite", false)) {
            plugin.getLogger().warning("Persistent passive drops: INDEFINITE lifetime is enabled. Accumulated items can cause serious entity lag; consider a finite lifetime.");
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk, enabled);
            }
        }
        if (enabled) timer = Bukkit.getScheduler().runTaskTimer(plugin, this::expireTracked, 20L, 20L);
    }

    void stop() {
        if (timer != null) timer.cancel();
        timer = null;
        tracked.clear(); // PDC survives a server restart; the next enable rescans loaded chunks.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("persistent-passive-drops.enabled", true)) return;
        Item item = event.getEntity();
        // Player-thrown and other attributed items are not passive animal products.
        if (item.getThrower() != null) return;
        if (!isEligibleAnimalProduct(item)) return;
        boolean unlimited = plugin.getConfig().getBoolean("persistent-passive-drops.indefinite", false);
        int seconds = Math.max(1, plugin.getConfig().getInt("persistent-passive-drops.lifetime-seconds", 1800));
        long expiry = unlimited ? -1L : System.currentTimeMillis() + seconds * 1000L;
        item.getPersistentDataContainer().set(expiryKey, PersistentDataType.LONG, expiry);
        item.setUnlimitedLifetime(true);
        tracked.put(item.getUniqueId(), item);
    }

    private boolean isEligibleAnimalProduct(Item item) {
        Material type = item.getItemStack().getType();
        Class<? extends Entity> animalType;
        boolean requiresTame = false;
        String category;
        switch (type) {
            case EGG, BLUE_EGG, BROWN_EGG -> {
                category = "chicken-eggs";
                animalType = Chicken.class;
            }
            case ARMADILLO_SCUTE -> {
                category = "armadillo-scutes";
                animalType = Armadillo.class;
            }
            case SLIME_BALL -> {
                category = "panda-slimeballs";
                animalType = Panda.class;
            }
            case FEATHER, PHANTOM_MEMBRANE, RABBIT_FOOT, RABBIT_HIDE,
                 ROTTEN_FLESH, CHICKEN, STRING -> {
                category = "cat-morning-gifts";
                animalType = Cat.class;
                requiresTame = true;
            }
            default -> { return false; }
        }
        if (!plugin.getConfig().getBoolean("persistent-passive-drops." + category, true)) return false;
        double radius = Math.max(0.5, Math.min(5.0, plugin.getConfig().getDouble("persistent-passive-drops.animal-detection-radius", 1.75)));
        for (Entity nearby : item.getNearbyEntities(radius, radius, radius)) {
            if (!animalType.isInstance(nearby)) continue;
            if (nearby instanceof Ageable age && !age.isAdult()) continue;
            if (requiresTame && (!(nearby instanceof Tameable tame) || !tame.isTamed())) continue;
            // The item must have appeared close to the animal, not merely in the same pen.
            if (nearby.getLocation().distanceSquared(item.getLocation()) <= radius * radius) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        scanChunk(event.getChunk(), plugin.getConfig().getBoolean("persistent-passive-drops.enabled", true));
    }

    private void scanChunk(Chunk chunk, boolean enabled) {
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Item item)) continue;
            PersistentDataContainer data = item.getPersistentDataContainer();
            Long expiry = data.get(expiryKey, PersistentDataType.LONG);
            if (expiry == null) continue;
            if (!enabled) {
                data.remove(expiryKey);
                item.setUnlimitedLifetime(false);
            } else if (expiry >= 0 && System.currentTimeMillis() >= expiry) {
                item.remove();
            } else {
                item.setUnlimitedLifetime(true);
                tracked.put(item.getUniqueId(), item);
            }
        }
    }

    private void expireTracked() {
        long now = System.currentTimeMillis();
        Iterator<Item> iterator = tracked.values().iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            if (!item.isValid()) { iterator.remove(); continue; }
            Long expiry = item.getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
            if (expiry == null) { iterator.remove(); continue; }
            if (expiry >= 0 && now >= expiry) {
                item.remove();
                iterator.remove();
            }
        }
    }

    // Prevent a protected item merging into an untracked stack (or vice versa),
    // which would otherwise lose its distinct lifetime and provenance.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(expiryKey, PersistentDataType.LONG)
                || event.getTarget().getPersistentDataContainer().has(expiryKey, PersistentDataType.LONG)) {
            event.setCancelled(true);
        }
    }
}
