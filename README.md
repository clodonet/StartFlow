# StartFlow

StartFlow is a free, source-available Paper plugin designed to guide new players through onboarding steps and show server administrators exactly where players drop off.

Many players leave a Minecraft server in their first few minutes because they are unsure what to do next. StartFlow provides players with an interactive step-by-step guide menu, tracks their actions automatically, and generates an onboarding funnel report that reveals the exact steps causing player drop-off.

## Features

- Interactive Starter Guide: Visual GUI menu (`/guide`) showing active, locked, and completed onboarding steps with descriptive hints.
- Automatic Action Detection: Detects player actions automatically without requiring manual staff verification:
  - Setting home via beds or respawn anchors
  - Selling items through Vault economy or trading with villagers
  - Claiming land with GriefPrevention or placing starter chests
  - Entering defined cuboid regions (such as spawn markets or portals)
  - Executing specific commands
  - Gathering items or placing blocks
- Funnel Drop-off Reports: Visual admin GUI, chat summary, and CSV export (`/sf report`) displaying completion rates and identifying the exact step where players get stuck.
- Secure Reward System: Execute console commands upon completing individual steps or the entire guide. All rewards are backed by SQLite transactions to prevent double claims across reconnects or restarts.
- Offline Player Management: Admin GUI and commands to view progress, complete steps, or reset progress for both online and cached offline players.
- Daily Login Rewards: Optional daily rewards with consecutive day streaks and milestone bonuses.
- Playtime Milestone Rewards: Optional playtime tracking with configurable rewards and automatic AFK detection.
- Starter Extras: Optional rules acceptance gate, starter kit delivery, and customizable join/first-join/quit broadcast messages (all disabled by default).
- Ready-Made Templates: Pre-configured step sets for Survival, Skyblock, and Anarchy servers applied with a single command (`/sf template`).
- Multi-Language Support: Built-in translations for English, Turkish, German, Spanish, Russian, and Portuguese, served automatically based on client language.
- Folia Support: Region-threaded scheduling, tested on Folia 1.21.11 (join flow, rules, starter kit, rewards, reports).
- Update Checker & bStats: Integrated Modrinth version checking and anonymous bStats metrics.

## Requirements

- Server: Paper 1.21.x (or compatible fork)
- Java: Java 21 or newer
- Optional Integrations:
  - PlaceholderAPI: Display progress, streaks, and playtime on scoreboards, tab lists, and chat.
  - Vault: Economy transaction detection for shop and trading steps.
  - GriefPrevention: Land claim detection for protection steps.

StartFlow loads normally without soft dependencies; uninstalled integrations simply disable their corresponding detection triggers with a clear log notice.

## Installation

1. Download `StartFlow-1.0.0.jar` from the release page.
2. Place the jar file into your server's `plugins/` directory.
3. Restart your server or load the plugin.
4. Edit `plugins/StartFlow/config.yml` to fit your server setup, then run `/sf reload`.

## Quick Start (First 5 Minutes)

1. Apply a Starter Template:
   Run `/sf template survival confirm` (or choose `skyblock` or `anarsi`). This configures ready-made steps and automatically creates a backup of your previous configuration.
2. Test the Player Interface:
   Run `/guide` in-game to view the onboarding menu and see the steps as a player would.
3. Adjust Coordinates and Commands:
   Open `config.yml` and adjust region coordinates (such as the market area) or specific commands to match your server world.
4. Review Progression Data:
   As players join, run `/sf report` to view your funnel drop-off statistics directly in chat or `/sf report csv` to export detailed metrics.

## Commands and Permissions

### Player Commands

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/guide` | `/rehber`, `/basla`, `/başla` | `startflow.use` (default: true) | Opens the interactive guide menu |
| `/guide daily` | `/rehber gunluk` | `startflow.use` | Displays daily login streak and today's claim status |
| `/guide playtime` | `/rehber sure` | `startflow.use` | Displays active playtime and progress to the next milestone |
| `/guide rules` | `/rehber kurallar`, `/kurallar` | `startflow.use` | Shows server rules |
| `/guide rules accept` | `/kurallar kabul` | `startflow.use` | Accepts server rules |
| `/guide skip` | `/rehber atla` | `startflow.use` | Skips remaining guide steps |
| `/guide reset` | `/rehber sifirla` | `startflow.use` | Resets personal guide progress |

### Admin Commands

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/startflow menu` | `/sf`, `/sf menu` | `startflow.admin` (default: op) | Opens the admin management interface |
| `/startflow report` | `/sf rapor` | `startflow.admin` | Displays the player drop-off funnel summary in chat |
| `/startflow report csv` | `/sf rapor csv` | `startflow.admin` | Exports the onboarding funnel report to a CSV file |
| `/startflow template <name> [confirm]` | `/sf sablon <name> [onayla]` | `startflow.admin` | Applies a template (`survival`, `skyblock`, `anarsi`) |
| `/startflow player <name> info` | `/sf oyuncu <name> bilgi` | `startflow.admin` | Displays completed steps for a player |
| `/startflow player <name> step <id>` | `/sf oyuncu <name> adim <id>` | `startflow.admin` | Marks a specific step as complete for a player |
| `/startflow player <name> reset [id]` | `/sf oyuncu <name> sifirla [id]` | `startflow.admin` | Resets all progress or a single step for a player |
| `/startflow player <name> reset-daily` | `/sf oyuncu <name> gunluk-sifirla` | `startflow.admin` | Resets daily login streak for a player |
| `/startflow player <name> reset-playtime` | `/sf oyuncu <name> sure-sifirla` | `startflow.admin` | Resets playtime records for a player |
| `/startflow player <name> reset-rules` | `/sf oyuncu <name> kural-sifirla` | `startflow.admin` | Resets rules acceptance state for a player |
| `/startflow player <name> reset-kit` | `/sf oyuncu <name> kit-sifirla` | `startflow.admin` | Resets starter kit delivery state for a player |
| `/startflow reload` | `/sf yenile` | `startflow.admin` | Reloads configuration and language files |
| `/startflow about` | `/sf hakkinda` | `startflow.admin` | Displays plugin version and information |

