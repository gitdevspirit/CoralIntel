# CoralIntel

A Forge 1.8.9 client mod for Hypixel Bedwars that scouts your bedwars lobby. It reads stats for everyone in the lobby, flags cheaters, and shows it all in a HUD overlay, and an enhanced tab list.

<img width="500" height="460" alt="add players" src="https://github.com/user-attachments/assets/c9ecc4f9-8257-4a05-b192-eb6a83081f5c" />

## Features

- **Intel HUD overlay** – every player's star, FKDR, WLR, winstreak and threat level in a compact table. Sortable, grouped by team with team-coloured names, adjustable column width, opacity, scale and colours.
- **Pregame scouting** – players who talk in the pregame lobby are picked up automatically and their stats load in the background. Nicked players are announced in chat and shown on the HUD.
- **Nick detection** – based on [Mellow](https://github.com/Roxiun/Mellow)'s logic: nicks are never looked up as real accounts.
- **Enhanced tab list** – prestige-coloured stars, FKDR/WLR and other stats, `[NICK]` tags, optional HP and Seraph-style layout.
- **Cheater tags** – Coral tag lookups (`.coralkey`), blacklist / safelist, and `CC` (closet cheater) / `CCC` (confirmed cheater) tags.
- **BedWarsTag** – floating star / FKDR / threat tag above players' heads (depth-tested, no wall-hack).
- **AntiCheat** – heuristic movement/combat checks (NoSlow, AutoBlock, Sprint, Velocity, Rotation, Scaffold) that only inform you about other players.
- **Resilient stats fetching** – Hypixel API with automatic keyless fallbacks (Bordic, Slothpixel). Already-loaded players are never reloaded when the roster is re-sorted.
- **Stat cache** – fully loaded stats are saved to disk for 0–30 minutes (default 15, set it in the LobbyIntel settings), so re-queuing into the same players doesn't re-fetch them and eases rate limits. `.bw` always fetches fresh.
- **Streamer mode** – hides your own stats and tags on the tab list, HUD and floating tag, and lets you show a custom name on your tag/tab row or disable your tag entirely. Lives in its own panel on the second row of the ClickGUI.
- **Session stats** – a small HUD (plus a chat summary) showing what you've gained since launching the game or your last `.reset`: session time, finals / FKDR, beds / BBLR, wins / WLR, kills / deaths and stars gained (to two decimals, e.g. `+0.61✫`). Every stat can be toggled and reordered, the background can be turned off, and you can place it with Ctrl+click in the ClickGUI. See [Session stats](#session-stats).
- **Pregame messages** – up to three auto-sent chat messages when the countdown hits 10 seconds, with a configurable delay.
- **Quick queue** – `.q 1s|2s|3s|4s` instead of typing `/play ...`.
- **ClickGUI** – every setting in one place (default key: Right Shift).

## Installation

1. Install [Minecraft Forge 1.8.9](https://files.minecraftforge.net/) (build 11.15.1.2318).
2. Download the latest `CoralIntel` jar from the [Releases](../../releases) page.
3. Put the jar in your `.minecraft/mods` folder and launch the game.
4. Join Hypixel and run `/api new` – the key is detected automatically from your log, or set it yourself with `.intelkey <key>`.
5. *(Optional but Recommended)* Set a Coral key with `.coralkey <key>` for cheater tags.

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

## Session stats

Tracks your own Bedwars progress. Your lifetime totals are fetched a few seconds after you join a server and everything shown is *current totals minus that baseline*.

```
Session Time: 3h 15m
Finals: 1000 / FKDR: 3.00
Beds: 300 / BBLR: 2.00
Wins: 100 / WLR: 0.50
Kills: 41 / Deaths: 12
Stars: +0.61✫
```

- **Stars** are shown with the fraction, so progress through a level counts (`+0.61✫` is 61% of a star). The glyph and colour follow your prestige.
- **FKDR / BBLR / WLR** are the ratios of what you gained *this session*, not your lifetime ratios.
- **Chat summary** – after you finish a game, a one-line `This session » ...` summary is printed. `.session` prints it on demand and `.reset` restarts the session from your current stats.
- **Settings** (SessionStats panel, second row of the ClickGUI):
  - A *Show ...* toggle for each stat. Turning one off removes it from the HUD, and a line disappears when everything on it is off. Most toggles also apply to the chat summary.
  - *HUD Background* – turn the dark box off to leave just the text.
  - *Order: ...* sliders – decide which line goes where (lowest number on top).
  - *Summary After Games* – the automatic chat summary.
- **Moving the HUD** – hold `Ctrl` and click anywhere in the ClickGUI to put it there (keep the mouse down to fine-tune), or open your inventory and drag it. The position is saved with your settings.
- **Reset button** – a `[Reset Session]` button appears under the HUD while your inventory is open, and does the same as `.reset`.

Notes: the session lives in memory only, so every launch starts a new one. Hypixel's API lags a little behind the game, so a game you just finished can take a minute or two to show up. Stats refresh automatically after a world change, at most once every two minutes. While [Streamer mode](#features) is hiding your own stats, the automatic chat summaries are suppressed (`.session` still prints).

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
| `.session` | `.sess` | Print your session stats (wins, kills, FKDR, BBLR, ... gained) |
| `.reset` | | Restart your session stats from your current stats |
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

CoralIntel only reads public stats and information the game already sends your client. Make sure any use complies with the rules of the server you play on. As all mods are, CoralIntel is strictly use at your own risk, we do not take any responsibility for **any** bans whatsoever.

## Credits

@spiritualizes on discord for any ideas, issues, or queries.
