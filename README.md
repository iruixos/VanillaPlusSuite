![VanillaPlusSuite Banner](assets/header.png)

# VanillaPlusSuite

**Small quality-of-life improvements. Familiar vanilla survival.**

VanillaPlusSuite is a modular quality-of-life plugin for **Paper Minecraft servers**, developed by **iruixos**. It adds practical conveniences and optional safeguards while keeping survival gameplay recognizable: no free items, teleport-to-grave commands, automatic repairs, or artificially generated honey. The current documentation covers **v1.4.6**.

## Features

| Module | What it does |
| --- | --- |
| **Anvil Preservation** | Warns when a heavily damaged anvil is near an anvil interaction. Does **not** prevent breakage or change vanilla anvil durability. Detection is approximate. |
| **Harvest Indicators** | Shows subtle particles near mature wheat, carrots, potatoes, beetroot, and nether wart. Does not harvest crops. |
| **Pet Protection** | Prevents direct player melee attacks against tamed animals, configurable for owned pets and other players' pets. Does not prevent indirect or environmental damage. |
| **Item Frame Lock** | Requires sneaking to rotate occupied item frames; optionally requires sneaking to break them. |
| **Iron Door & Trapdoor Interaction** | Lets players right-click iron doors and optionally iron trapdoors, with sound effects, redstone safeguards, and optional timed auto-close. |
| **Grave Coordinates** | Privately sends the player's death world and XYZ coordinates after respawn. Does not teleport or recover items. |
| **Tool Durability Warning** | Warns when tool durability drops below a configurable percentage. Does not repair items. |
| **Sign Editing Utilities** | Adds permission-controlled sign colours, RGB hex colours, glow controls, and styling commands, with ownership, waxed-sign, and WayBridge safeguards. |
| **Custom Death Messages** | Replaces vanilla death announcements with configurable, randomized, sometimes context-aware messages. Coordinates remain private. |
| **Bee Colony Cooperation** *(experimental)* | Helps nectar-carrying bees find other hives when their home hive is honey-full, and adds occupancy inspection, activity particles, improved hive selection, relocation assistance, and rain shelter behaviour. |
| **AFK Detection** | Automatically marks inactive players AFK or lets them toggle it using `/afk`, with configurable escalating manual-command cooldowns, Tab-list/overhead nametags, and announcements. No gameplay immunity. |
| **Armored Elytras** | Combine an Elytra and chestplate, keep their armor bonuses, then separate them; supports dropped-item or workstation GUI interactions. |
| **Persistent Passive Animal Drops** | Extend the ground lifetime of naturally produced eggs, armadillo scutes, tamed-cat morning gifts, and panda slimeballs, with an optional indefinite setting. |
| **Unusual Block Breakage** | Recover budding amethyst in Survival using a Silk Touch pickaxe. |
| **Mob Silencing** | Silence or unsilence mobs using specially named Name Tags, with a floating `[SILENCED]` indicator that preserves their real nametags. |
| **Stackable Consumables** | Stack mushroom/rabbit stew, beetroot soup, suspicious stew, water buckets, and lava buckets; defaults to 16 per stack. |

### Sign editing and WayBridge compatibility

Use `/sign glow [colour]` to enable glowing sign text and `/sign unglow` to disable it (see `/sign help` for the installed command syntax). Named dye colours such as `light_purple` and hex colours such as `#FFAAAA` are supported. Glow changes preserve existing sign text styling; vanilla sign glow applies to an entire sign face and does not accept a line number. Hex glow colours are approximated to a vanilla dye colour. Sign ownership and wax protections remain in force.

When **WayBridge** is installed, registered warp signs are protected against `/sign` editing, including administrative edits. VanillaPlusSuite uses WayBridge's registration API when available and fails closed if it cannot verify registration; this does not change ordinary sign styling.

### Iron doors and trapdoors

- **Players only:** right-click with the main hand to open or close an iron door or iron trapdoor; mobs do not gain manual access.
- **Iron doors:** both halves update together.
- **Iron trapdoors:** controlled by `iron-door-interaction.include-trapdoors`.
- **Redstone:** powered blocks are not manually overridden when `respect-redstone` is enabled; vanilla redstone still works.
- **Auto-close:** optionally closes a manually opened door/trapdoor after a configured delay. Manually closing it cancels its pending timer. A powered block is not forced closed by the timer.

### Bee Colony Cooperation — experimental

When a bee carries nectar and its **home hive is already at maximum honey level**, the plugin can temporarily assign it to an eligible nearby hive or nest. It attempts to restore the original home after the visit. This uses vanilla bee pollen-delivery mechanics: **the plugin does not directly increase honey levels or create bees**.

