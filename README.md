# CoralIntel

A Forge 1.8.9 client mod for Hypixel Bedwars that scouts your lobby before the game starts. It reads stats for everyone in the lobby, flags cheaters, and shows it all in a draggable HUD overlay, an enhanced tab list and a ClickGUI.

[IMAGE]

## Features

- **Intel HUD overlay** – every player's star, FKDR, WLR, winstreak and threat level in a compact table. Sortable, grouped by team with team-coloured names, adjustable column width, opacity, scale and colours.
- **Pregame scouting** – players who talk in the pregame lobby are picked up automatically and their stats load in the background. Nicked players are announced in chat and shown on the HUD.
- **Nick detection** – based on [Mellow](https://github.com/Roxiun/Mellow)'s logic: nicks are never looked up as real accounts.
- **Enhanced tab list** – prestige-coloured stars, FKDR/WLR and other stats, `[NICK]` tags, optional HP and Seraph-style layout.
- **Cheater tags** – Coral tag lookups (`.coralkey`), blacklist / safelist, and `CC` (closet cheater) / `CCC` (confirmed cheater) tags.
- **BedWarsTag** – floating star / FKDR / threat tag above players' heads (depth-tested, no wall-hack).
- **AntiCheat** – heuristic movement/combat checks (NoSlow, AutoBlock, Sprint, Velocity, Rotation, Scaffold) that only inform you about other players.
- **Resilient stats fetching** – Hypixel API with automatic keyless fallbacks (Bordic, Slothpixel). Already-loaded players are never reloaded when the roster is re-sorted.
- **Pregame messages** – up to three auto-sent chat messages when the countdown hits 10 seconds, with a configurable delay.
- **Quick queue** – `.q 1s|2s|3s|4s` instead of typing `/play ...`.
- **ClickGUI** – every setting in one place (default key: Right Shift).

## Installation

1. Install [Minecraft Forge 1.8.9](https://files.minecraftforge.net/) (build 11.15.1.2318).
2. Download the latest `CoralIntel` jar from the [Releases](../../releases) page.
3. Put the jar in your `.minecraft/mods` folder and launch the game.
4. Join Hypixel and run `/api new` – the key is detected automatically from your log, or set it yourself with `.intelkey <key>`.
5. *(Optional)* Set a Coral key with `.coralkey <key>` for cheater tags.

### Building from source

```bash
git clone https://github.com/gitdevspirit/CoralIntel.git
cd CoralIntel
./gradlew build
```

The jar ends up in `build/libs/`. The Gradle toolchain targets Java 8.

## Default keys

| Key | Action |
| --- | --- |
| `H` | Toggle the HUD |
| `L` | Open the Intel GUI |
| `Right Shift` | Open the ClickGUI |

Rebind them with `.bind`.

## Commands

The command prefix is `.` (typed in chat, never sent to the server).

| Command | Aliases | Description |
| --- | --- | --- |
| `.c <s\|l\|list\|help> [name]` | `.config` | Save / load a config, or list all commands |
| `.clickgui` | `.cgui` | Open the ClickGUI |
| `.bw <ign>` | `.bwstats` | One-off Bedwars stat lookup (fields are configurable in LobbyIntel settings) |
| `.daily [ign]` | | Bedwars stats from the last 24 hours (no name = everyone in the lobby) |
| `.monthly [ign]` | | Bedwars stats from the last 30 days (no name = everyone in the lobby) |
| `.view <player>` | `.coralview`, `.cview` | Show a player's Coral tags |
| `.add <player>` | `.inteladd`, `.aintel` | Add a player to the Intel roster |
| `.remove <player>` | `.intelremove`, `.rintel` | Remove a manually added player |
| `.blacklist <player> [reason]` / `list` | `.kos`, `.bl` | Blacklist a player |
| `.unblacklist <player>` | `.unkos`, `.unbl` | Remove a player from the blacklist |
| `.safelist <player> [reason]` / `list` | `.sl` | Safelist a player |
| `.unsafelist <player>` | `.unsl` | Remove a player from the safelist |
| `.q <1s\|2s\|3s\|4s>` | `.queue` | Queue solos / doubles / 3v3v3v3 / 4v4v4v4 |
| `.pgm1` `.pgm2` `.pgm3` `<text\|clear>` | | Set the auto-sent pregame messages |
| `.intelkey <key>` | `.ikey` | Set your Hypixel API key |
| `.coralkey <key>` | `.urchinkey`, `.ukey` | Set your Coral API key |
| `.intelpath` | `.ipath` | Set the log path used for API key auto-detection |
| `.bind <hud\|gui\|clickgui\|tag\|ac> <key>` / `list` | | Rebind CoralIntel keys |
| `.role list\|set\|remove\|check` | | Manage player roles |
| `.inteldebug <name>` / `log` / `clear` | `.idebug` | Debug the stats fetch |

## Configuration

Settings are saved to `config/CoralIntel/` inside your Minecraft folder. Use the ClickGUI or `.c s <name>` / `.c l <name>` to save and load named configs.

## Disclaimer

CoralIntel only reads public stats and information the game already sends your client. Make sure any use complies with the rules of the server you play on.
