![VanillaPlusSuite Banner](assets/header.png)

# VanillaPlusSuite

**Small quality-of-life improvements. Familiar vanilla survival.**

VanillaPlusSuite is a modular quality-of-life plugin for **Paper Minecraft servers**, developed by **iruixos**. It adds practical conveniences and optional safeguards while keeping survival gameplay recognizable: no free items, teleport-to-grave commands, automatic repairs, or artificially generated honey.

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
| **Sign Editing Utilities** | Adds permission-controlled sign colours, RGB hex colours, and styling commands, with ownership and waxed-sign safeguards. |
| **Custom Death Messages** | Replaces vanilla death announcements with configurable, randomized, sometimes context-aware messages. Coordinates remain private. |
| **Bee Colony Cooperation** *(experimental)* | Helps nectar-carrying bees find other hives when their home hive is honey-full, and adds occupancy inspection, activity particles, improved hive selection, relocation assistance, and rain shelter behaviour. |
| **AFK Detection** | Automatically marks inactive players AFK or lets them toggle it using `/afk`; optional Tab-list/overhead nametags and public announcements. No gameplay immunity. |
| **Armored Elytras** | Combine an Elytra and chestplate, keep their armor bonuses, then separate them; supports dropped-item or workstation GUI interactions. |

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

Players automatically enter AFK status after a configurable inactivity period (five minutes by default). `/afk` immediately toggles the status without waiting. Movement, looking around, chatting, commands other than `/afk`, interactions, and inventory activity can clear it. While AFK, the player's name is dark grey and `[AFK]` is yellow in the Tab list and, when supported by the viewer's scoreboard, the overhead nametag. The marker can appear as a prefix or suffix.

Public messages announce when someone goes AFK and returns; a separate private return confirmation is optional. All message strings and announcement toggles are configurable. AFK status **does not** grant damage immunity, prevent mob attacks, alter hunger, or otherwise change survival gameplay. The overhead display uses scoreboard teams and does not overwrite existing teams.

### Armored Elytras

Combine an Elytra with a chestplate on an **anvil**, either by dropping both items on top of it or by placing them into the **anvil GUI** (either slot order). The resulting Armored Elytra is wearable and retains the chestplate's armor properties. Separate it by dropping it onto a **grindstone** or using the **grindstone GUI**; both underlying items are returned. The GUI separation bypasses ordinary disenchanting, so the original enchantments are retained.

The plugin stores the original item data to support reconstruction. A Netherite chestplate can be salvaged if the dropped Armored Elytra is destroyed by fire or lava when the salvage option is enabled. When separating in the GUI, the chestplate goes into the player's inventory, or is dropped nearby if there is no room. The Elytra's durability is carried through separation; other changes made to a combined item may not be reflected in the stored originals.

These mechanics are a **clean-room implementation** and do not incorporate PaperTweaks source code. No special XP cost is charged for the custom anvil GUI combination.

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
| `/afk` | Toggle your AFK status immediately. | `vanillaplussuite.afk` |
| `/sign color red [1-4]` | Colour all lines or the specified line on a targeted sign. | `vanillaplussuite.sign.color` |
| `/sign color #FF69B4 [1-4]` | Apply RGB text colour. | `vanillaplussuite.sign.hex` |
| `/sign bold [1-4]` | Toggle bold formatting. | `vanillaplussuite.sign.command`, `vanillaplussuite.sign.format` |
| `/sign italic [1-4]` | Toggle italic formatting. | `vanillaplussuite.sign.command`, `vanillaplussuite.sign.format` |
| `/sign reset [1-4]` | Reset sign line-level styling. | `vanillaplussuite.sign.command` |

Sign commands target the **front** of the sign you are looking at (default maximum distance: 6 blocks). Omit the optional line number to affect all four lines. Waxed signs cannot be changed. Normal players may only style recorded signs that they own; legacy/untracked signs require the administrative bypass permission. Sign formatting is not a replacement for a land-claim plugin.

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

The complete bundled configuration (including the death-message library) is in [`src/main/resources/config.yml`](src/main/resources/config.yml). Most modules have an `enabled` toggle. The examples below reproduce settings from the **customized configuration currently used on your server**, which can differ from fresh-install defaults.

### AFK detection

```yaml
afk-detection:
  enabled: true
  timeout-seconds: 300
  check-interval-seconds: 5
  movement-threshold: 0.05
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

## Startup console banner

The compact ASCII-style banner appears whenever the plugin enables. The plugin version and Minecraft version are detected automatically, and the border adjusts to the text length. **There is no configuration toggle.**

## Design and compatibility notes

- Intended for near-vanilla **Hard Survival**: AFK status is cosmetic/social, and armor benefits from Armored Elytras are the intended equipment exception.
- Other plugins managing scoreboards, custom inventories, item metadata, signs, or bee AI may cause conflicts. Test changes on a staging server.
- Experimental bee behaviour has not been fully validated over long periods.
- Using workstations for Armored Elytras supports normal clicks and shift-clicks; test unusual third-party inventory interactions before production use.

## Project information

- **Plugin:** VanillaPlusSuite
- **Author:** iruixos
- **Platform:** Paper
- **Package:** `com.iruixos.vanillaplussuite`
- **Notes:** This project contains AI-generated code, but was tested by a human in a real PaperMC server :)

## License

VanillaPlusSuite is licensed under the MIT License. See [LICENSE](LICENSE) for details.
