package com.iruixos.vanillaplussuite;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * Applies per-item max_stack_size metadata to selected foods and filled buckets.
 *
 * No artificial use/placement or extra remainder spawning is performed: modern
 * Minecraft handles one bowl on consumption and one bucket on fluid placement,
 * including inventory-full drops. Manually granting a second remainder here
 * would duplicate items. This behaviour should be verified on the target Paper build.
 */
final class StackableConsumables implements Listener {
    private final VanillaPlusSuite plugin;
    private BukkitTask task;

    StackableConsumables(VanillaPlusSuite plugin) {
        this.plugin = plugin;
    }

    void restart() {
        stop();
        if (!enabled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                normalize(player.getInventory());
                // Includes the currently open chest, barrel, anvil, etc.
                if (player.getOpenInventory() != null) normalize(player.getOpenInventory().getTopInventory());
            }
        }, 20L, 40L);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("stackable-consumables.enabled", true);
    }

    private int stackSize(Material type) {
        if (!enabled()) return 0;
        String base = "stackable-consumables.";
        switch (type) {
            case MUSHROOM_STEW:
                return plugin.getConfig().getBoolean(base + "mushroom-stew", true) ? foodSize() : 0;
            case RABBIT_STEW:
                return plugin.getConfig().getBoolean(base + "rabbit-stew", true) ? foodSize() : 0;
            case BEETROOT_SOUP:
                return plugin.getConfig().getBoolean(base + "beetroot-soup", true) ? foodSize() : 0;
            case SUSPICIOUS_STEW:
                return plugin.getConfig().getBoolean(base + "suspicious-stew", true) ? foodSize() : 0;
            case WATER_BUCKET:
                return plugin.getConfig().getBoolean(base + "water-buckets", true) ? bucketSize() : 0;
            case LAVA_BUCKET:
                return plugin.getConfig().getBoolean(base + "lava-buckets", true) ? bucketSize() : 0;
            default:
                return 0;
        }
    }

    private int foodSize() {
        return Math.max(1, Math.min(99, plugin.getConfig().getInt("stackable-consumables.stew-stack-size", 16)));
    }

    private int bucketSize() {
        return Math.max(1, Math.min(99, plugin.getConfig().getInt("stackable-consumables.bucket-stack-size", 16)));
    }

    private boolean soup(Material type) {
        return type == Material.MUSHROOM_STEW || type == Material.RABBIT_STEW
                || type == Material.BEETROOT_SOUP || type == Material.SUSPICIOUS_STEW;
    }

    private ItemStack prepare(ItemStack original) {
        if (original == null || original.getType().isAir()) return original;
        int size = stackSize(original.getType());
        if (size <= 0) return original;
        ItemMeta previous = original.getItemMeta();
        if (previous == null) return original;
        boolean needsSize = !previous.hasMaxStackSize() || previous.getMaxStackSize() != size;
        boolean needsBowl = soup(original.getType()) && !previous.hasUseRemainder();
        if (!needsSize && !needsBowl) return original;
        ItemStack updated = original.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) return original;
        meta.setMaxStackSize(size);
        // Retain custom use remainders rather than replacing another plugin's metadata.
        if (needsBowl) meta.setUseRemainder(new ItemStack(Material.BOWL));
        updated.setItemMeta(meta);
        return updated;
    }

    private void normalize(Inventory inventory) {
        if (inventory == null || !enabled()) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ItemStack updated = prepare(item);
            if (updated != item) inventory.setItem(slot, updated);
        }
    }

    private void nextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !enabled()) return;
            normalize(player.getInventory());
            normalize(player.getOpenInventory().getTopInventory());
        });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        nextTick(event.getPlayer());
    }

    @EventHandler public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) nextTick(player);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) nextTick(player);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) nextTick(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        ItemStack changed = prepare(result);
        if (result != changed) event.getInventory().setResult(changed);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack old = item.getItemStack();
        ItemStack updated = prepare(old);
        if (old != updated) item.setItemStack(updated);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        Item item = event.getItem();
        ItemStack old = item.getItemStack();
        ItemStack updated = prepare(old);
        if (old != updated) item.setItemStack(updated);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (stackSize(event.getItem().getType()) <= 0) return;
        // Never change event.setItem(): Bukkit keeps the original item in the
        // source inventory if an event changes its item, causing duplication.
        // Normalize both inventories after the vanilla transfer completes.
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        Bukkit.getScheduler().runTask(plugin, () -> {
            normalize(source);
            normalize(destination);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        EquipmentSlot slot = event.getHand();
        if (slot == null) return;
        Player player = event.getPlayer();
        ItemStack held = slot == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        ItemStack updated = prepare(held);
        if (updated != held) {
            if (slot == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(updated);
            else player.getInventory().setItemInOffHand(updated);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        nextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        nextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        nextTick(event.getPlayer());
    }
}