Other optional behaviours:

- **Hive occupancy inspection:** sneak-right-click a beehive or bee nest with an empty main hand to see occupancy and honey level.
- **Hive activity particles:** nearby players may see subtle particles when a monitored hive's honey level increases.
- **Improved hive selection:** bees without a valid home can seek available nearby hives.
- **Relocation assistance:** helps displaced/homeless bees find another hive.
- **Rain shelter:** allows homeless bees to seek shelter during rain; bees with homes keep their vanilla weather behaviour.

**Limitations:** temporary hive assignment and return-home tracking are best-effort because bees are not present as world entities while inside hives. Only loaded chunks are considered. Other plugins that change bee AI may interfere. Test this module before enabling it on a production server.

### AFK detection

Players automatically enter AFK status after a configurable inactivity period (five minutes by default). `/afk` manually toggles the status, subject to a configurable escalating cooldown intended to discourage chat announcement spam. Actual player movement input, looking around, chatting, commands other than `/afk`, interactions, and inventory activity can clear it. Passive displacement from mob collisions, flowing water, or knockback alone does not count as player activity. While AFK, the player's name is dark grey and `[AFK]` is yellow in the Tab list and, when supported by the viewer's scoreboard, the overhead nametag. The marker can appear as a prefix or suffix.

Public messages announce when someone goes AFK and returns; a separate private return confirmation is optional. Successful manual `/afk` uses increase the cooldown, while blocked attempts neither change AFK status nor broadcast a message. Natural AFK detection and activity-based clearing never use or advance this cooldown. All message strings and announcement toggles are configurable. AFK status **does not** grant damage immunity, prevent mob attacks, alter hunger, or otherwise change survival gameplay. The overhead display uses scoreboard teams and does not overwrite existing teams.

### Armored Elytras

Combine an Elytra with a chestplate on an **anvil**, either by dropping both items on top of it or by placing them into the **anvil GUI** (either slot order). The resulting Armored Elytra is wearable and retains the chestplate's armor properties. Separate it by dropping it onto a **grindstone** or using the **grindstone GUI**; both underlying items are returned. The GUI separation bypasses ordinary disenchanting, so the original enchantments are retained.

The plugin stores the original item data to support reconstruction. A Netherite chestplate can be salvaged if the dropped Armored Elytra is destroyed by fire or lava when the salvage option is enabled. When separating in the GUI, the chestplate goes into the player's inventory, or is dropped nearby if there is no room. The Elytra's durability is carried through separation; other changes made to a combined item may not be reflected in the stored originals.

These mechanics are a **clean-room implementation** and do not incorporate PaperTweaks source code. No special XP cost is charged for the custom anvil GUI combination.

### Persistent Passive Animal Drops

This module extends the time that certain **naturally produced items** stay on the ground: chicken eggs (including blue and brown eggs), armadillo scutes, morning gifts from tamed cats, and slimeballs from pandas. The bundled lifetime is **1,800 seconds (30 minutes)**. An optional indefinite mode overrides that timer, but is disabled by default and logs a server warning because accumulated item entities can affect performance.

Only qualifying item spawns are tracked. Detection uses the presence of an eligible nearby animal rather than a guaranteed producer identity, so an unrelated item that spawns very close to an animal may occasionally match. The plugin stores expiry metadata on tracked items; it does not make them invulnerable to pickup or destruction. Expiry is based on elapsed real-world time, including time when their chunks are unloaded.

### Unusual Block Breakage

In **Survival mode**, a player can harvest and keep **budding amethyst** using a pickaxe enchanted with **Silk Touch**. Without Silk Touch, budding amethyst retains normal break/drop behaviour. The plugin observes the completed, uncancelled break; protection plugins can still prevent collection. Other unusual blocks are not supported by this module yet.

### Mob Silencing

Use a Name Tag renamed to any of the following **case-insensitive** values on a mob. The mob's custom name is preserved, including when an ordinary Name Tag is used to rename it later.

| Silence | Unsilence |
| --- | --- |
| `silence` | `unsilence` |
| `silence me` | `unsilence me` |
| `silence-me` | `unsilence-me` |
| `silence_me` | `unsilence_me` |
| `silenceme` | `unsilenceme` |

A successful silence or unsilence operation consumes one triggering Name Tag in Survival by default, but not in Creative. No extra Name Tag is consumed if the operation does not change the mob's state. Silencing persists between restarts; unsilencing removes the stored silence state and restores the mob's normal sounds. Optional private feedback confirms successful changes.

