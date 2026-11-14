package com.iruixos.vanillaplussuite;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Clean-room reversible mob silencing, with a temporary visual status indicator.
 * Mob names are never changed. The persistent marker belongs to the mob, not
 * to the TextDisplay; a normal Name Tag can rename a silenced mob at any time.
 */
public final class MobSilencing implements Listener {
    private final VanillaPlusSuite plugin;
    private final NamespacedKey silenceKey;
    private final Map<UUID, UUID> indicators = new HashMap<>();
    private BukkitTask indicatorTask;
    private BukkitTask followTask;

    public MobSilencing(VanillaPlusSuite plugin) {
        this.plugin = plugin;
        this.silenceKey = new NamespacedKey(plugin, "mob_silenced");
    }

    public void restart() {
        stop();
        if (!plugin.getConfig().getBoolean("mob-silencing.enabled", true)
                || !plugin.getConfig().getBoolean("mob-silencing.indicator.enabled", true)) return;
        // Periodic reconciliation also handles renamed mobs, freshly loaded mobs,
        // despawns, player arrivals, and configuration reloads without saving helpers.
        indicatorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateIndicators, 20L, 10L);
        // Independently track every silenced mob; never occupy passenger slots.
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::followFallbackIndicators, 1L, 1L);
    }

    public void stop() {
        if (indicatorTask != null) { indicatorTask.cancel(); indicatorTask = null; }
        if (followTask != null) { followTask.cancel(); followTask = null; }
        for (UUID id : indicators.values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) entity.remove();
        }
        indicators.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNameTagUse(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("mob-silencing.enabled", true)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof LivingEntity mob)
                || mob instanceof Player || mob instanceof ArmorStand) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.NAME_TAG || held.getAmount() < 1) return;
        ItemMeta meta = held.getItemMeta();
        if (meta == null || !meta.hasCustomName()) return;
        String tagName = PlainTextComponentSerializer.plainText()
                .serialize(meta.customName()).trim().toLowerCase(Locale.ROOT);

        List<String> silenceNames = plugin.getConfig().getStringList("mob-silencing.trigger-names");
        if (silenceNames.isEmpty()) silenceNames = List.of("silence", "silence me", "silence-me", "silence_me");
        List<String> restoreNames = plugin.getConfig().getStringList("mob-silencing.unsilence-names");
        if (restoreNames.isEmpty()) restoreNames = List.of("unsilence", "unsilence me", "unsilence-me", "unsilence_me");
        boolean silencing = silenceNames.stream().anyMatch(s -> s.trim().equalsIgnoreCase(tagName));
        boolean restoring = restoreNames.stream().anyMatch(s -> s.trim().equalsIgnoreCase(tagName));
        if (!silencing && !restoring) return;

        // Never pass these special tags to vanilla name-changing mechanics.
        event.setCancelled(true);
        boolean managed = mob.getPersistentDataContainer().has(silenceKey, PersistentDataType.BYTE);
        if (silencing) {
            if (managed || mob.isSilent()) {
                notify(player, "This mob is already silent.");
                return;
            }
            mob.setSilent(true);
            mob.getPersistentDataContainer().set(silenceKey, PersistentDataType.BYTE, (byte) 1);
            consume(player, held);
            notify(player, "Mob silenced. Its custom name is unchanged.");
        } else {
            // Do not change silence imposed by other plugins or vanilla commands.
            if (!managed) {
                notify(player, "This mob has no VanillaPlusSuite silence status to clear.");
                return;
            }
            mob.getPersistentDataContainer().remove(silenceKey);
            mob.setSilent(false);
            removeIndicator(mob.getUniqueId());
            consume(player, held);
            notify(player, "Mob sounds restored. Its custom name is unchanged.");
        }
        // Show the indicator promptly instead of waiting for the next scan.
        if (silencing && indicatorTask != null) updateIndicators();
    }

    private void consume(Player player, ItemStack held) {
        if (player.getGameMode() == GameMode.CREATIVE
                || !plugin.getConfig().getBoolean("mob-silencing.consume-name-tag", true)) return;
        if (held.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else held.setAmount(held.getAmount() - 1);
    }

    private void notify(Player player, String text) {
        if (plugin.getConfig().getBoolean("mob-silencing.notify-player", true)) {
            player.sendMessage(ChatColor.GRAY + "[Vanilla+] " + ChatColor.YELLOW + text);
        }
    }

    private void updateIndicators() {
        if (!plugin.getConfig().getBoolean("mob-silencing.enabled", true)
                || !plugin.getConfig().getBoolean("mob-silencing.indicator.enabled", true)) {
            removeAllIndicators();
            return;
        }
        int range = Math.max(2, Math.min(64,
                plugin.getConfig().getInt("mob-silencing.indicator.visible-range", 16)));
        boolean showUnnamed = plugin.getConfig().getBoolean("mob-silencing.indicator.show-on-unnamed-mobs", true);
        Set<UUID> visibleMobs = new HashSet<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Entity candidate : viewer.getNearbyEntities(range, range, range)) {
                if (!(candidate instanceof LivingEntity mob)
                        || mob instanceof Player || mob instanceof ArmorStand
                        || !mob.getPersistentDataContainer().has(silenceKey, PersistentDataType.BYTE)) continue;
                if (!showUnnamed && (mob.customName() == null || !mob.isCustomNameVisible())) continue;
                if (mob.getLocation().distanceSquared(viewer.getLocation()) > (double) range * range) continue;
                visibleMobs.add(mob.getUniqueId());
                // Persistence is attached to the mob, never to the display entity.
                if (!mob.isSilent()) mob.setSilent(true);
                updateDisplay(mob);
            }
        }
        for (UUID mobId : Set.copyOf(indicators.keySet())) {
            if (!visibleMobs.contains(mobId)) removeIndicator(mobId);
        }
    }

    private void updateDisplay(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        Entity existing = indicators.containsKey(mobId) ? Bukkit.getEntity(indicators.get(mobId)) : null;
        TextDisplay display = existing instanceof TextDisplay text && text.isValid() ? text : null;
        if (display != null && !display.getWorld().equals(mob.getWorld())) {
            removeIndicator(mobId);
            display = null;
        }
        if (display == null) {
            display = mob.getWorld().spawn(indicatorLocation(mob), TextDisplay.class, spawned -> {
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setGravity(false);
                spawned.setPersistent(false);
                spawned.setShadowed(true);
                spawned.setSeeThrough(false);
                spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                spawned.setTeleportDuration(1);
            });
            indicators.put(mobId, display.getUniqueId());
        }
        String marker = plugin.getConfig().getString("mob-silencing.indicator.text", "[SILENCED]");
        String colorName = plugin.getConfig().getString("mob-silencing.indicator.color", "gray");
        NamedTextColor color = NamedTextColor.NAMES.value(colorName.toLowerCase(Locale.ROOT).replace('-', '_'));

        // Always keep the TextDisplay independent. Minecraft suppresses a mob's
        // native nametag when a display rides it, so passenger attachment cannot
        // preserve normal names reliably. Per-tick following keeps both visible.
        if (display.isInsideVehicle()) display.leaveVehicle();
        display.text(Component.text(marker, color == null ? NamedTextColor.GRAY : color));
        syncFallbackPosition(mob, display);
    }

    private void syncFallbackPosition(LivingEntity mob, TextDisplay display) {
        if (!mob.getWorld().equals(display.getWorld()) || display.isInsideVehicle()) return;
        Location target = indicatorLocation(mob);
        if (display.getLocation().distanceSquared(target) > 0.0004) {
            display.teleport(target);
        }
    }

    private void followFallbackIndicators() {
        // One position update per server tick for all nearby silenced mobs.
        // There are no attachments, so mounting, passengers, and native mob
        // names are unaffected. The slower scan manages visibility and cleanup.
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(indicators).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            Entity candidate = Bukkit.getEntity(entry.getValue());
            if (!(entity instanceof LivingEntity mob) || !mob.isValid()
                    || !(candidate instanceof TextDisplay display) || !display.isValid()) continue;
            if (display.isInsideVehicle()) display.leaveVehicle();
            syncFallbackPosition(mob, display);
        }
    }

    private Location indicatorLocation(LivingEntity mob) {
        // Keep the standalone subtitle immediately above the head, underneath
        // the native custom name. No passenger attachment is used.
        return mob.getLocation().add(0, mob.getHeight() + 0.05, 0);
    }

    private void removeIndicator(UUID mobId) {
        UUID id = indicators.remove(mobId);
        if (id == null) return;
        Entity entity = Bukkit.getEntity(id);
        if (entity != null && entity.isValid()) entity.remove();
    }

    private void removeAllIndicators() {
        for (UUID mobId : Set.copyOf(indicators.keySet())) removeIndicator(mobId);
    }
}
