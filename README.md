# CoralIntel

A Forge 1.8.9 client mod for Hypixel Bedwars that scouts your bedwars lobby. It reads stats for everyone in the lobby, flags cheaters, and shows it all in a HUD overlay, and an enhanced tab list.

<img width="500" height="460" alt="add players" src="https://github.com/user-attachments/assets/c9ecc4f9-8257-4a05-b192-eb6a83081f5c" />

## Features

- **HUD modes** – *HUD: Mode* (Full, Compact, Scout, Threats, Enemies, Teammates, Sweats, Auto; Auto uses *HUD: Auto Pregame Mode* / *Auto In-Game Mode*, which you pick) changes which columns and players show, independent of the style. Each mode can use its own style via *HUD: <Mode> Style* (Default = the main *HUD: Style*), so e.g. Auto can look different pregame and in game.
- **HUD themes + sizing** – *HUD: Theme* (Midnight, Sunset, Hacker, Clean, Neon, Crimson) sets style, colours and opacity in one go; *HUD: Custom Sizing* unlocks the Row Height and Corner Radius sliders (off = built-in look). The ClickGUI also grows in when opened.
- **HUD styles** – *HUD: Style* (LobbyIntel panel): Classic, Minimal (floating rows), Striped, Cards (a box per team), Outline (see-through with a bold frame) Heatmap (rows tinted by threat), Glass (frosted), Neon (glowing frame), Terminal (green-on-black), Pills (a pill per row), Team Bars (team-coloured row bars) or Threat Bar (threat-coloured row bars). Same columns and stats in every style; players are always grouped by team.
- **Intel HUD overlay** – every player's star, FKDR, WLR, winstreak and threat level in a compact table. Sortable, grouped by team with team-coloured names, adjustable column width, opacity, scale and colours.
- **Pregame scouting** – players who talk in the pregame lobby are picked up automatically and their stats load in the background. Nicked players are announced in chat and shown on the HUD.
- **Nick detection** – based on [Mellow](https://github.com/Roxiun/Mellow)'s logic: nicks are never looked up as real accounts.
- **Enhanced tab list** – prestige-coloured stars, FKDR/WLR and other stats, `[NICK]` tags, optional HP. In Bed Wars (pregame and in game) the whole tab list is replaced by a Seraph-style overlay. The Bed Wars overlay version of the Seraph-style tab list is based on [Zoobooo/tabstats](https://github.com/Zoobooo/tabstats).
- **Cheater tags** – Coral tag lookups (`.coralkey`), blacklist / safelist, and `CC` (closet cheater) / `CCC` (confirmed cheater) tags.
- **BedWarsTag** – floating star / FKDR / threat tag above players' heads (depth-tested, no wall-hack).
- **AntiCheat** – heuristic movement/combat checks (NoSlow, AutoBlock, Sprint, Velocity, Rotation, Scaffold) that only inform you about other players.
- **Resilient stats fetching** – Hypixel API with automatic keyless fallbacks (Bordic, Slothpixel). Already-loaded players are never reloaded when the roster is re-sorted.
- **Stat cache** – fully loaded stats are saved to disk for 0–30 minutes (default 15, set it in the LobbyIntel settings), so re-queuing into the same players doesn't re-fetch them and eases rate limits. `.bw` always fetches fresh.
- **Streamer mode** – hides your own stats and tags on the tab list, HUD and floating tag, and lets you show a custom name on your tag/tab row or disable your tag entirely. Lives in its own panel on the second row of the ClickGUI.
- **Session stats** – a small HUD (plus a chat summary) showing what you've gained since launching the game or your last `.reset`: session time, active time, average game length, finals / FKDR, beds / BBLR, wins / WLR, kills / deaths and stars gained, all tracked live from chat (to two decimals, e.g. `+0.61✫`). Every stat can be toggled and reordered, the background can be turned off, and you can place it with Ctrl+click in the ClickGUI. See [Session stats](#session-stats).
- **Chat** – one ClickGUI panel with two sub-sections. *Pregame Messages*: up to three auto-sent chat messages when the countdown hits 10 seconds, with a configurable delay. *Snipe Messages*: one message you set with `.sm1` and send to all chat with `/shout` by typing `.sm` (it is never sent automatically).
- **Denick (Bedlify)** – shows who is likely behind a nicked player, in the tab list and in chat, using the [Bedlify API](https://api.bedlify.xyz/docs). Needs your own free key. See [Denick](#denick-bedlify).
- **Reminders** – jot down players who might be cheating (`.remind <player> [note]`) so you can tag them in Coral later. You're alerted if they show up in a later lobby. See [Reminders](#reminders).
- **Shop auto middle-click** – optional setting (LobbyIntel: *Shop: Auto Middle-Click (Quick Buy)*): in the Bed Wars shop's category pages (Blocks, Melee, Armor, Tools, Ranged, Potions, Utility) a left click buys the item and also sends the middle click, so it is added to Quick Buy without a second click. Off by default.
- **Scripting (Lua)** – drop small `.lua` scripts into `config/CoralIntel/scripts/` to add your own commands, modules (with toggle / slider / text settings in the ClickGUI), chat alerts and HUD text, without rebuilding the client. Sandboxed: scripts can't send chat to the server or touch your files, and a broken script is disabled on its own. See [Scripting](#scripting-lua).
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

## Denick (Bedlify)

CoralIntel can look up who is behind a nick through the Bedlify API.

1. Get a key: join `bedlify.xyz` in Minecraft (1.8+) to get a 6-digit code, then DM `/link <code>` to the Bedlify bot on Discord ([discord.gg/antisniper](https://discord.gg/antisniper)). The bot DMs you your key.
2. In game, run `.bedlify key <your key>`.

From then on every nicked player in your lobby is looked up in the background. You get a chat line like `[NICK] Nick is likely RealName (seen 3h ago, 2 games)`, and the tab list shows the **real player's stats** on the nick's row, in both the normal and Seraph tab styles (once they've loaded): `Nick (RealName)` with their star, FKDR, WLR, tag badge and so on. `.bw RealName` gets you the full stats.

- `.denick <nick>` looks up any nick; `.denick` alone redoes every nicked player in the lobby.
- `.bedlify` shows whether a key is set (masked) and how many requests you have left; `.bedlify clear` removes it.
- Settings (LobbyIntel panel): *Denick Nicks (Bedlify)*, *Denick: Chat Alert*, *Tab: Show Denicked Name*, *Tab: Show Denicked Stats*.

Notes: a nick can be reused by different players over time, so the newest match is a best guess; the age and game count are there so you can judge. Your key is personal (Bedlify's rules: don't share or publish it), so it is **never part of the mod**. It is saved only on your machine in `config/CoralIntel/bedlify.json`, sent in a request header, never shown in full, and never sent to the Minecraft server (`.` commands stay local). Don't share your `config/CoralIntel` folder. Lookups are cached and stay far under Bedlify's 120 requests/minute limit.

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
| `.scripts` / `reload` / `enable <name>` / `disable <name>` | | List, reload, enable or disable Lua scripts. See [Scripting](#scripting-lua) |
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
| `.tab [on\|off\|size\|bg\|shadow\|gray\|respawn\|dc\|col <1-6> <stat>\|reset]` | `.tabo` | Change the Bed Wars tab overlay from chat |
| `.denick [nick]` | | Find who is behind a nick (Bedlify) |
| `.bedlify key <key>` / `clear` | | Set or remove your Bedlify API key |
| `.remind <player> [note]` / `list` / `view <player>` / `done <player>` | `.reminder`, `.rem` | Note a possible cheater to tag in Coral later |
| `.intelkey <key>` | `.ikey` | Set your Hypixel API key |
| `.coralkey <key>` | `.urchinkey`, `.ukey` | Set your Coral API key |
| `.intelpath` | `.ipath` | Set the log path used for API key auto-detection |
| `.bind <hud\|gui\|clickgui\|tag\|ac> <key>` / `list` | | Rebind CoralIntel keys |
| `.role list\|set\|remove\|check` | | Manage player roles |
| `.inteldebug <name>` / `log` / `clear` | `.idebug` | Debug the stats fetch |

## Scripting (Lua)

Write small scripts without editing or rebuilding the client. Scripts are plain Lua 5.1 files (run by [LuaJ](https://github.com/luaj/luaj), built into the mod) in:

```
.minecraft/config/CoralIntel/scripts/*.lua
```

They load when the game starts and again on `.scripts reload` (or the **Reload** button in the ClickGUI's **Scripts** panel). A script's name is its file name without `.lua` (letters, digits, `_` and `-`).

On the very first run three example scripts are copied into the folder, **switched off**: `gg_reminder`, `fkdr_alert` and `game_recap` (their source is in `src/main/resources/coralintel/scripts/`). Turn on the ones you want in the Scripts panel or with `.scripts enable <name>`.

| Command | Description |
| --- | --- |
| `.scripts` | List every script with its status (running / disabled / error + the last error) |
| `.scripts reload` | Unload everything, re-scan the folder, load again (settings are kept) |
| `.scripts enable <name>` / `disable <name>` | Turn one script on or off |

**ClickGUI → Scripts panel:** every script with an on/off toggle and a status line, a *Reload scripts* button and an *Open scripts folder* button (opens the folder and prints its path in chat). Each module a script registers gets its own panel, with its settings working like the built-in modules. It disappears when the script is disabled or reloaded and comes back in the same place.

**Saved for you:** each script's on/off state and every module's on/off state and settings are saved in `config/CoralIntel/script-state.json` and restored on the next start. A script's own data (`data.*`, below) is saved in `config/CoralIntel/scripts/data/<name>.json`.

### A first script

```lua
-- scripts/hello.lua
command.register("hello", function(args)
  chat.print("&aHello &f" .. (args[1] or "world") .. "&a!")
end, "Says hello")
```

Run `.scripts reload`, then type `.hello Steve`.

### What scripts can and can't do

Scripts only get the functions documented here. There is deliberately **no** function for any of the following, so a script cannot do them:

- send chat or commands to the server (`chat.print` only ever shows a line to you; its text can't carry click actions)
- read or write files (except its own `data.*` store), open network connections, or use Java classes / reflection
- load other code (`require`, `load`, `dofile`, `package`, `io`, `os.execute`, `debug` and `coroutine` don't exist)
- change or cancel incoming chat, packets or game state

Safety limits, enforced by the host:

| Limit | Value |
| --- | --- |
| Time per callback (event, command, `onChange`, ...) | 50 ms |
| Time for a script's top-level code when it loads | 250 ms |
| Total time spent on queued events per game tick | about 100 ms (the rest waits for the next tick) |
| Queued events (chat / title / join / leave) | 512, oldest dropped |
| `chat.print` / `sound.play` | 10 lines / 5 sounds per second per script |
| Per script | 20 commands, 8 modules, 24 settings per module, 64 listeners, 16 HUD labels |
| `string.rep` result | 64 KB (also: `string.format` width/precision max 2 digits) |
| Script file size | 256 KB |
| `data` store | 256 keys, 64 KB total, nesting depth 4 |

If a script throws an error, runs past its time limit, or recurses too deep, the error is printed in chat and shown in the Scripts panel / `.scripts`, and **only that script** is unloaded (its commands, modules, listeners and HUD labels go away). Fix the file and use `.scripts reload`, or toggle it off and on. Memory use is not limited, so don't build huge tables.

> Use dot calls everywhere, e.g. `m.get("Min FKDR")`, not `m:get(...)`.

### API reference

#### `command.register(name, fn, description)`

Adds a client command `.name`. `fn` receives a table of the words typed after it. Names are `a-z`, `0-9`, `_`, `-` (max 24) and can't clash with a built-in command or another script's command. The description appears in `.c help`.

```lua
command.register("echo", function(args)
  chat.print("&7You said: &f" .. table.concat(args, " "))
end, "Repeats what you type")
```

#### `module.register{ ... }` → module handle

Creates a module that gets its own ClickGUI panel. The table takes `name` (required, max 32 characters, unique), `description`, `enabled` (default `false`; a saved value wins) and a `settings` list. Each setting needs a `type` and a unique `name`:

| type | extra fields |
| --- | --- |
| `"toggle"` | `default` (boolean, default `false`) |
| `"slider"` | `min`, `max`, `step` (all required, `max > min`, `step > 0`), `default` (default `min`) |
| `"text"` | `default` (string, max 100 characters) |

```lua
local m = module.register{
  name = "Greeter",
  description = "Greets players as they join",
  enabled = false,
  settings = {
    { type = "toggle", name = "Sound",  default = true },
    { type = "slider", name = "Volume", default = 3, min = 1, max = 10, step = 1 },
    { type = "text",   name = "Prefix", default = "&aWelcome" },
  },
}
```

The handle has:

| Function | What it does |
| --- | --- |
| `m.get(setting)` | Current value (boolean, number or string). Unknown names raise an error |
| `m.set(setting, value)` | Sets a value (boolean / number / string to match the type; sliders snap to the step). Doesn't trigger `onChange` |
| `m.enabled()` | `true` while the module is on |
| `m.onEnable(fn)` / `m.onDisable(fn)` | Called when the module is switched on or off (also once after load if it starts on) |
| `m.onChange(fn)` | `fn(settingName, value)` when you change a setting in the ClickGUI (dragging a slider is coalesced to the latest value) |
| `m.on(event, fn)` | Like `events.on`, but only delivered **while the module is on**; returns an id |

```lua
m.onChange(function(name, value)
  chat.print("&7" .. name .. " is now &f" .. tostring(value))
end)
m.onEnable(function() chat.print("&aGreeter on") end)
m.onDisable(function() hud.clear() end)
m.on("player_join", function(name)
  chat.print(m.get("Prefix") .. " &f" .. name)
end)
```

#### `events.on(name, fn)` → id, `events.off(id)`

Always delivered while the script runs. `events.off(id)` removes a listener and returns `true` if it existed.

| Event | Arguments | When |
| --- | --- | --- |
| `"chat"` | `text, formatted` | A chat line arrives. `text` has colour codes removed, `formatted` keeps the `§` codes. Action-bar text isn't included. Read-only |
| `"tick"` | none | Every client tick (20 per second) while in a world |
| `"world_load"` | none | A new world loads (joining a server, switching lobby) |
| `"player_join"` / `"player_leave"` | `name` | A real player appears in / disappears from the tab list (NPCs are skipped; checked every 5 ticks) |
| `"title"` | `kind, text` | A title packet. `kind` is `"title"` or `"subtitle"`, `text` has colour codes removed |

Chat and title events reach your function up to one tick after they arrive. The tab list at the moment a script loads is not announced as joins; after a new world loads, everyone in it counts as joining. The pregame lobby tab list may not show real names, so `player_join` is most reliable once the game has started; `lobby.players()` also includes players picked up from chat.

```lua
local id = events.on("chat", function(text)
  if text:find("bed was destroyed") then sound.play("random.orb") end
end)
events.on("world_load", function() events.off(id) end)
```

#### `chat.print(message)` / `print(...)`

Shows a line **only to you** (client side). `&` colour codes work. Newlines are flattened and the line is capped at 256 characters. `print(...)` does the same with a grey `[script]` prefix, handy for debugging.

```lua
chat.print("&bHello &7from a script")
print("value is", 42)
```

#### `sound.play(name)`

Plays a game sound by name (letters, digits, `_`, `.`; unknown names are ignored). Examples: `"random.orb"`, `"note.pling"`, `"random.levelup"`.

```lua
sound.play("note.pling")
```

#### `hud.text(id, text, x, y, color)`, `hud.remove(id)`, `hud.clear()`

Keeps a line of text on screen. The client draws it every frame, so you only call `hud.text` when the text changes. `x`/`y` are in GUI pixels from the top-left, `color` is optional (`0xRRGGBB`, or `0xAARRGGBB`; default white). `&` colour codes work, text is capped at 200 characters, and calling `hud.text` again with the same `id` updates that label.

```lua
local ticks = 0
events.on("tick", function()
  ticks = ticks + 1
  if ticks % 20 == 0 then hud.text("clock", os.date("%H:%M:%S"), 5, 5, 0x55FFFF) end
end)
-- later: hud.remove("clock")   or   hud.clear()
```

#### `session.get()` → table

Your session numbers (the same ones as Session stats; all `0` until there is data):
`seconds`, `active_seconds`, `games`, `wins`, `losses`, `kills`, `deaths`, `final_kills`, `final_deaths`, `beds_broken`, `beds_lost`, `fkdr`, `bblr`, `wlr`, `stars_gained`.

```lua
local s = session.get()
chat.print(string.format("&7%d games, FKDR &f%.2f", s.games, s.fkdr))
```

#### `lobby.players()` → list of names, `lobby.me()` → your name

`lobby.players()` returns everyone the lobby tracker knows about (tab list plus players picked up from chat). `lobby.me()` is your own name.

```lua
for _, name in ipairs(lobby.players()) do print(name) end
```

#### `lobby.player(name)` → table or `nil`

A copy of one player's data (changing it does nothing). Fields: `name`, `team` (when known), `star`, `fkdr`, `wlr`, `winstreak`, `final_kills`, `final_deaths`, `beds_broken`, `beds_lost`, `kills`, `deaths`, `wins`, `losses`, `nicked`, `loaded`, `stats_hidden`, `tags`, `blacklisted`, `safelisted`.

`loaded` becomes `true` once the lobby tracker considers the player's stats final; before that the numbers are `0`, so check it first. `tags` is a list of the tag labels shown for the player (e.g. `{"CC"}`).

```lua
local p = lobby.player("Steve")
if p and p.loaded and p.fkdr > 8 then
  chat.print("&c" .. p.name .. " has " .. string.format("%.2f", p.fkdr) .. " FKDR")
end
if p and #p.tags > 0 then chat.print("tags: " .. table.concat(p.tags, ", ")) end
```

#### `lobby.suspicion(name)` → table or `nil`

The same score as the `.cheat` command (CheatSuspicion): `score`, `tier` (text) and `reasons` (list of strings).

```lua
local s = lobby.suspicion("Steve")
if s and s.score >= 50 then
  chat.print("&e" .. s.tier .. " (" .. s.score .. "): " .. table.concat(s.reasons, "; "))
end
```

#### `data.get(key, default)`, `data.set(key, value)`, `data.delete(key)`, `data.keys()`

A small saved store that only this script can use. Keys are strings up to 64 characters; values are strings, numbers, booleans or tables of those (nesting depth 4). `data.set(key, nil)` deletes. `data.delete` returns whether the key existed, `data.keys()` returns a list. It is saved automatically and on exit.

```lua
local runs = data.get("runs", 0) + 1
data.set("runs", runs)
data.set("last", { when = os.date("%Y-%m-%d"), count = runs })
chat.print("This script has run " .. runs .. " times")
```

#### Standard library

`string` (including `format` with correct `%f`/`%e`/`%g`), `table`, `math`, `os.time()`, `os.clock()`, `os.date(format)` (supports `%Y %y %m %d %H %M %S %%`), and the basics (`pairs`, `ipairs`, `type`, `tostring`, `tonumber`, `pcall`, `error`, `select`, ...). `pcall` works, but it cannot be used to swallow the time limit.

### Example scripts

| Script | What it does |
| --- | --- |
| `gg_reminder.lua` | When a game ends (`VICTORY!` / `GAME OVER!` title) prints a reminder to say gg, with an optional sound. It can't type gg for you; scripts can't send chat |
| `fkdr_alert.lua` | *FKDR Alert* module with a **Min FKDR** slider: once per game, warns when a lobby player with loaded stats is at or above it |
| `game_recap.lua` | *Game Recap* module: after each `You earned N Bed Wars XP` line prints one recap line (XP, session record, FKDR) and keeps an all-time XP / game count in its own `data` |

## Configuration

Settings are saved to `config/CoralIntel/` inside your Minecraft folder. Use the ClickGUI or `.c s <name>` / `.c l <name>` to save and load named configs.

## Disclaimer

CoralIntel only reads public stats and information the game already sends your client. Make sure any use complies with the rules of the server you play on. As all mods are, CoralIntel is strictly use at your own risk, we do not take any responsibility for **any** bans whatsoever.

## Credits

@spiritualizes on discord for any ideas, issues, or queries.
- [Zoobooo/tabstats](https://github.com/Zoobooo/tabstats) – the Seraph-style tab list (label band, columns, respawn / DC status) that `TabOverlay` is ported from.
