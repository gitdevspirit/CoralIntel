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
- **Session stats** – a small HUD (plus a chat summary) showing what you've gained since launching the game or your last `.reset`: session time, active time, average game length, finals / FKDR, beds / BBLR, wins / WLR, kills / deaths and stars gained, all tracked live from chat (to two decimals, e.g. `+0.61✫`). Every stat can be toggled and reordered, the background can be turned off, and you can place it with Ctrl+click in the ClickGUI. See [Session stats](#session-stats).
- **Chat** – one ClickGUI panel with two sub-sections. *Pregame Messages*: up to three auto-sent chat messages when the countdown hits 10 seconds, with a configurable delay. *Snipe Messages*: one message you set with `.sm1` and send to all chat with `/shout` by typing `.sm` (it is never sent automatically).
- **Reminders** – jot down players who might be cheating (`.remind <player> [note]`) so you can tag them in Coral later. You're alerted if they show up in a later lobby. See [Reminders](#reminders).
- **Quick queue** – `.q 1s|2s|3s|4s` instead of typing `/play ...`.
- **ClickGUI** – every setting in one place (default key: Right Control).

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
| `Right Control` | Open the ClickGUI |

Rebind them with `.bind`. If you saved a config on an older version, your ClickGUI key may still be `Right Shift`; run `.bind clickgui RCONTROL` to switch.

## Using CoralIntel with other mods

CoralIntel is built so it can sit next to other client mods (including other mixin-based ones) without either breaking the other:

- **Key presses** use Forge's shared key event, not a Mixin hook. Only one mod can hook a given spot in Minecraft, so a hook-based key listener silently stops working when two mods try; Forge's event has no such limit.
- **Mixins only use stacking injectors** (`@Inject`, `@ModifyArg`, `@ModifyVariable`), never `@Redirect` / `@Overwrite` / `@ModifyConstant`, and every handler is named `coralintel$...` so it can't share a name with another mod's code. The tab-list logic lives in a normal class (`TabListFormatter`), not inside the mixin.
- **Separate files and config**: everything is saved under `config/CoralIntel/`, and the mixin config / refmap are named `coralintel`.
- **Same key in two mods**: if another mod opens its own screen on one of CoralIntel's keys, that mod's screen wins and you get a one-time chat hint. Pick a free key with `.bind clickgui <key>` or `.bind gui <key>`. `.clickgui` always works.

For contributors: `python3 scripts/check_mixins.py` enforces the mixin rules above, and CI runs it on every push, so a future change can't quietly reintroduce a clash.

## Session stats

Tracks your own Bedwars progress **live from the game chat** (the same lines that end up in `latest.log`), so a kill shows up the moment it happens instead of waiting for Hypixel's API. Every stat has its own counter and they are never mixed:

| Stat | Counted when |
| --- | --- |
| Kills / Deaths | you kill / die to someone in a normal (non-final) kill message, or die to the void, a fall and so on |
| Finals / Final deaths | you are the killer / the victim in a `FINAL KILL!` message |
| Beds broken / Beds lost | a `BED DESTRUCTION` message names you as the breaker / says `Your Bed` |
| Wins / Losses | at the end of a game: the `VICTORY!` title or your name on the winning team is a win, otherwise a loss |

```
Session Time: 3h 15m / Active: 2h 40m
Avg Game: 14m 10s
Finals: 1000 / FKDR: 3.00
Beds: 300 / BBLR: 2.00
Wins: 100 / WLR: 0.50
Kills: 41 / Deaths: 12
Stars: +0.61✫
```

- **Active time** – the session time only keeps running while you're inside a game; sitting in the lobby, queue or pregame pauses it. It sits next to the total session time, with the average length of the games you played to the end (a game you leave early or rejoin mid-way counts toward active time but not the average). It works even with *Track From Chat* off.
- **Stars** can't be read from chat, so they are the difference between your Hypixel API stats now and when the session started (they lag behind the game, see the notes). They are shown with the fraction, so progress through a level counts (`+0.61✫` is 61% of a star). The glyph and colour follow your prestige.
- **FKDR / BBLR / WLR** are the ratios of what you gained *this session*, not your lifetime ratios.
- **Chat summary** – as soon as a game ends, a one-line `This session » ...` summary is printed. `.session` prints it on demand and `.reset` restarts the session from your current stats.
- **Settings** (SessionStats panel, second row of the ClickGUI):
  - *Show Session Time*, *Show Active Time* and *Show Avg Game Length* sit at the top.
  - A *Show ...* toggle for each stat. Turning one off removes it from the HUD, and a line disappears when everything on it is off. Most toggles also apply to the chat summary.
  - *HUD Background* – turn the dark box off to leave just the text.
  - *Order: ...* sliders – decide which line goes where (lowest number on top).
  - *Summary After Games* – the automatic chat summary.
  - *Track From Chat* – on by default. Turn it off to fall back to API differences (the old behaviour) if Hypixel ever changes its chat messages.
- **Moving the HUD** – hold `Ctrl` and click anywhere in the ClickGUI to put it there (keep the mouse down to fine-tune), or open your inventory and drag it. The position is saved with your settings.
- **Reset button** – a `[Reset Session]` button appears under the HUD while your inventory is open, and does the same as `.reset`.

Notes: the session lives in memory only, so every launch starts a new one. Stars (and the starting point for them) come from Hypixel's API, which lags a little behind the game, so the stars from a game you just finished can take a minute or two to show up; they refresh after each game and after a world change, at most once every two minutes. A game you leave before it ends isn't counted as a win or a loss. If you're nicked, your nick is matched through your tab-list name, which is best-effort. Run `.idebug log` to see each event the tracker counted. While [Streamer mode](#features) is hiding your own stats, the automatic chat summaries are suppressed (`.session` still prints).

## Reminders

Saw someone who might be cheating but don't want to stop and tag them mid-game? `.remind <player> [note]` saves them (and a note) to `config/CoralIntel/reminders.json`. Run it again to add more notes.

- `.remind list` – everyone you've noted, newest first, with how many times you've met them.
- `.remind view <player>` – every note on one player.
- `.remind done <player>` – remove them once you've tagged them in Coral (or click the x in the **Reminders** panel of the ClickGUI).
- If a noted player shows up in a later lobby, you get a `[Reminder]` chat alert with your latest note.
- **End of game** – when a game ends, you get a chat reminder listing the noted players who were in it (met in the lobby, or noted during the game), with your latest note, so you can tag them right away. If you leave a game early there's no alert.

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
| `.sm1 <text\|clear>` | | Set your snipe message |
| `.sm` | | Send your snipe message to all chat with `/shout` |
| `.remind <player> [note]` / `list` / `view <player>` / `done <player>` | `.reminder`, `.rem` | Note a possible cheater to tag in Coral later |
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
