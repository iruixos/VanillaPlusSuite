package com.iruixos.vanillaplussuite;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/** Visual AFK status only; never modifies player invulnerability or game mechanics. */
final class AfkDetection implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, Component> originalTabNames = new HashMap<>();
    private final Map<UUID, String> ownedTeams = new HashMap<>();
    private final Set<UUID> afk = new HashSet<>();
    // Manual /afk history is independent from natural inactivity detection.
    private final Map<UUID, ManualCooldown> manualCooldowns = new HashMap<>();

    private static final class ManualCooldown {
        int uses;
        long lastUseMillis;
        long blockedUntilMillis;
    }

    record ManualAfkResult(boolean accepted, boolean nowAfk, long remainingSeconds) {}

    private BukkitTask checkTask;

    AfkDetection(JavaPlugin plugin) { this.plugin = plugin; }

    void restart() {
        stop();
        if (!plugin.getConfig().getBoolean("afk-detection.enabled", true)) return;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) lastActivity.put(player.getUniqueId(), now);
        long interval = Math.max(1, plugin.getConfig().getLong("afk-detection.check-interval-seconds", 5)) * 20L;
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkPlayers, interval, interval);
    }

    void stop() {
        if (checkTask != null) { checkTask.cancel(); checkTask = null; }
        for (UUID uuid : new HashSet<>(afk)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) clearAfk(player, false);
        }
        for (String name : new HashSet<>(ownedTeams.values())) {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(name);
            if (team != null) team.unregister();
        }
        ownedTeams.clear(); originalTabNames.clear(); afk.clear(); lastActivity.clear();
    }

    private void checkPlayers() {
        long now = System.currentTimeMillis();
        long timeout = Math.max(1, plugin.getConfig().getLong("afk-detection.timeout-seconds", 300)) * 1000L;
        // Avoid retaining offline players' expired cooldown history indefinitely.
        manualCooldowns.entrySet().removeIf(entry ->
                now >= entry.getValue().blockedUntilMillis
                && now - entry.getValue().lastUseMillis >= resetAfterMillis());
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            lastActivity.putIfAbsent(uuid, now);
            if (!afk.contains(uuid) && now - lastActivity.get(uuid) >= timeout) setAfk(player);
        }
    }

    /** Only successful /afk invocations advance the escalating cooldown. */
    ManualAfkResult toggleManualAfk(Player player) {
        if (checkTask == null) return new ManualAfkResult(false, false, 0);
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        ManualCooldown state = manualCooldowns.computeIfAbsent(uuid, ignored -> new ManualCooldown());

        // Never reset or shorten a cooldown that is already active.
        if (now < state.blockedUntilMillis) {
            long seconds = (state.blockedUntilMillis - now + 999L) / 1000L;
            return new ManualAfkResult(false, afk.contains(uuid), seconds);
        }

        // The escalation resets after the configured idle window since the last
        // successful manual invocation. Natural AFK changes don't affect this.
        if (state.uses > 0 && now - state.lastUseMillis >= resetAfterMillis()) {
            state.uses = 0;
        }
        state.uses++;
        state.lastUseMillis = now;
        long cooldownSeconds = configuredCooldownSeconds(state.uses);
        state.blockedUntilMillis = now + cooldownSeconds * 1000L;

        if (afk.contains(uuid)) {
            clearAfk(player, true);
            lastActivity.put(uuid, now);
        } else {
            lastActivity.putIfAbsent(uuid, now);
            setAfk(player);
        }
        return new ManualAfkResult(true, afk.contains(uuid), 0);
    }

    private long resetAfterMillis() {
        return Math.max(1L, plugin.getConfig().getLong(
                "afk-detection.manual-cooldown.reset-after-seconds", 300L)) * 1000L;
    }

    private long configuredCooldownSeconds(int useCount) {
        List<Integer> steps = plugin.getConfig().getIntegerList("afk-detection.manual-cooldown.steps-seconds");
        if (steps.isEmpty()) steps = List.of(0, 15, 30, 60, 900);
        // Repeated use past the last configured step keeps the final cooldown.
        return Math.max(0L, steps.get(Math.min(useCount, steps.size()) - 1));
    }

    boolean isRunning() { return checkTask != null; }

    private boolean enabled(String setting) {
        return checkTask != null && plugin.getConfig().getBoolean("afk-detection.activity." + setting, true);
    }

    private void activity(Player player) {
        if (checkTask == null) return;
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (afk.contains(player.getUniqueId())) clearAfk(player, true);
    }

    private boolean prefix() {
        return plugin.getConfig().getString("afk-detection.display.position", "suffix").equalsIgnoreCase("prefix");
    }

    private NamedTextColor color(String key, NamedTextColor fallback) {
        String configured = plugin.getConfig().getString("afk-detection.display." + key, "");
        NamedTextColor result = NamedTextColor.NAMES.value(configured.toLowerCase(Locale.ROOT));
        return result == null ? fallback : result;
    }

    private ChatColor legacyColor(String key, ChatColor fallback) {
        try { return ChatColor.valueOf(plugin.getConfig().getString("afk-detection.display." + key, "").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return fallback; }
    }

    private void setAfk(Player player) {
        UUID uuid = player.getUniqueId();
        if (!afk.add(uuid)) return;
        String marker = plugin.getConfig().getString("afk-detection.display.marker", "[AFK]");
        if (plugin.getConfig().getBoolean("afk-detection.display.tab-list", true)) {
            originalTabNames.put(uuid, player.playerListName());
            Component name = Component.text(player.getName(), color("name-color", NamedTextColor.DARK_GRAY));
            Component label = Component.text(marker, color("marker-color", NamedTextColor.YELLOW));
            player.playerListName(prefix() ? label.append(Component.space()).append(name) : name.append(Component.space()).append(label));
        }
        if (plugin.getConfig().getBoolean("afk-detection.display.overhead-nametag", true)) {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            // Never hijack another plugin's existing team.
            if (board.getEntryTeam(player.getName()) == null) {
                String teamName = "vpsafk" + uuid.toString().replace("-", "").substring(0, 10);
                Team team = board.getTeam(teamName);
                if (team == null) team = board.registerNewTeam(teamName);
                team.setColor(legacyColor("name-color", ChatColor.DARK_GRAY));
                String label = legacyColor("marker-color", ChatColor.YELLOW) + marker;
                if (prefix()) team.setPrefix(label + " " + legacyColor("name-color", ChatColor.DARK_GRAY));
                else team.setSuffix(" " + label);
                team.addEntry(player.getName());
                ownedTeams.put(uuid, teamName);
            }
        }
        announce(player, "broadcast-on-afk", "afk", "&7[Vanilla+] &e{player} &7is now AFK.");
    }

    /** Announcements occur only for actual returns, never during cleanup or disconnect. */
    private void clearAfk(Player player, boolean announceReturn) {
        UUID uuid = player.getUniqueId();
        if (!afk.remove(uuid)) return;
        if (originalTabNames.containsKey(uuid)) player.playerListName(originalTabNames.remove(uuid));
        String teamName = ownedTeams.remove(uuid);
        if (teamName != null) {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
            if (team != null) team.unregister();
        }
        if (announceReturn) {
            announce(player, "broadcast-on-return", "return", "&7[Vanilla+] &e{player} &7is no longer AFK.");
            if (plugin.getConfig().getBoolean("afk-detection.messages.notify-on-return", true)) {
                String format = plugin.getConfig().getString("afk-detection.messages.private-return", "&7[Vanilla+] &eYour AFK status has been cleared.");
                player.sendMessage(formatMessage(format, player));
            }
        }
    }

    private String formatMessage(String format, Player player) {
        return ChatColor.translateAlternateColorCodes('&', format.replace("{player}", player.getName()));
    }

    private void announce(Player player, String toggle, String path, String fallback) {
        if (!plugin.getConfig().getBoolean("afk-detection.messages." + toggle, true)) return;
        String format = plugin.getConfig().getString("afk-detection.messages." + path, fallback);
        Bukkit.broadcastMessage(formatMessage(format, player));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer(); clearAfk(player, false); lastActivity.remove(player.getUniqueId());
    }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (!enabled("movement") && !enabled("look-around")) return;
        Location from = event.getFrom(), to = event.getTo();
        if (to == null) return;
        boolean look = from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();
        double threshold = Math.max(0, plugin.getConfig().getDouble("afk-detection.movement-threshold", 0.05));
        // A position change alone is not evidence of player activity: mobs,
        // water, pistons and knockback can move an idle player. Require an
        // actual movement input reported by the client before counting it.
        Input input = event.getPlayer().getCurrentInput();
        boolean intentionalMovement = input.isForward() || input.isBackward()
                || input.isLeft() || input.isRight() || input.isJump();
        boolean moved = from.getWorld().equals(to.getWorld())
                && from.distanceSquared(to) >= threshold * threshold;
        if ((look && enabled("look-around"))
                || (moved && intentionalMovement && enabled("movement"))) activity(event.getPlayer());
    }
    // The input event also handles the start of movement and jumping when
    // the player's location has not yet visibly changed (e.g. against a wall).
    @EventHandler public void onInput(PlayerInputEvent event) {
        if (!enabled("movement")) return;
        Input input = event.getInput();
        if (input.isForward() || input.isBackward() || input.isLeft()
                || input.isRight() || input.isJump()) activity(event.getPlayer());
    }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if (enabled("interactions")) activity(event.getPlayer()); }
    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent event) { if (enabled("interactions")) activity(event.getPlayer()); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { if (enabled("inventory") && event.getWhoClicked() instanceof Player player) activity(player); }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) { if (enabled("inventory") && event.getWhoClicked() instanceof Player player) activity(player); }
    @EventHandler public void onInventoryOpen(InventoryOpenEvent event) { if (enabled("inventory") && event.getPlayer() instanceof Player player) activity(player); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled("commands")) return;
        // A manual AFK toggle must not clear its own AFK status before execution.
        String command = event.getMessage().trim().split("\\s+", 2)[0];
        if (command.equalsIgnoreCase("/afk") || command.equalsIgnoreCase("/vanillaplussuite:afk")) return;
        activity(event.getPlayer());
    }
    @EventHandler public void onChat(AsyncChatEvent event) {
        if (!enabled("chat")) return;
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) activity(player);
        });
    }
}