A configurable floating `[SILENCED]` text indicator follows silenced mobs with **independent per-tick tracking**, without becoming a passenger or replacing the mob's actual nametag. It can appear on unnamed mobs and is maintained near players within the configured visibility range. It does not change mob movement, damage, or normal rider/passenger behaviour.

### Stackable Consumables

The following items can stack to **16 by default**, with separate configurable maximums for stews and filled buckets:

| Item | Default maximum | Container returned after use |
| --- | ---: | --- |
| Mushroom Stew | 16 | Bowl |
| Rabbit Stew | 16 | Bowl |
| Beetroot Soup | 16 | Bowl |
| Suspicious Stew | 16 | Bowl |
| Water Bucket | 16 | Empty Bucket |
| Lava Bucket | 16 | Empty Bucket |

Each item type can be enabled or disabled separately. Suspicious Stew with different effects is kept distinct by item metadata. The module uses Paper's item-metadata stack limit and Minecraft's remainder handling: the intended result is one empty bowl per consumed stew and one empty iron bucket per placed liquid source, with containers dropped when inventory space is unavailable. Test these interactions on your Paper build before production use, especially full inventories, stacked bucket placement, hoppers, and lava buckets as furnace fuel. Existing items may need to be handled or refreshed by the plugin before the new limits appear.

## Requirements

- **Server:** Paper 26.3 (source targets Paper API `26.3.build.244-stable`).
- **Java:** 25 (the Maven project compiles with `--release 25`).
- **Build from source:** Maven, run `mvn clean package`.
- **Runtime dependencies:** None declared; LuckPerms is optional for permissions management.

Folia compatibility is **not** claimed.

## Installation and updating

1. Download a compatible release JAR, or build from source with `mvn clean package`.
2. Place the JAR in `plugins/` on your Paper server.
3. Restart to generate `plugins/VanillaPlusSuite/config.yml`.
4. Configure modules to your liking. `/vps reload` reloads configuration; restart after a JAR upgrade.

**Upgrades:** an existing configuration is not automatically overwritten. Compare your current `config.yml` with the newly bundled [`src/main/resources/config.yml`](src/main/resources/config.yml) and manually merge new options. Back up your config first.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/vps reload` | Reload the configuration and relevant tasks. | `vanillaplussuite.admin` |
| `/afk` | Manually toggle AFK status (subject to a configurable cooldown). | `vanillaplussuite.afk` |
| `/sign color red [1-4]` | Colour all lines or the specified line on a targeted sign. | `vanillaplussuite.sign.color` |
| `/sign color #FF69B4 [1-4]` | Apply RGB text colour. | `vanillaplussuite.sign.hex` |
| `/sign bold [1-4]` | Toggle bold formatting. | `vanillaplussuite.sign.command`, `vanillaplussuite.sign.format` |
| `/sign italic [1-4]` | Toggle italic formatting. | `vanillaplussuite.sign.command`, `vanillaplussuite.sign.format` |
| `/sign reset [1-4]` | Reset sign line-level styling. | `vanillaplussuite.sign.command` |
| `/sign glow [colour]` | Enable glowing text; optionally select a named dye/hex colour. | `vanillaplussuite.sign.command` and applicable sign styling permission |
| `/sign unglow` | Remove glowing text from the targeted sign. | `vanillaplussuite.sign.command` |

Sign commands target the **front** of the sign you are looking at (default maximum distance: 6 blocks). Omit the optional line number to affect all four lines. Waxed signs and registered WayBridge signs cannot be changed. Normal players may only style recorded signs that they own; legacy/untracked signs require the administrative bypass permission. Sign formatting is not a replacement for a land-claim plugin.

### Sign formatting syntax

When the corresponding settings and permissions allow it, the sign editor accepts standard colour codes (`&aWelcome`), formatting (`&lImportant`), and RGB colours (`&#FF69B4Pink`). This module does not increase the vanilla sign character limit.

## Permissions

| Node | Purpose | Default |
| --- | --- | --- |
| `vanillaplussuite.admin` | `/vps reload` | OP |
| `vanillaplussuite.afk` | `/afk` | Everyone |
| `vanillaplussuite.sign.color` | Named colours / legacy colour codes | Everyone |
| `vanillaplussuite.sign.hex` | RGB sign colours | Everyone |
| `vanillaplussuite.sign.format` | Text formatting | OP |
| `vanillaplussuite.sign.command` | Sign styling commands | Everyone |
| `vanillaplussuite.sign.admin` | Bypass sign ownership check | OP |

Permissions can be managed with LuckPerms. They do not override a disabled module.

