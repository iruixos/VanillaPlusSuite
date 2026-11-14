package com.iruixos.vanillaplussuite;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
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
 * Applies per-item max_stack_size metadata to selected foods, cake, and filled buckets.
 *
 * Normal consumables and fluid buckets retain vanilla remainder handling.
 * Powder snow buckets need a narrowly scoped workaround for Minecraft's
 * overstacked solid-bucket replacement logic (MC-279773).
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
            case CAKE:
                return plugin.getConfig().getBoolean(base + "cake", true) ? cakeSize() : 0;
            case MILK_BUCKET:
                return plugin.getConfig().getBoolean(base + "milk-buckets", true) ? bucketSize() : 0;
            case POWDER_SNOW_BUCKET:
                return plugin.getConfig().getBoolean(base + "powder-snow-buckets", true) ? bucketSize() : 0;
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

    private int cakeSize() {
        return Math.max(1, Math.min(99, plugin.getConfig().getInt("stackable-consumables.cake-stack-size", 16)));
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
        // Milk is consumed as food and needs a container remainder. Powder snow
        // is PLACED like a block, so assigning a use_remainder BUCKET to it can
        // double-apply vanilla's own replacement logic. Remove only the vanilla
        // BUCKET remainder previously written by VanillaPlusSuite 1.4.7.
        boolean needsBucket = original.getType() == Material.MILK_BUCKET && !previous.hasUseRemainder();
        boolean removeSnowRemainder = original.getType() == Material.POWDER_SNOW_BUCKET
                && previous.hasUseRemainder() && previous.getUseRemainder() != null
                && previous.getUseRemainder().getType() == Material.BUCKET
                && previous.getUseRemainder().getAmount() == 1;
        if (!needsSize && !needsBowl && !needsBucket && !removeSnowRemainder) return original;
        ItemStack updated = original.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) return original;
        meta.setMaxStackSize(size);
        // Retain custom use remainders rather than replacing another plugin's metadata.
        if (needsBowl) meta.setUseRemainder(new ItemStack(Material.BOWL));
        if (needsBucket) meta.setUseRemainder(new ItemStack(Material.BUCKET));
        if (removeSnowRemainder) meta.setUseRemainder(null);
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

        // Powder snow is a placeable solid, unlike water/lava. Its placement
        // is not reliably repaired using PlayerBucketEmptyEvent. Snapshot the
        // stack BEFORE vanilla handles the click, then confirm a real block
        // transition on the following tick before restoring any items.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || player.getGameMode() == GameMode.CREATIVE
                || stackSize(Material.POWDER_SNOW_BUCKET) <= 1
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || updated == null
                || updated.getType() != Material.POWDER_SNOW_BUCKET
                || updated.getAmount() <= 1) return;
        Block adjacent = clicked.getRelative(event.getBlockFace());
        Material clickedBefore = clicked.getType();
        Material adjacentBefore = adjacent.getType();
        final int originalSlot = player.getInventory().getHeldItemSlot();
        final ItemStack remainder = updated.clone();
        remainder.setAmount(updated.getAmount() - 1);
        final java.util.UUID playerId = player.getUniqueId();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player live = Bukkit.getPlayer(playerId);
            if (live == null || !live.isOnline() || !enabled()) return;
            // Only repair a COMPLETED placement. This also prevents adding
            // filled buckets after a protection plugin cancels the action.
            boolean placed = (clickedBefore != Material.POWDER_SNOW
                    && clicked.getType() == Material.POWDER_SNOW)
                    || (adjacentBefore != Material.POWDER_SNOW
                    && adjacent.getType() == Material.POWDER_SNOW)
                    || (clickedBefore == Material.CAULDRON
                    && clicked.getType() == Material.POWDER_SNOW_CAULDRON);
            if (!placed) return;
            if (slot == EquipmentSlot.HAND
                    && live.getInventory().getHeldItemSlot() != originalSlot) return;
            ItemStack actual = slot == EquipmentSlot.HAND
                    ? live.getInventory().getItemInMainHand()
                    : live.getInventory().getItemInOffHand();
            // When vanilla has replaced the entire overstacked solid bucket
            // with ONE empty bucket, relocate that one real bucket to the
            // inventory and restore only the filled buckets that were lost.
            if (actual.getType() != Material.BUCKET || actual.getAmount() != 1) return;
            if (slot == EquipmentSlot.HAND) live.getInventory().setItemInMainHand(remainder);
            else live.getInventory().setItemInOffHand(remainder);
            for (ItemStack overflow : live.getInventory()
                    .addItem(new ItemStack(Material.BUCKET)).values()) {
                live.getWorld().dropItemNaturally(live.getLocation(), overflow);
            }
            live.updateInventory();
        });
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
