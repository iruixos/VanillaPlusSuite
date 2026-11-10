package com.iruixos.vanillaplussuite;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.ChatColor;
import java.util.*;

public final class VanillaPlusSuite extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Location> deaths = new HashMap<>();
    private final Map<String, Long> notices = new HashMap<>();
    private BukkitTask harvestTask;
    private final Map<Location, BukkitTask> ironDoorCloseTasks = new HashMap<>();
    private final Random random = new Random();
    private NamespacedKey signOwnerKey;
    private BeeColonyCooperation beeColony;
    private AfkDetection afkDetection;
    private ArmoredElytras armoredElytras;

    @Override public void onEnable() {
        saveDefaultConfig();
        signOwnerKey = new NamespacedKey(this, "sign_owner");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("vps")).setExecutor(this);
        Objects.requireNonNull(getCommand("sign")).setExecutor(this);
        Objects.requireNonNull(getCommand("afk")).setExecutor(this);
        startHarvestTask();
        beeColony = new BeeColonyCooperation(this);
        getServer().getPluginManager().registerEvents(beeColony, this);
        beeColony.restart();
        afkDetection = new AfkDetection(this);
        getServer().getPluginManager().registerEvents(afkDetection, this);
        afkDetection.restart();
        armoredElytras = new ArmoredElytras(this);
        getServer().getPluginManager().registerEvents(armoredElytras, this);
        armoredElytras.restart();
        printStartupBanner();
    }
    /** Always prints a compact banner when the plugin is enabled. */
    private void printStartupBanner() {
        String pluginVersion = getPluginMeta().getVersion();
        String minecraftVersion = Bukkit.getMinecraftVersion();
        String[] lines = {
                "[V+] VanillaPlusSuite v" + pluginVersion,
                "     by iruixos",
                "     Paper " + minecraftVersion + " / MC " + minecraftVersion
        };
        int width = 0;
        for (String line : lines) width = Math.max(width, line.length());
        String border = "+" + "-".repeat(width + 2) + "+";

        getLogger().info("");
        getLogger().info("  " + border);
        for (String line : lines) {
            getLogger().info("  | " + String.format(Locale.ROOT, "%-" + width + "s", line) + " |");
        }
        getLogger().info("  " + border);
        getLogger().info("");
    }

    @Override public void onDisable() {
        if (harvestTask != null) harvestTask.cancel();
        cancelAllIronDoorTimers();
        if (beeColony != null) beeColony.stop();
        if (afkDetection != null) afkDetection.stop();
        if (armoredElytras != null) armoredElytras.stop();
    }

    private void cancelAllIronDoorTimers() {
        for (BukkitTask task : ironDoorCloseTasks.values()) task.cancel();
        ironDoorCloseTasks.clear();
    }

    private void cancelIronDoorTimer(Location location) {
        BukkitTask previous = ironDoorCloseTasks.remove(location);
        if (previous != null) previous.cancel();
    }

    private boolean on(String module) { return getConfig().getBoolean(module + ".enabled", true); }
    private void message(Player p, String text) { p.sendMessage(ChatColor.GRAY + "[Vanilla+] " + ChatColor.YELLOW + text); }
    private boolean cooldown(String key, int seconds) {
        long now = System.currentTimeMillis();
        if (now - notices.getOrDefault(key, 0L) < seconds * 1000L) return false;
        notices.put(key, now); return true;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("afk")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /afk.");
                return true;
            }
            if (args.length != 0) {
                message(player, "Usage: /afk");
                return true;
            }
            if (afkDetection == null || !afkDetection.isRunning()) {
                message(player, "AFK detection is disabled.");
                return true;
            }
            boolean isAfk = afkDetection.toggleManualAfk(player);
            if (isAfk) message(player, "You are now marked as AFK.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("sign")) return signCommand(sender, args);
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig(); startHarvestTask(); cancelAllIronDoorTimers(); if (beeColony != null) beeColony.restart(); if (afkDetection != null) afkDetection.restart(); if (armoredElytras != null) armoredElytras.restart(); sender.sendMessage("VanillaPlusSuite configuration reloaded."); return true;
        }
        sender.sendMessage("Usage: /vps reload"); return true;
    }
    private boolean signCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can edit signs."); return true; }
        if (!on("sign-editing") || !getConfig().getBoolean("sign-editing.commands.enabled", true)) {
            message(player, "Sign commands are disabled."); return true;
        }
        if (args.length == 0) { message(player, "Usage: /sign <color|bold|italic|reset> [color] [line 1-4]"); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        boolean colorAction = action.equals("color");
        if (!colorAction && !Set.of("bold", "italic", "reset").contains(action)) {
            message(player, "Usage: /sign <color|bold|italic|reset> [color] [line 1-4]"); return true;
        }
        int lineIndex = -1;
        int lineArg = colorAction ? 2 : 1;
        if (args.length > lineArg + 1 || (colorAction && args.length < 2)) {
            message(player, "Invalid arguments. Optional line number must be 1-4."); return true;
        }
        if (args.length == lineArg + 1) {
            try { lineIndex = Integer.parseInt(args[lineArg]) - 1; }
            catch (NumberFormatException ex) { message(player, "Line must be a number from 1 to 4."); return true; }
            if (lineIndex < 0 || lineIndex > 3) { message(player, "Line must be from 1 to 4."); return true; }
        }
        TextColor chosenColor = null;
        if (colorAction) {
            String colorInput = args[1];
            boolean hex = colorInput.matches("(?i)#[0-9a-f]{6}");
            if (hex) {
                if (!getConfig().getBoolean("sign-editing.allow-hex-colors", false)
                        || !player.hasPermission("vanillaplussuite.sign.hex")) {
                    message(player, "You cannot use hexadecimal sign colors."); return true;
                }
                chosenColor = TextColor.fromHexString(colorInput);
            } else {
                if (!getConfig().getBoolean("sign-editing.allow-color-codes", true)
                        || !player.hasPermission("vanillaplussuite.sign.color")) {
                    message(player, "You cannot use standard sign colors."); return true;
                }
                chosenColor = NamedTextColor.NAMES.value(colorInput.toLowerCase(Locale.ROOT).replace('-', '_'));
                if (chosenColor == null) { message(player, "Unknown color. Use a name like red, dark_blue, or #FF69B4."); return true; }
            }
        } else if (!player.hasPermission("vanillaplussuite.sign.command")) {
            message(player, "You do not have permission to edit sign styling."); return true;
        } else if (!action.equals("reset") && (!getConfig().getBoolean("sign-editing.allow-format-codes", false)
                || !player.hasPermission("vanillaplussuite.sign.format"))) {
            message(player, "Sign text formatting is disabled or not permitted."); return true;
        }
        Block block = player.getTargetBlockExact(Math.max(1, Math.min(12, getConfig().getInt("sign-editing.commands.max-distance", 6))));
        if (block == null || !(block.getState() instanceof Sign sign)) {
            message(player, "Look directly at a sign within range."); return true;
        }
        if (sign.isWaxed()) { message(player, "This sign is waxed and cannot be edited."); return true; }
        // Fail closed on untracked signs to avoid bypassing protection plugins.
        String owner = sign.getPersistentDataContainer().get(signOwnerKey, PersistentDataType.STRING);
        if (!player.hasPermission("vanillaplussuite.sign.admin")
                && (owner == null || !owner.equals(player.getUniqueId().toString()))) {
            message(player, "Only the recorded sign owner may use this command. Existing untracked signs require an administrator."); return true;
        }
        org.bukkit.block.sign.Side side = org.bukkit.block.sign.Side.FRONT;
        org.bukkit.block.sign.SignSide signSide = sign.getSide(side);
        for (int i = 0; i < 4; i++) {
            if (lineIndex != -1 && i != lineIndex) continue;
            Component original = signSide.line(i);
            Component updated;
            switch (action) {
                case "color" -> updated = original.color(chosenColor);
                case "bold" -> updated = original.decoration(TextDecoration.BOLD,
                        original.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE
                                ? TextDecoration.State.FALSE : TextDecoration.State.TRUE);
                case "italic" -> updated = original.decoration(TextDecoration.ITALIC,
                        original.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE
                                ? TextDecoration.State.FALSE : TextDecoration.State.TRUE);
                default -> updated = Component.text().append(original.color(null)
                        .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE)
                        .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
                        .decoration(TextDecoration.UNDERLINED, TextDecoration.State.FALSE)
                        .decoration(TextDecoration.STRIKETHROUGH, TextDecoration.State.FALSE)
                        .decoration(TextDecoration.OBFUSCATED, TextDecoration.State.FALSE)).build();
            }
            signSide.line(i, updated);
        }
        sign.update(true, false);
        message(player, "Sign " + action + " applied to " + (lineIndex == -1 ? "all lines" : "line " + (lineIndex + 1)) + ".");
        return true;
    }

    @EventHandler(ignoreCancelled=true) public void onAnvil(InventoryClickEvent e) {
        if (!on("anvil-preservation") || !getConfig().getBoolean("anvil-preservation.warn-on-use")) return;
        if (!(e.getWhoClicked() instanceof Player p) || e.getView().getTopInventory().getType() != InventoryType.ANVIL) return;
        if (e.getRawSlot() != 2 || e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return;
        Location loc = p.getLocation();
        Block target = loc.getBlock();
        // InventoryView does not guarantee the exact anvil location; inspect nearby anvils only.
        boolean damaged = false;
        for (int x=-3;x<=3 && !damaged;x++) for(int y=-2;y<=2 && !damaged;y++) for(int z=-3;z<=3;z++) {
            Material type = target.getRelative(x,y,z).getType();
            if (type == Material.DAMAGED_ANVIL) { damaged=true; break; }
        }
        if (damaged && cooldown("anvil:"+p.getUniqueId(), getConfig().getInt("anvil-preservation.cooldown-seconds",30)))
            message(p,"Warning: a nearby anvil is heavily damaged and may break during use.");
    }
    private void startHarvestTask() {
        if (harvestTask != null) harvestTask.cancel();
        if (!on("harvest-indicators")) return;
        long period = Math.max(20L, getConfig().getLong("harvest-indicators.interval-ticks",60));
        harvestTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int radius = Math.max(1, Math.min(16, getConfig().getInt("harvest-indicators.radius",8)));
            int max = Math.max(1, Math.min(1000, getConfig().getInt("harvest-indicators.max-blocks-per-player",300)));
            double chance = Math.max(0,Math.min(1,getConfig().getDouble("harvest-indicators.particle-chance",.1)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                Location center = p.getLocation(); World w = p.getWorld(); int scanned=0;
                // Random sampling prevents scanning an entire cube every tick.
                for (int i=0;i<max;i++) {
                    int x=random.nextInt(radius*2+1)-radius, y=random.nextInt(7)-3, z=random.nextInt(radius*2+1)-radius;
                    Block b=w.getBlockAt(center.getBlockX()+x,center.getBlockY()+y,center.getBlockZ()+z);
                    if (!(b.getBlockData() instanceof Ageable age) || age.getAge()!=age.getMaximumAge()) continue;
                    if (!Set.of(Material.WHEAT,Material.CARROTS,Material.POTATOES,Material.BEETROOTS,Material.NETHER_WART).contains(b.getType())) continue;
                    if (random.nextDouble()<chance) p.spawnParticle(Particle.HAPPY_VILLAGER,b.getLocation().add(.5,.8,.5),1,.15,.1,.15,0);
                    scanned++;
                }
            }
        }, period, period);
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGH) public void onPetAttack(EntityDamageByEntityEvent e) {
        if (!on("pet-protection") || !(e.getEntity() instanceof Tameable pet) || !pet.isTamed() || pet.getOwner()==null) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        boolean own = pet.getOwner().getUniqueId().equals(attacker.getUniqueId());
        if ((own && getConfig().getBoolean("pet-protection.protect-own",true)) || (!own && getConfig().getBoolean("pet-protection.protect-others",false))) {
            e.setCancelled(true);
            if (getConfig().getBoolean("pet-protection.notify-attacker",true) && cooldown("pet:"+attacker.getUniqueId(),10)) message(attacker,"Direct attacks on this tamed animal are protected.");
        }
    }
    // Manual iron-door interaction; vanilla redstone updates remain untouched.
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onIronDoorInteract(PlayerInteractEvent event) {
        if (!on("iron-door-interaction") || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.IRON_DOOR
                || !(clicked.getBlockData() instanceof Door clickedData)) return;

        Block lower = clickedData.getHalf() == Bisected.Half.BOTTOM
                ? clicked : clicked.getRelative(BlockFace.DOWN);
        Block upper = lower.getRelative(BlockFace.UP);
        if (lower.getType() != Material.IRON_DOOR || upper.getType() != Material.IRON_DOOR
                || !(lower.getBlockData() instanceof Door door)) return;

        // A powered door is always controlled by redstone, not manual clicks.
        if (getConfig().getBoolean("iron-door-interaction.respect-redstone", true)
                && (lower.isBlockPowered() || lower.isBlockIndirectlyPowered()
                || upper.isBlockPowered() || upper.isBlockIndirectlyPowered()
                || door.isPowered())) return;

        // Synchronize the two halves. Setting only the lower block data can
        // leave the upper half visually closed on clients.
        if (!(upper.getBlockData() instanceof Door upperDoor)
                || upperDoor.getHalf() != Bisected.Half.TOP
                || door.getHalf() != Bisected.Half.BOTTOM) return;

        boolean opening = !door.isOpen();
        door.setOpen(opening);
        upperDoor.setOpen(opening);
        lower.setBlockData(door, false);
        upper.setBlockData(upperDoor, false);
        if (getConfig().getBoolean("iron-door-interaction.play-sound", true)) {
            lower.getWorld().playSound(lower.getLocation(),
                    opening ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE,
                    1.0f, 1.0f);
        }
        event.setUseInteractedBlock(Event.Result.DENY);

        // Only manually opened doors receive a timer. Manual closing cancels it.
        Location key = lower.getLocation();
        cancelIronDoorTimer(key);
        if (opening && getConfig().getBoolean("iron-door-interaction.auto-close.enabled", false)) {
            long seconds = Math.max(1L, Math.min(86400L,
                    getConfig().getLong("iron-door-interaction.auto-close.delay-seconds", 5L)));
            // A scheduler tick is 1/20 second. Work only on the main thread.
            BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
                ironDoorCloseTasks.remove(key);
                if (!on("iron-door-interaction")
                        || !getConfig().getBoolean("iron-door-interaction.auto-close.enabled", false)) return;
                Block bottomBlock = key.getBlock();
                Block topBlock = bottomBlock.getRelative(BlockFace.UP);
                if (bottomBlock.getType() != Material.IRON_DOOR
                        || topBlock.getType() != Material.IRON_DOOR
                        || !(bottomBlock.getBlockData() instanceof Door bottomDoor)
                        || !(topBlock.getBlockData() instanceof Door topDoor)
                        || bottomDoor.getHalf() != Bisected.Half.BOTTOM
                        || topDoor.getHalf() != Bisected.Half.TOP
                        || !bottomDoor.isOpen() || !topDoor.isOpen()) return;
                // Do not override active redstone, even if the door was opened manually.
                if (bottomBlock.isBlockPowered() || bottomBlock.isBlockIndirectlyPowered()
                        || topBlock.isBlockPowered() || topBlock.isBlockIndirectlyPowered()
                        || bottomDoor.isPowered() || topDoor.isPowered()) return;
                bottomDoor.setOpen(false);
                topDoor.setOpen(false);
                bottomBlock.setBlockData(bottomDoor, false);
                topBlock.setBlockData(topDoor, false);
                if (getConfig().getBoolean("iron-door-interaction.play-sound", true)) {
                    bottomBlock.getWorld().playSound(bottomBlock.getLocation(),
                            Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.0f);
                }
            }, seconds * 20L);
            ironDoorCloseTasks.put(key, task);
        }
    }

    // Player-only iron trapdoor interaction; uses the same auto-close settings as iron doors.
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onIronTrapdoorInteract(PlayerInteractEvent event) {
        if (!on("iron-door-interaction")
                || !getConfig().getBoolean("iron-door-interaction.include-trapdoors", true)
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.IRON_TRAPDOOR
                || !(block.getBlockData() instanceof TrapDoor trapdoor)) return;

        if (getConfig().getBoolean("iron-door-interaction.respect-redstone", true)
                && (block.isBlockPowered() || block.isBlockIndirectlyPowered()
                || trapdoor.isPowered())) return;

        boolean opening = !trapdoor.isOpen();
        trapdoor.setOpen(opening);
        block.setBlockData(trapdoor, false);
        if (getConfig().getBoolean("iron-door-interaction.play-sound", true)) {
            block.getWorld().playSound(block.getLocation(),
                    opening ? Sound.BLOCK_IRON_TRAPDOOR_OPEN : Sound.BLOCK_IRON_TRAPDOOR_CLOSE,
                    1.0f, 1.0f);
        }
        event.setUseInteractedBlock(Event.Result.DENY);

        Location key = block.getLocation();
        cancelIronDoorTimer(key);
        if (opening && getConfig().getBoolean("iron-door-interaction.auto-close.enabled", false)) {
            long seconds = Math.max(1L, Math.min(86400L,
                    getConfig().getLong("iron-door-interaction.auto-close.delay-seconds", 5L)));
            BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
                ironDoorCloseTasks.remove(key);
                if (!on("iron-door-interaction")
                        || !getConfig().getBoolean("iron-door-interaction.include-trapdoors", true)
                        || !getConfig().getBoolean("iron-door-interaction.auto-close.enabled", false)) return;
                Block current = key.getBlock();
                if (current.getType() != Material.IRON_TRAPDOOR
                        || !(current.getBlockData() instanceof TrapDoor currentData)
                        || !currentData.isOpen()) return;
                if (current.isBlockPowered() || current.isBlockIndirectlyPowered()
                        || currentData.isPowered()) return;
                currentData.setOpen(false);
                current.setBlockData(currentData, false);
                if (getConfig().getBoolean("iron-door-interaction.play-sound", true)) {
                    current.getWorld().playSound(current.getLocation(),
                            Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.0f, 1.0f);
                }
            }, seconds * 20L);
            ironDoorCloseTasks.put(key, task);
        }
    }

    @EventHandler(ignoreCancelled=true) public void onFrameRotate(PlayerInteractEntityEvent e) {
        if (!on("item-frame-lock") || !getConfig().getBoolean("item-frame-lock.require-sneak-to-rotate",true)) return;
        if (!(e.getRightClicked() instanceof ItemFrame frame) || e.getPlayer().isSneaking()) return;
        if (!frame.getItem().getType().isAir()) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void onFrameRemove(EntityDamageByEntityEvent e) {
        if (!on("item-frame-lock") || !getConfig().getBoolean("item-frame-lock.require-sneak-to-remove",false)) return;
        if (e.getEntity() instanceof ItemFrame && e.getDamager() instanceof Player p && !p.isSneaking()) e.setCancelled(true);
    }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        DeathMessages.broadcast(this, e, random);
        if (!on("grave-coordinates")) return;
        Player p=e.getEntity(); Location l=p.getLocation().clone(); deaths.put(p.getUniqueId(),l);
        // Coordinates are sent privately after respawn, not on death.
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        if (!on("grave-coordinates") || !getConfig().getBoolean("grave-coordinates.send-on-respawn",true)) return;
        Location l=deaths.get(e.getPlayer().getUniqueId());
        if(l!=null) Bukkit.getScheduler().runTask(this,()->sendDeath(e.getPlayer(),l));
    }
    private void sendDeath(Player p, Location l) {
        message(p,"Death location: " + l.getWorld().getName()+" | X: "+l.getBlockX()+" Y: "+l.getBlockY()+" Z: "+l.getBlockZ());
    }
    @EventHandler(ignoreCancelled=true) public void onItemDamage(PlayerItemDamageEvent e) {
        if (!on("tool-durability-warning")) return;
        ItemStack item=e.getItem(); if (!(item.getItemMeta() instanceof Damageable d)) return;
        int max=item.getType().getMaxDurability(); if(max<=0) return;
        int remaining=max-d.getDamage()-e.getDamage();
        int threshold=Math.max(1,Math.min(99,getConfig().getInt("tool-durability-warning.threshold-percent",10)));
        if (remaining>0 && remaining*100.0/max<=threshold && cooldown("durability:"+e.getPlayer().getUniqueId()+":"+item.getType(),getConfig().getInt("tool-durability-warning.cooldown-seconds",45)))
            message(e.getPlayer(),"Low durability: "+item.getType().name().toLowerCase(Locale.ROOT).replace('_',' ')+" ("+remaining+"/"+max+").");
    }
    @EventHandler(ignoreCancelled=true) public void onSign(SignChangeEvent e) {
        if (!on("sign-editing")) return;
        if (e.getBlock().getState() instanceof Sign sign) {
            if (!sign.getPersistentDataContainer().has(signOwnerKey, PersistentDataType.STRING)) {
                sign.getPersistentDataContainer().set(signOwnerKey, PersistentDataType.STRING,
                        e.getPlayer().getUniqueId().toString());
                sign.update(true, false);
            }
        }
        boolean colors = getConfig().getBoolean("sign-editing.allow-color-codes", true)
                && e.getPlayer().hasPermission("vanillaplussuite.sign.color");
        boolean format = getConfig().getBoolean("sign-editing.allow-format-codes", false)
                && e.getPlayer().hasPermission("vanillaplussuite.sign.format");
        boolean hex = getConfig().getBoolean("sign-editing.allow-hex-colors", false)
                && e.getPlayer().hasPermission("vanillaplussuite.sign.hex");
        if (!colors && !format && !hex) return;
        for (int i = 0; i < 4; i++) {
            String line = e.getLine(i);
            if (line == null || line.isEmpty()) continue;
            StringBuilder out = new StringBuilder();
            for (int j = 0; j < line.length(); j++) {
                if (line.charAt(j) == '&') {
                    // Custom RGB syntax: &#RRGGBB (six hexadecimal digits).
                    if (hex && j + 8 <= line.length() && line.charAt(j + 1) == '#') {
                        String rgb = line.substring(j + 2, j + 8);
                        if (rgb.matches("(?i)[0-9a-f]{6}")) {
                            out.append('§').append('x');
                            for (char digit : rgb.toCharArray()) out.append('§').append(digit);
                            j += 7;
                            continue;
                        }
                    }
                    if (j + 1 < line.length()) {
                        char code = Character.toLowerCase(line.charAt(j + 1));
                        if ((colors && "0123456789abcdef".indexOf(code) >= 0)
                                || (format && "klmnor".indexOf(code) >= 0)) {
                            out.append('§').append(code);
                            j++;
                            continue;
                        }
                    }
                }
                out.append(line.charAt(j));
            }
            e.line(i, LegacyComponentSerializer.legacySection().deserialize(out.toString()));
        }
    }
}