## Configuration

The complete bundled configuration (including the death-message library) is in [`src/main/resources/config.yml`](src/main/resources/config.yml). Most modules have an `enabled` toggle. The examples below reflect the **bundled configuration**, including your selected defaults. Existing installations retain their own config until it is updated manually.

### Sign glow controls

```yaml
sign-editing:
  enabled: true
  commands:
    enabled: true
    max-distance: 6
  allow-color-codes: true
  allow-format-codes: true
  allow-hex-colors: true
```

Use `/sign glow` or `/sign unglow` to change the glow state of an eligible sign. Sign colouring and glowing also respect WayBridge protection when that plugin is installed.

### AFK detection

```yaml
afk-detection:
  enabled: true
  timeout-seconds: 300
  check-interval-seconds: 5
  movement-threshold: 0.05

  # Manual /afk command only; automatic AFK detection is unaffected.
  manual-cooldown:
    # Cooldown after successful uses: 1st, 2nd, 3rd, 4th, 5th+.
    steps-seconds: [0, 15, 30, 60, 900]
    # Reset escalation after 5 minutes since last successful /afk.
    # Does not shorten an already active cooldown.
    reset-after-seconds: 300

  activity:
    movement: true
    look-around: true
    chat: true
    commands: true
    interactions: true
    inventory: true
  display:
    marker: '[AFK]'
    position: suffix
    name-color: dark_gray
    marker-color: yellow
    tab-list: true
    overhead-nametag: true
  messages:
    broadcast-on-afk: true
    broadcast-on-return: true
    notify-on-return: false
    afk: '&7[Vanilla+] &e{player} &7is now AFK.'
    return: '&7[Vanilla+] &e{player} &7is no longer AFK.'
    private-return: '&7[Vanilla+] &eYour AFK status has been cleared.'
```

`manual-cooldown.steps-seconds` sets the cooldown **after each successful `/afk` use**: the first use has no delay, the second applies 15 seconds, the third 30 seconds, the fourth 60 seconds, and the fifth and later uses 900 seconds (15 minutes). You can change the number of steps and their durations; once the last step is reached, its delay continues to apply. Blocked attempts show the remaining time privately and do not advance the escalation. `reset-after-seconds` resets the escalation count after five minutes without a successful manual `/afk`, but never cancels a cooldown that is still running. Automatic AFK detection and activity-based status clearing are unaffected. The manual cooldown history is kept in memory, not persisted across server restarts.

`position` supports `suffix` (`Steve [AFK]`) or `prefix` (`[AFK] Steve`). The example above uses a dark grey name and yellow marker. `notify-on-return: false` disables the *private* confirmation, while the two public messages remain enabled.

### Armored Elytras

```yaml
armored-elytras:
  enabled: true
  anvil-combining: true
  grindstone-separation: true
  anvil-gui-combining: true
  grindstone-gui-separation: true
  netherite-fire-salvage: true
```

The `anvil-combining` and `grindstone-separation` switches control **dropping items on blocks**; the `*-gui-*` switches independently control inventory GUI interactions.

### Iron doors and trapdoors

```yaml
iron-door-interaction:
  enabled: true
  include-trapdoors: true
  play-sound: true
  respect-redstone: true
  auto-close:
    enabled: true
    delay-seconds: 2
```

In this example, iron doors and trapdoors automatically close **two seconds** after a player opens them. Both door halves stay synchronized, and powered blocks are not forcibly overridden when redstone protection is enabled. Mobs cannot manually right-click them via this module.

### Bee Colony Cooperation (experimental)

```yaml
bee-colony-cooperation:
  enabled: true
  share-honey: true
  search-radius: 12
  scan-interval-ticks: 100
  search-cooldown-seconds: 30
  empty-hives-only: true
  include-bee-nests: true
  include-beehives: true
  ignore-full-hives: true
  occupancy-inspection: true
  activity-particles: true
  activity-radius: 8
  improved-hive-selection: true
  relocation-assistance: true
  rain-shelter: true
```

Sharing uses vanilla pollen delivery rather than artificially generating honey; temporary home-hive redirection and return tracking are best-effort. Only loaded chunks are considered. Test with real bees before relying on it in production.

### Persistent passive animal drops

```yaml
persistent-passive-drops:
  enabled: true
  chicken-eggs: true
  armadillo-scutes: true
  cat-morning-gifts: true
  panda-slimeballs: true
  animal-detection-radius: 1.75
  lifetime-seconds: 1800
  # WARNING: Unlimited item lifetimes may cause server lag.
  # Overrides lifetime-seconds; disabled by default.
  indefinite: false
```

