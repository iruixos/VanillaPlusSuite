package com.iruixos.vanillaplussuite;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Opt-in Silk Touch recovery of normally unobtainable blocks, starting with budding amethyst. */
final class UnusualBlockBreakage implements Listener {
    private final JavaPlugin plugin;

    UnusualBlockBreakage(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("unusual-block-breakage.enabled", true)
                || !plugin.getConfig().getBoolean("unusual-block-breakage.budding-amethyst", true)) return;
        Block block = event.getBlock();
        if (block.getType() != Material.BUDDING_AMETHYST) return;
        if (!event.isDropItems()) return;
        if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!tool.getType().name().endsWith("_PICKAXE") || !tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        // Execute after Minecraft has actually broken the block. This never bypasses
        // protection-plugin cancellation, and does not duplicate the vanilla block loot.
        var location = block.getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (location.getBlock().getType() != Material.AIR) return;
            location.getWorld().dropItemNaturally(location.clone().add(0.5, 0.5, 0.5),
                    new ItemStack(Material.BUDDING_AMETHYST));
        });
    }
}
