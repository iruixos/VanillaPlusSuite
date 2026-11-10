package com.iruixos.vanillaplussuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clean-room dropped-item workstation feature. No other plugin's implementation is used.
 * The complete original Elytra and chestplate are encoded in item PDC so separation
 * restores enchantments, trims, damage, names, and other supported Bukkit metadata.
 */
public final class ArmoredElytras implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey originalElytraKey;
    private final NamespacedKey chestplateKey;
    private final NamespacedKey armorAttributeKey;
    private final NamespacedKey toughnessAttributeKey;
    private final NamespacedKey knockbackAttributeKey;
    private final Map<UUID, Long> recentDrops = new HashMap<>();
    private BukkitTask task;

    public ArmoredElytras(JavaPlugin plugin) {
        this.plugin = plugin;
        originalElytraKey = new NamespacedKey(plugin, "armored_elytra_original");
        chestplateKey = new NamespacedKey(plugin, "armored_elytra_chestplate");
        armorAttributeKey = new NamespacedKey(plugin, "armored_elytra_armor");
        toughnessAttributeKey = new NamespacedKey(plugin, "armored_elytra_toughness");
        knockbackAttributeKey = new NamespacedKey(plugin, "armored_elytra_knockback");
    }

    public void restart() {
        stop();
        if (!enabled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::processDrops, 10L, 10L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        recentDrops.clear();
    }

    private boolean enabled() { return plugin.getConfig().getBoolean("armored-elytras.enabled", true); }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (enabled()) recentDrops.put(event.getItemDrop().getUniqueId(), System.currentTimeMillis());
    }

    private void processDrops() {
        long now = System.currentTimeMillis();
        recentDrops.entrySet().removeIf(entry -> now - entry.getValue() > 60000L
                || Bukkit.getEntity(entry.getKey()) == null);
        List<Item> items = new ArrayList<>();
        for (UUID id : recentDrops.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Item item && item.isValid() && item.isOnGround()) items.add(item);
        }
        // One workstation operation per dropped item; consumed items cannot be processed again.
        for (Item item : items) {
            if (!item.isValid() || !recentDrops.containsKey(item.getUniqueId())) continue;
            Material support = item.getLocation().getBlock().getRelative(0, -1, 0).getType();
            if (isAnvil(support) && plugin.getConfig().getBoolean("armored-elytras.anvil-combining", true)) {
                combine(item, items);
            } else if (support == Material.GRINDSTONE
                    && plugin.getConfig().getBoolean("armored-elytras.grindstone-separation", true)) {
                split(item);
            }
        }
    }

    private static boolean isAnvil(Material type) {
        return type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL;
    }

    private void combine(Item first, List<Item> candidates) {
        ItemStack firstStack = first.getItemStack();
        if (firstStack.getType() != Material.ELYTRA && !isChestplate(firstStack.getType())) return;
        for (Item second : candidates) {
            if (first == second || !second.isValid() || first.getWorld() != second.getWorld()
                    || first.getLocation().distanceSquared(second.getLocation()) > 2.25) continue;
            ItemStack secondStack = second.getItemStack();
            Item elytraEntity = firstStack.getType() == Material.ELYTRA ? first : second;
            Item chestEntity = firstStack.getType() == Material.ELYTRA ? second : first;
            ItemStack elytra = elytraEntity.getItemStack();
            ItemStack chest = chestEntity.getItemStack();
            if (elytra.getType() != Material.ELYTRA || !isChestplate(chest.getType()) || isCombined(elytra)) continue;
            try {
                ItemStack originalElytra = elytra.clone(); originalElytra.setAmount(1);
                ItemStack originalChest = chest.clone(); originalChest.setAmount(1);
                ItemStack combined = createCombined(originalElytra, originalChest);
                Location dropAt = first.getLocation().clone().add(0, 0.15, 0);
                consumeOne(elytraEntity);
                consumeOne(chestEntity);
                first.getWorld().dropItem(dropAt, combined).setPickupDelay(20);
                return;
            } catch (IOException e) {
                plugin.getLogger().warning("Could not combine Elytra and chestplate: " + e.getMessage());
                return;
            }
        }
    }

    private ItemStack createCombined(ItemStack elytra, ItemStack chest) throws IOException {
        ItemStack result = elytra.clone();
        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(originalElytraKey, PersistentDataType.STRING, encode(elytra));
        meta.getPersistentDataContainer().set(chestplateKey, PersistentDataType.STRING, encode(chest));
        // Use chest-slot attributes. The Elytra keeps its normal flight mechanics.
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(armorAttributeKey,
                armorValue(chest.getType()), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        double toughness = toughnessValue(chest.getType());
        if (toughness > 0) meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                new AttributeModifier(toughnessAttributeKey, toughness,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        if (chest.getType() == Material.NETHERITE_CHESTPLATE) meta.addAttributeModifier(
                Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(knockbackAttributeKey, 0.1,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        // Armor enchantments need to be present on the worn Elytra to take effect.
        // The originals stored above are returned verbatim when separating.
        if (chest.hasItemMeta()) {
            chest.getEnchantments().forEach((enchantment, level) -> {
                int current = meta.getEnchantLevel(enchantment);
                if (level > current) meta.addEnchant(enchantment, level, true);
            });
        }
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.GOLD + "Armored Elytra (" + friendlyName(chest.getType()) + ")");
        lore.add(ChatColor.GRAY + "Drop onto a grindstone to separate");
        meta.setLore(lore);
        result.setItemMeta(meta);
        return result;
    }

    private void split(Item entity) {
        ItemStack stack = entity.getItemStack();
        if (!isCombined(stack)) return;
        try {
            ItemMeta meta = stack.getItemMeta();
            ItemStack elytra = decode(meta.getPersistentDataContainer().get(originalElytraKey, PersistentDataType.STRING));
            ItemStack chest = decode(meta.getPersistentDataContainer().get(chestplateKey, PersistentDataType.STRING));
            if (elytra.getType() != Material.ELYTRA || !isChestplate(chest.getType())) return;
            // Carry forward durability used while flying with the combined item.
            if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable currentDamage
                    && elytra.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable originalDamage) {
                originalDamage.setDamage(currentDamage.getDamage());
                elytra.setItemMeta(originalDamage);
            }
            Location location = entity.getLocation().clone().add(0, .15, 0);
            World world = entity.getWorld();
            // The combined Elytra is unstackable; one input gives exactly two outputs.
            recentDrops.remove(entity.getUniqueId());
            entity.remove();
            world.dropItem(location, elytra).setPickupDelay(20);
            world.dropItem(location, chest).setPickupDelay(20);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not separate an Armored Elytra; item left unchanged: " + ex.getMessage());
        }
    }

    // The GUI and dropped-item methods share createCombined() and the same stored originals.
    // GUI results are produced in Prepare events, but *taken* in a cancelled click transaction,
    // to ensure all inputs are consumed exactly once and the grindstone never strips enchantments.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!enabled() || !plugin.getConfig().getBoolean("armored-elytras.anvil-gui-combining", true)) return;
        Inventory top = event.getInventory();
        ItemStack left = top.getItem(0);
        ItemStack right = top.getItem(1);
        if (!validPair(left, right)) return;
        try {
            ItemStack result = makeFromPair(left, right);
            // A non-negative cost is needed for the server to allow a custom anvil result.
            // No XP is charged: drop-based combining is also free.
            event.getView().setRepairCost(0);
            event.setResult(result);
        } catch (IOException ex) {
            event.setResult(null);
            plugin.getLogger().warning("Unable to prepare Armored Elytra: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!enabled() || !plugin.getConfig().getBoolean("armored-elytras.grindstone-gui-separation", true)) return;
        Inventory top = event.getInventory();
        ItemStack first = top.getItem(0);
        ItemStack second = top.getItem(1);
        // The second input must be empty, otherwise ordinary grinding can operate normally.
        if (!isCombinedSafe(first) || !empty(second)) return;
        try {
            ItemStack[] originals = recoverOriginals(first);
            event.setResult(originals[0]);
        } catch (Exception ex) {
            event.setResult(null);
            plugin.getLogger().warning("Unable to preview Elytra separation: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorkstationResultClick(InventoryClickEvent event) {
        if (!enabled() || !(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() != 2) return;
        Inventory top = event.getView().getTopInventory();
        boolean anvil = top.getType() == InventoryType.ANVIL;
        boolean grindstone = top.getType() == InventoryType.GRINDSTONE;
        if (!anvil && !grindstone) return;

        ItemStack a = top.getItem(0);
        ItemStack b = top.getItem(1);
        boolean combine = anvil && plugin.getConfig().getBoolean("armored-elytras.anvil-gui-combining", true)
                && validPair(a, b);
        boolean separate = grindstone && plugin.getConfig().getBoolean("armored-elytras.grindstone-gui-separation", true)
                && isCombinedSafe(a) && empty(b);
        if (!combine && !separate) return;

        // Own the custom result click completely; vanilla transactions would otherwise
        // consume inputs or disenchant the Elytra before the chestplate can be recovered.
        event.setCancelled(true);
        ClickType click = event.getClick();
        boolean shift = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        if (!shift && click != ClickType.LEFT && click != ClickType.RIGHT) return;
        if (!shift && !empty(event.getCursor())) return;
        if (shift && player.getInventory().firstEmpty() == -1) return;

        try {
            ItemStack result;
            ItemStack chest = null;
            if (combine) {
                result = makeFromPair(a, b);
            } else {
                ItemStack[] originals = recoverOriginals(a);
                result = originals[0];
                chest = originals[1];
            }
            // Never issue an item if another plugin or the server changed the preview.
            ItemStack preview = top.getItem(2);
            if (empty(preview) || !preview.isSimilar(result)) return;

            // All validations have succeeded; consume before issuing results.
            top.setItem(0, null);
            if (combine) top.setItem(1, null);
            top.setItem(2, null);
            if (shift) {
                // We already verified a free slot. If another inventory handler changes
                // capacity unexpectedly, any remainder is dropped, never silently lost.
                giveOrDrop(player, result);
            } else {
                player.setItemOnCursor(result);
            }
            if (chest != null) giveOrDrop(player, chest);
            player.updateInventory();
        } catch (Exception ex) {
            plugin.getLogger().warning("Armored Elytra GUI transaction cancelled: " + ex.getMessage());
        }
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() < 1;
    }

    private boolean isCombinedSafe(ItemStack item) {
        return !empty(item) && isCombined(item) && item.getAmount() == 1;
    }

    private boolean validPair(ItemStack first, ItemStack second) {
        if (empty(first) || empty(second) || first.getAmount() != 1 || second.getAmount() != 1) return false;
        boolean orderA = first.getType() == Material.ELYTRA && isChestplate(second.getType());
        boolean orderB = second.getType() == Material.ELYTRA && isChestplate(first.getType());
        return (orderA || orderB) && !isCombinedSafe(first) && !isCombinedSafe(second);
    }

    private ItemStack makeFromPair(ItemStack first, ItemStack second) throws IOException {
        ItemStack elytra = first.getType() == Material.ELYTRA ? first : second;
        ItemStack chest = first.getType() == Material.ELYTRA ? second : first;
        return createCombined(elytra.clone(), chest.clone());
    }

    private ItemStack[] recoverOriginals(ItemStack combined) throws IOException, ClassNotFoundException {
        ItemMeta meta = combined.getItemMeta();
        ItemStack elytra = decode(meta.getPersistentDataContainer().get(originalElytraKey, PersistentDataType.STRING));
        ItemStack chest = decode(meta.getPersistentDataContainer().get(chestplateKey, PersistentDataType.STRING));
        if (elytra.getType() != Material.ELYTRA || !isChestplate(chest.getType())
                || elytra.getAmount() != 1 || chest.getAmount() != 1) {
            throw new IOException("Invalid original items in Armored Elytra");
        }
        if (combined.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable currentDamage
                && elytra.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable originalDamage) {
            originalDamage.setDamage(currentDamage.getDamage());
            elytra.setItemMeta(originalDamage);
        }
        return new ItemStack[] {elytra, chest};
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> remainder = player.getInventory().addItem(stack);
        for (ItemStack leftover : remainder.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover).setPickupDelay(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDroppedItemBurn(EntityDamageEvent event) {
        if (!enabled() || !plugin.getConfig().getBoolean("armored-elytras.netherite-fire-salvage", true)
                || !(event.getEntity() instanceof Item item)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FIRE
                && event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK
                && event.getCause() != EntityDamageEvent.DamageCause.LAVA) return;
        ItemStack combined = item.getItemStack();
        if (!isCombined(combined)) return;
        try {
            ItemStack chest = decode(combined.getItemMeta().getPersistentDataContainer()
                    .get(chestplateKey, PersistentDataType.STRING));
            if (chest.getType() != Material.NETHERITE_CHESTPLATE) return;
            event.setCancelled(true);
            Location location = item.getLocation().clone();
            recentDrops.remove(item.getUniqueId());
            item.remove();
            // Netherite items survive ordinary fire/lava; the Elytra is intentionally lost.
            location.getWorld().dropItem(location, chest).setPickupDelay(20);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not recover netherite chestplate: " + ex.getMessage());
        }
    }

    private boolean isCombined(ItemStack item) {
        if (item.getType() != Material.ELYTRA || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(originalElytraKey, PersistentDataType.STRING)
                && meta.getPersistentDataContainer().has(chestplateKey, PersistentDataType.STRING);
    }

    private void consumeOne(Item entity) {
        recentDrops.remove(entity.getUniqueId());
        ItemStack stack = entity.getItemStack();
        if (stack.getAmount() <= 1) entity.remove();
        else { stack.setAmount(stack.getAmount() - 1); entity.setItemStack(stack); }
    }

    private static boolean isChestplate(Material type) {
        return type == Material.LEATHER_CHESTPLATE || type == Material.CHAINMAIL_CHESTPLATE
                || type == Material.IRON_CHESTPLATE || type == Material.GOLDEN_CHESTPLATE
                || type == Material.DIAMOND_CHESTPLATE || type == Material.NETHERITE_CHESTPLATE;
    }

    private static double armorValue(Material type) {
        return switch (type) {
            case LEATHER_CHESTPLATE -> 3;
            case CHAINMAIL_CHESTPLATE, GOLDEN_CHESTPLATE -> 5;
            case IRON_CHESTPLATE -> 6;
            case DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE -> 8;
            default -> 0;
        };
    }

    private static double toughnessValue(Material type) {
        return switch (type) {
            case DIAMOND_CHESTPLATE -> 2;
            case NETHERITE_CHESTPLATE -> 3;
            default -> 0;
        };
    }

    private static String friendlyName(Material material) {
        String name = material.name().replace("_CHESTPLATE", "").replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String encode(ItemStack stack) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream stream = new BukkitObjectOutputStream(bytes)) {
            stream.writeObject(stack);
            stream.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }

    private static ItemStack decode(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.length() > 200000) throw new IOException("Missing or oversized stored item");
        try (BukkitObjectInputStream stream = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            Object value = stream.readObject();
            if (!(value instanceof ItemStack stack)) throw new IOException("Invalid stored item");
            return stack;
        }
    }
}