## Configuration Overview

Configuration options are organized into modular sections inside `config.yml`:

- `steps`: Defines the onboarding pipeline. Each step specifies:
  - `id`: Unique step identifier
  - `trigger`: Action type (`home`, `sale`, `claim`, `region`, `command`, `place`)
  - `fallback`: Optional alternative trigger if a soft dependency is not present (e.g. trading instead of shop sale, chest placement instead of land claim)
  - `rewards`: List of console commands executed when the step is completed
  - `icon`: Material displayed in the guide inventory menu
- `completion-rewards`: Console commands granted when a player completes all steps.
- `daily-reward`: Daily login streak settings, reward commands, and milestone bonuses (disabled by default).
- `playtime-reward`: Active playtime milestones with AFK timeout detection (disabled by default).
- `rules`: Rules acceptance gate with optional interaction restriction until accepted (disabled by default).
- `starter-kit`: First-join starter item bundle (disabled by default). Item `name` and `lore` can be plain text or a per-language map (`en:`, `tr:`) so each player sees the kit in their own client language; a missing language falls back to the default language.
- `join-messages`: Custom first-join, return-join, and quit messages (disabled by default).
- `default-language`: Default message language when client language is not matched (default: `tr`).

## Customizing Rules and Reloading

Write your server rules once in `config.yml` and they are used for every language:

```yaml
rules:
  enabled: true
  lines:
    - "1. Be kind to other players."
    - "2. No cheating."
```

To show each player the rules in their own Minecraft client language, use `lines-by-language` instead. A language that is not listed falls back to `lines`:

```yaml
rules:
  enabled: true
  lines-by-language:
    en:
      - "1. Be kind to other players."
    tr:
      - "1. Diğer oyunculara saygılı ol."
```

If both are empty, the per-language texts from `messages/<lang>.yml` are used instead. After editing, run `/sf reload`. If `config.yml` has a syntax error (for example a wrong indent), the reload reports the line and keeps your previous settings active.

## Placeholders

When PlaceholderAPI is installed, the following placeholders are available:

| Placeholder | Output Example | Description |
|---|---|---|
| `%startflow_progress%` | `3/5` | Number of completed steps over total enabled steps |
| `%startflow_progress_percent%` | `60` | Guide completion percentage (0-100) |
| `%startflow_step%` | `Protect your land` | Display name of the player's current step, or `Complete` |
| `%startflow_completed%` | `Yes` / `No` | Whether the player has completed all guide steps |
| `%startflow_bar%` | Visual bar string | Colored progress bar using MiniMessage formatting |
| `%startflow_streak%` | `4` | Current consecutive daily login streak count |
| `%startflow_best_streak%` | `12` | Highest daily login streak reached |
| `%startflow_playtime%` | `3h 25m` | Total tracked active playtime |
| `%startflow_playtime_hours%` | `3` | Total tracked active playtime in whole hours |
| `%startflow_next_milestone%` | `veteran` | Identifier of the next playtime milestone |

## Languages

StartFlow detects player client locales and automatically displays messages in their preferred language. If a player's locale is not supported, messages fall back to `default-language` in `config.yml`.

Included languages:
- <img src="https://raw.githubusercontent.com/clodonet/StartFlow/main/assets/flags/gb.png" width="22" alt="English"> English (`en.yml`)
- <img src="https://raw.githubusercontent.com/clodonet/StartFlow/main/assets/flags/tr.png" width="22" alt="Turkish"> Turkish (`tr.yml`)
- <img src="https://raw.githubusercontent.com/clodonet/StartFlow/main/assets/flags/de.png" width="22" alt="German"> German (`de.yml`)
- <img src="https://raw.githubusercontent.com/clodonet/StartFlow/main/assets/flags/es.png" width="22" alt="Spanish"> Spanish (`es.yml`)
- <img src="https://raw.githubusercontent.com/clodonet/StartFlow/main/assets/flags/ru.png" width="22" alt="Russian"> Russian (`ru.yml`)
- <img src="https://raw.githubusercontent.com/clodonet/StartFlow/main/assets/flags/br.png" width="22" alt="Portuguese"> Portuguese (`pt.yml`)

All language files can be edited in `plugins/StartFlow/messages/`.

## Building from Source

To compile the plugin from source:

1. Clone the repository.
2. Ensure Java 21 JDK is installed.
3. Run the Gradle build:

```bash
./gradlew build
```

The compiled shaded jar will be located at:
`build/libs/StartFlow-1.0.0.jar`

## License

StartFlow is source-available and free to use on your own servers. You may read the code, change the configuration, and contribute through pull requests. Publishing or redistributing StartFlow, forks, or modified versions, and removing the Clodo credits, requires written permission. The StartFlow and Clodo names and logo are trademarks. See the [LICENSE](LICENSE) file for the full terms.