The animal-detection radius helps identify natural item spawns; it is not a guarantee of exact producer attribution. A finite lifetime is recommended on busy survival servers.

### Unusual block breakage

```yaml
unusual-block-breakage:
  enabled: true
  budding-amethyst: true
```

Only Silk Touch **pickaxes** in Survival can retrieve budding amethyst with this option enabled.

### Other module settings

```yaml
anvil-preservation:
  enabled: true
  warn-on-use: true
  cooldown-seconds: 30
harvest-indicators:
  enabled: true
  interval-ticks: 60
  radius: 8
  max-blocks-per-player: 300
  particle-chance: 0.1
pet-protection:
  enabled: true
  protect-own: true
  protect-others: false
  notify-attacker: true
item-frame-lock:
  enabled: true
  require-sneak-to-rotate: true
  require-sneak-to-remove: true
grave-coordinates:
  enabled: true
  send-on-respawn: true
  send-on-death: false
tool-durability-warning:
  enabled: true
  threshold-percent: 10
  cooldown-seconds: 45
sign-editing:
  enabled: true
  commands:
    enabled: true
    max-distance: 6
  allow-color-codes: true
  allow-format-codes: true
  allow-hex-colors: true
```

Death coordinates are sent privately upon respawn and are held in memory only. The `grave-coordinates.send-on-death` key remains in the config but is not currently used to send a second message on death. Anvil preservation only *warns*; it does not prevent breaking. Tool durability warnings do not repair items.

### Mob silencing

```yaml
mob-silencing:
  enabled: true
  trigger-names:
    - "silence"
    - "silence me"
    - "silence-me"
    - "silence_me"
    - "silenceme"
  unsilence-names:
    - "unsilence"
    - "unsilence me"
    - "unsilence-me"
    - "unsilence_me"
    - "unsilenceme"
  indicator:
    enabled: true
    text: "[SILENCED]"
    color: gray
    visible-range: 16
    show-on-unnamed-mobs: true
  consume-name-tag: true
  notify-player: true
```

### Stackable consumables

```yaml
stackable-consumables:
  enabled: true
  # Separate size limits, valid range: 1-99.
  stew-stack-size: 16
  bucket-stack-size: 16
  mushroom-stew: true
  rabbit-stew: true
  beetroot-soup: true
  suspicious-stew: true
  water-buckets: true
  lava-buckets: true
```

Because existing server configurations are **not automatically overwritten**, merge these new sections into the live `plugins/VanillaPlusSuite/config.yml` when upgrading. The stack size override is an item metadata feature, and its interactions with other inventory plugins should be tested.

### Custom death messages

```yaml
death-messages:
  enabled: true
  replace-vanilla: true
  environment:
    enabled: true
    chance: 0.30
  messages:
    fall:
      - "{player} attempted to negotiate with gravity. Gravity declined."
    lava:
      - "{player} discovered that lava is not, in fact, a hot tub."
    pvp:
      - "{killer} sent {player} back to the respawn screen."
    generic:
      - "{player} discovered that Hard difficulty is aptly named."
```

This is a **shortened example**, not the full configurable message library. See `config.yml` for every death category. The `{player}` placeholder identifies the deceased; `{killer}` is intended for PvP messages. Grave coordinates are private and separate from public death announcements.

## Design and compatibility notes

- Intended for near-vanilla **Hard Survival**: AFK status is cosmetic/social, while Armored Elytras and Silk Touch budding-amethyst harvesting are explicit gameplay exceptions.
- Other plugins managing scoreboards, custom inventories, item metadata, signs, display entities, or bee AI may cause conflicts. Test changes on a staging server.
- The Mob Silencing indicator uses independent text displays and updates their positions each tick near players; monitor performance with unusually large numbers of silenced mobs.
- Stacked soups and filled buckets require item-use testing, including container remainders and automation, before production rollout.
- Experimental bee behaviour has not been fully validated over long periods.
- Passive item lifetimes may increase entity counts; indefinite preservation should be used cautiously. Passive drop attribution is proximity-based.
- Using workstations for Armored Elytras supports normal clicks and shift-clicks; test unusual third-party inventory interactions before production use.

## Project information

- **Plugin:** VanillaPlusSuite
- **Author:** iruixos
- **Platform:** Paper
- **Package:** `com.iruixos.vanillaplussuite`
- **Notes:** This project contains AI-generated code, but was tested by a human in a real PaperMC server :)

## License

VanillaPlusSuite is licensed under the MIT License. See [LICENSE](LICENSE) for details.
