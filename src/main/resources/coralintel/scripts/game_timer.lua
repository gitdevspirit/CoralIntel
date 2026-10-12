-- Game Timer
-- Shows how long the current Bedwars game has been running (mm:ss) on the HUD.
-- Starts on the "Protect your bed and destroy the enemy beds" line, stops on
-- VICTORY!/GAME OVER!, and resets whenever a new world loads.

local m = module.register{
  name = "Game Timer",
  description = "mm:ss game clock on the HUD",
  settings = {
    { type = "slider", name = "HUD X", default = 5, min = 0, max = 400, step = 5 },
    { type = "slider", name = "HUD Y", default = 45, min = 0, max = 300, step = 5 },
  },
}

local running = false
local ticks = 0

local function show()
  local secs = math.floor(ticks / 20)
  hud.text("gametimer", string.format("&bGame: &f%02d:%02d", math.floor(secs / 60), secs % 60),
    m.get("HUD X"), m.get("HUD Y"))
end

local function stop()
  running = false
end

m.onDisable(function()
  running = false
  hud.remove("gametimer")
end)
m.onChange(function() if ticks > 0 then show() end end)

m.on("world_load", function()
  running = false
  ticks = 0
  hud.remove("gametimer")
end)

m.on("chat", function(text)
  if text:find("Protect your bed and destroy the enemy beds", 1, true) then
    ticks = 0
    running = true
    show()
  end
end)

m.on("title", function(kind, text)
  if kind == "title" and (text:find("VICTORY") or text:find("GAME OVER")) then stop() end
end)

m.on("tick", function()
  if not running then return end
  ticks = ticks + 1
  if ticks % 20 == 0 then show() end
end)
