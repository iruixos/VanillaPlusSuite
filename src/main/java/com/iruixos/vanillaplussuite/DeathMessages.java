package com.iruixos.vanillaplussuite;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Public flavour text only; grave coordinates remain private in VanillaPlusSuite. */
final class DeathMessages {
    private DeathMessages() {}

    static void broadcast(JavaPlugin plugin, PlayerDeathEvent event, Random random) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("death-messages.enabled", true)) return;
        Player player = event.getEntity();
        EntityDamageEvent last = player.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = last == null ? null : last.getCause();
        String key = category(player, cause);
        String context = context(player.getLocation(), key, config, random);
        List<String> options = config.getStringList("death-messages.messages." + context);
        if (options.isEmpty()) options = config.getStringList("death-messages.messages." + key);
        if (options.isEmpty()) options = config.getStringList("death-messages.messages.generic");
        if (options.isEmpty()) options = List.of("{player} discovered that Hard difficulty is aptly named.");
        String template = options.get(random.nextInt(options.size()));
        String killer = player.getKiller() == null ? "someone" : player.getKiller().getName();
        String text = template.replace("{player}", player.getName()).replace("{killer}", killer);
        if (config.getBoolean("death-messages.replace-vanilla", true)) event.deathMessage(null);
        Bukkit.broadcast(Component.text(text, NamedTextColor.GRAY));
    }

    private static String category(Player player, EntityDamageEvent.DamageCause cause) {
        if (player.getKiller() != null) return "pvp";
        if (cause == null) return "generic";
        switch (cause) {
            case FALL: return "fall";
            case LAVA: return "lava";
            case FIRE, FIRE_TICK, HOT_FLOOR: return "fire";
            case DROWNING: return "drowning";
            case LIGHTNING: return "lightning";
            case VOID: return "void";
            case SUFFOCATION, CRAMMING: return "suffocation";
            case STARVATION: return "starvation";
            case FREEZE: return "freezing";
            case POISON, WITHER, MAGIC: return "magic";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION: return "explosion";
            case PROJECTILE: return "projectile";
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, THORNS: return "mob";
            default: return "generic";
        }
    }

    private static String context(Location loc, String cause, FileConfiguration config, Random random) {
        if (!config.getBoolean("death-messages.environment.enabled", true)) return cause;
        double chance = config.getDouble("death-messages.environment.chance", 0.30);
        if (random.nextDouble() >= Math.max(0, Math.min(1, chance))) return cause;
        World world = loc.getWorld();
        if (world == null) return cause;
        if ("fall".equals(cause) && near(loc, Material.LAVA, 3)) return "fall-near-lava";
        if ("mob".equals(cause) && nearVillage(loc)) return "mob-near-village";
        if ("fall".equals(cause) && world.getEnvironment() == World.Environment.THE_END) return "fall-in-end";
        if ("generic".equals(cause) && world.getEnvironment() == World.Environment.NETHER) return "generic-nether";
        return cause;
    }

    private static boolean near(Location loc, Material material, int radius) {
        World world = loc.getWorld();
        if (world == null) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        for (int dx=-radius;dx<=radius;dx++) for (int dy=-radius;dy<=radius;dy++) for (int dz=-radius;dz<=radius;dz++) {
            int by=y+dy;
            if (by >= world.getMinHeight() && by < world.getMaxHeight() && world.getBlockAt(x+dx,by,z+dz).getType()==material) return true;
        }
        return false;
    }

    private static boolean nearVillage(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        return !world.getNearbyEntities(loc, 16, 8, 16, entity -> entity.getType() == EntityType.VILLAGER).isEmpty();
    }
}
