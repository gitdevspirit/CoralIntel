-- Win Streak
-- Tracks your winstreak across games (and across restarts, via data.*) from the
-- VICTORY! / GAME OVER! titles and shows it on the HUD.

local m = module.register{
  name = "Win Streak",
  description = "Tracks your winstreak and best streak on the HUD",
  settings = {
    { type = "toggle", name = "Show HUD",   default = true },
    { type = "slider", name = "HUD X",      default = 5,  min = 0, max = 400, step = 5 },
    { type = "slider", name = "HUD Y",      default = 60, min = 0, max = 300, step = 5 },
    { type = "toggle", name = "Announce",   default = true },
  },
}

local function refresh()
  if not m.enabled() or not m.get("Show HUD") then
    hud.remove("winstreak")
    return
  end
  local streak = data.get("streak", 0)
  local best = data.get("best", 0)
  hud.text("winstreak", string.format("&aStreak: &f%d &8| &7Best: &f%d", streak, best),
    m.get("HUD X"), m.get("HUD Y"))
end

m.onEnable(refresh)
m.onDisable(function() hud.remove("winstreak") end)
m.onChange(function() refresh() end)

m.on("title", function(kind, text)
  if kind ~= "title" then return end
  if text:find("VICTORY") then
    local streak = data.get("streak", 0) + 1
    data.set("streak", streak)
    if streak > data.get("best", 0) then data.set("best", streak) end
    if m.get("Announce") then
      chat.print(string.format("&a[Streak] &f%d &7win%s in a row (best &f%d&7)",
        streak, streak == 1 and "" or "s", data.get("best", 0)))
    end
    refresh()
  elseif text:find("GAME OVER") then
    local old = data.get("streak", 0)
    data.set("streak", 0)
    if m.get("Announce") and old > 0 then
      chat.print("&c[Streak] &7Streak ended at &f" .. old)
    end
    refresh()
  end
end)

-- Command to wipe the saved streak.
command.register("resetstreak", function()
  data.set("streak", 0)
  data.set("best", 0)
  refresh()
  chat.print("&7Win streak and best streak reset.")
end, "Reset the Win Streak script's saved numbers")
