package com.iruixos.vanillaplussuite;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Beehive;
import org.bukkit.block.data.type.*;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Conservative, loaded-chunk-only bee colony assistance. No honey is created directly. */
public final class BeeColonyCooperation implements Listener {
    private final JavaPlugin plugin;
    private BukkitTask task;
    private final Map<UUID, Visit> visits = new HashMap<>();
    private final Map<UUID, Long> lastSearch = new HashMap<>();
    private final Map<Location, Integer> observedHoney = new HashMap<>();
    private record Visit(Location home, Location guest, boolean disappeared, long started) {}

    public BeeColonyCooperation(JavaPlugin plugin) { this.plugin = plugin; }
    private boolean setting(String key, boolean def) { return plugin.getConfig().getBoolean("bee-colony-cooperation." + key, def); }
    private int number(String key, int def) { return plugin.getConfig().getInt("bee-colony-cooperation." + key, def); }
    private boolean enabled() { return setting("enabled", false); }

    public void restart() {
        stop();
        if (!enabled()) return;
        long interval = Math.max(40, number("scan-interval-ticks", 100));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, interval, interval);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        // Restore original home where the bee is still a loaded entity.
        for (World world : Bukkit.getWorlds()) for (Bee bee : world.getEntitiesByClass(Bee.class)) {
            Visit visit = visits.get(bee.getUniqueId());
            if (visit != null && validHive(visit.home())) bee.setHive(visit.home());
        }
        visits.clear(); lastSearch.clear(); observedHoney.clear();
    }

    private boolean validHive(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return false;
        Material material = location.getBlock().getType();
        return material == Material.BEEHIVE || material == Material.BEE_NEST;
    }

    private boolean eligible(Block block, boolean requireEmpty) {
        if (!validHive(block.getLocation())) return false;
        if (block.getType() == Material.BEEHIVE && !setting("include-beehives", true)) return false;
        if (block.getType() == Material.BEE_NEST && !setting("include-bee-nests", true)) return false;
        if (!(block.getState() instanceof Beehive hive)) return false;
        if (hive.getEntityCount() >= hive.getMaxEntities()) return false;
        if (requireEmpty && hive.getEntityCount() != 0) return false;
        if (setting("ignore-full-hives", true) && block.getBlockData() instanceof org.bukkit.block.data.type.Beehive data
                && data.getHoneyLevel() >= data.getMaximumHoneyLevel()) return false;
        return true;
    }

    private Location findHive(Bee bee, Location excluded, boolean emptyOnly) {
        int radius = Math.max(2, Math.min(16, number("search-radius", 12)));
        Location base = bee.getLocation(); World world = bee.getWorld();
        Location best = null; double bestDistance = Double.MAX_VALUE;
        // Search only loaded blocks; do not generate or load chunks.
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            int bx = base.getBlockX() + x, bz = base.getBlockZ() + z;
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            for (int y = -5; y <= 5; y++) {
                int by = base.getBlockY() + y;
                if (by < world.getMinHeight() || by >= world.getMaxHeight()) continue;
                Block block = world.getBlockAt(bx, by, bz);
                if (block.getType() != Material.BEEHIVE && block.getType() != Material.BEE_NEST) continue;
                Location candidate = block.getLocation();
                if (excluded != null && excluded.equals(candidate)) continue;
                if (!eligible(block, emptyOnly)) continue;
                double distance = base.distanceSquared(candidate);
                if (distance < bestDistance) { bestDistance = distance; best = candidate; }
            }
        }
        return best;
    }

    private void scan() {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        Set<UUID> visible = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            for (Bee bee : world.getEntitiesByClass(Bee.class)) {
                UUID id = bee.getUniqueId(); visible.add(id);
                Visit visit = visits.get(id);
                if (visit != null) {
                    // A bee entering a hive is temporarily removed from the world. Restore
                    // its original home only after it has reappeared following that visit.
                    if (visit.disappeared()) {
                        if (validHive(visit.home())) bee.setHive(visit.home());
                        visits.remove(id);
                    } else if (now - visit.started() > 120_000L) {
                        if (validHive(visit.home())) bee.setHive(visit.home());
                        visits.remove(id);
                    }
                    continue;
                }
                if (bee.isDead() || !bee.isValid() || bee.getAnger() > 0) continue;
                long cooldown = Math.max(5, number("search-cooldown-seconds", 30)) * 1000L;
                if (now - lastSearch.getOrDefault(id, 0L) < cooldown) continue;
                lastSearch.put(id, now);
                Location home = bee.getHive();
                if (validHive(home)) {
                    if (!setting("share-honey", true) || !bee.hasNectar()) continue;
                    if (!(home.getBlock().getBlockData() instanceof org.bukkit.block.data.type.Beehive data)
                            || data.getHoneyLevel() < data.getMaximumHoneyLevel()) continue;
                    Location guest = findHive(bee, home, setting("empty-hives-only", true));
                    if (guest != null) {
                        visits.put(id, new Visit(home.clone(), guest.clone(), false, now));
                        bee.setHive(guest);
                    }
                } else {
                    boolean raining = world.hasStorm() || world.isThundering();
                    if (!setting("improved-hive-selection", true) && !setting("relocation-assistance", true)
                            && !(raining && setting("rain-shelter", true))) continue;
                    if (raining && !setting("rain-shelter", true)) continue;
                    Location replacement = findHive(bee, null, false);
                    if (replacement != null) bee.setHive(replacement);
                }
            }
        }
        for (Map.Entry<UUID, Visit> entry : new ArrayList<>(visits.entrySet())) {
            if (!visible.contains(entry.getKey()) && !entry.getValue().disappeared()) {
                Visit v = entry.getValue();
                visits.put(entry.getKey(), new Visit(v.home(), v.guest(), true, v.started()));
            }
        }
        // Expire stale records, e.g. when a bee despawns or a chunk unloads.
        visits.entrySet().removeIf(e -> now - e.getValue().started() > 300_000L);
        if (setting("activity-particles", true)) showActivity();
    }

    private void showActivity() {
        int radius = Math.max(2, Math.min(12, number("activity-radius", 8)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location center = player.getLocation(); World world = player.getWorld();
            for (int x = -radius; x <= radius; x += 2) for (int z = -radius; z <= radius; z += 2) {
                int bx = center.getBlockX() + x, bz = center.getBlockZ() + z;
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
                for (int y = -3; y <= 3; y++) {
                    Block block = world.getBlockAt(bx, center.getBlockY() + y, bz);
                    if (block.getType() != Material.BEEHIVE && block.getType() != Material.BEE_NEST) continue;
                    if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Beehive data)) continue;
                    Location loc = block.getLocation(); Integer previous = observedHoney.put(loc, data.getHoneyLevel());
                    if (previous != null && data.getHoneyLevel() > previous) {
                        player.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(.5, .8, .5), 4, .2, .2, .2, 0);
                    }
                }
            }
        }
        if (observedHoney.size() > 3000) observedHoney.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInspect(PlayerInteractEvent event) {
        if (!enabled() || !setting("occupancy-inspection", true)
                || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || !event.getPlayer().isSneaking()
                || !event.getPlayer().getInventory().getItemInMainHand().getType().isAir()) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Beehive hive)) return;
        int honey = block.getBlockData() instanceof org.bukkit.block.data.type.Beehive data ? data.getHoneyLevel() : 0;
        event.getPlayer().sendMessage(ChatColor.GRAY + "[Vanilla+] " + ChatColor.GOLD
                + "Hive: " + hive.getEntityCount() + "/" + hive.getMaxEntities()
                + " bees inside | Honey: " + honey + "/5");
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }
}
