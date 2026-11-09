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

## Requirements

- **Server:** Paper 26.3 (the current source targets Paper API `26.3.build.244-stable`).
- **Java:** 25 or newer compatible runtime; source is compiled with `--release 25`.
- **Build:** Maven, if building from source.

No additional runtime plugin dependencies are declared. Folia compatibility is **not** claimed.

## Installation

1. Build the project with `mvn clean package`, or use a compatible release JAR if one is provided.
2. Copy the built JAR from `target/` into your Paper server's `plugins/` directory.
3. Restart the server to generate `plugins/VanillaPlusSuite/config.yml`.
4. Adjust the settings to your preference and use `/vps reload` where appropriate. A restart is safest when upgrading the JAR.

**Updating:** existing `config.yml` files are not automatically replaced. Compare them with the latest bundled `src/main/resources/config.yml` and manually merge newly added settings. Back up your configuration first.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/vps reload` | Reloads configuration and restarts relevant scheduled tasks. | `vanillaplussuite.admin` |
| `/sign color red` | Apply a named colour to all four lines on the sign you are looking at. | `vanillaplussuite.sign.color` |
| `/sign color #FF69B4 2` | Apply an RGB colour to line 2 only. | `vanillaplussuite.sign.hex` |
| `/sign bold [1-4]` | Toggle bold on all lines or one specified line. | `vanillaplussuite.sign.command` + `.sign.format` |
| `/sign italic [1-4]` | Toggle italic on all lines or one specified line. | `vanillaplussuite.sign.command` + `.sign.format` |
| `/sign reset [1-4]` | Reset line-level colour and styling on all lines or one line. | `vanillaplussuite.sign.command` |

Sign commands act on the **front** of a targeted sign within the configured maximum distance (6 blocks by default). Omit the line number to affect all four lines. Standard colours, RGB colours, and formatting each have their own config switch. The `/sign` command family also requires sign editing and sign commands to be enabled.

**Sign safeguards:** waxed signs cannot be edited with these commands. Signs tracked by the plugin are associated with the first recorded editor; ordinary players can only use commands on signs recorded as theirs. Existing untracked signs require `vanillaplussuite.sign.admin`. This is not a replacement for a land-claim or protection plugin.

### Sign formatting codes

When permitted by configuration and permissions, sign text entered in the sign editor supports:

- **Standard colours:** `&0`–`&9`, `&a`–`&f` (example: `&aWelcome`).
- **Formatting:** `&k`, `&l`, `&m`, `&n`, `&o`, `&r` (example: `&lImportant`).
- **RGB colours:** `&#RRGGBB` (example: `&#FF69B4Pink`).

The plugin does not increase Minecraft's sign character limit or sign width.

## Permissions

The following nodes are declared in `plugin.yml` and can be managed through LuckPerms:

| Permission | Purpose | Default |
| --- | --- | --- |
| `vanillaplussuite.admin` | `/vps reload` | OP |
| `vanillaplussuite.sign.color` | Standard sign colours | Everyone |
| `vanillaplussuite.sign.hex` | RGB sign colours | Everyone |
| `vanillaplussuite.sign.format` | Sign formatting | OP |
| `vanillaplussuite.sign.command` | Sign styling commands | Everyone |
| `vanillaplussuite.sign.admin` | Bypass recorded sign ownership requirement | OP |

Permissions do not override a disabled feature in `config.yml`.

## Configuration

Each major module has its own `enabled` setting where applicable. The complete default configuration, including all customizable death messages, is in [`src/main/resources/config.yml`](src/main/resources/config.yml).

### General modules

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
  particle-chance: 0.10

pet-protection:
  enabled: true
  protect-own: true
  protect-others: false
  notify-attacker: true

item-frame-lock:
  enabled: true
  require-sneak-to-rotate: true
  require-sneak-to-remove: false

grave-coordinates:
  enabled: true
  send-on-respawn: true
  send-on-death: false

tool-durability-warning:
  enabled: true
  threshold-percent: 10
  cooldown-seconds: 45
```

Death coordinates are stored in memory only and are lost after a server restart. The current implementation sends the private notification after respawn; `send-on-death` is present in the default configuration but does not enable a separate on-death notification.

### Iron doors and trapdoors

```yaml
iron-door-interaction:
  enabled: true
  include-trapdoors: true
  play-sound: true
  respect-redstone: true
  auto-close:
    enabled: false
    delay-seconds: 5
```

`delay-seconds` is the number of seconds after manual opening, with a minimum effective delay of one second. The same auto-close settings apply to iron doors and iron trapdoors.

### Sign editing

```yaml
sign-editing:
  enabled: true
  commands:
    enabled: true
    max-distance: 6
  allow-color-codes: true
  allow-format-codes: false
  allow-hex-colors: false
```

### Bee Colony Cooperation

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

`empty-hives-only` applies to alternative honey-sharing destinations. The hive-selection and relocation logic also considers hive capacity. Avoid excessively large scan radii or frequent scans on busy servers.

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

This is a **shortened example**, not the complete default death-message library. The bundled config includes categories for fall, contextual falls, lava, fire, drowning, lightning, void, suffocation, starvation, freezing, magic, explosions, projectiles, mobs, village-adjacent mob deaths, PvP, Nether, and generic deaths. Use `{player}` for the deceased player and `{killer}` for PvP messages.

## Startup console banner

VanillaPlusSuite prints a compact ASCII banner **every time the plugin enables**:

```text
[VanillaPlusSuite]
[VanillaPlusSuite]   +--------------------------------+
[VanillaPlusSuite]   | [V+] VanillaPlusSuite v1.1.0   |
[VanillaPlusSuite]   |      by iruixos                |
[VanillaPlusSuite]   |      Paper 26.3 / MC 26.3      |
[VanillaPlusSuite]   +--------------------------------+
[VanillaPlusSuite]
```

The plugin version and Minecraft version are detected dynamically, and the box adjusts to the displayed text. The banner is always enabled; there is **no configuration toggle**. The `Paper` label shows the Minecraft version, not the full Paper build identifier.

## Design and compatibility notes

- Intended for a **near-vanilla Hard Survival** experience.
- Most gameplay changes are independently configurable; the startup banner is not.
- Iron-door/trapdoor interaction listens for player interaction only; mobs may still activate vanilla redstone devices where Minecraft permits.
- Existing protection plugins can cancel interactions before the plugin processes them.
- Bee Colony Cooperation is experimental and needs longer-term in-game validation.
- Test upgrades on a staging server, particularly when other plugins also alter signs, bee AI, or block interactions.

**Author:** iruixos
