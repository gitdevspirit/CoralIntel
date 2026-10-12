-- FKDR Alert
-- Warns once per game when a player in the lobby has an FKDR at or above a threshold.
-- Stats load a moment after a player appears, so this checks once a second
-- and only looks at players whose stats are loaded.

local m = module.register{
  name = "FKDR Alert",
  description = "Alerts when a lobby player's FKDR is at or above a threshold",
  settings = {
    { type = "slider", name = "Min FKDR", default = 5, min = 1, max = 30, step = 0.5 },
    { type = "toggle", name = "Sound",    default = true },
  },
}

local alerted = {}
local ticks = 0

m.on("world_load", function()
  alerted = {}
end)

m.on("tick", function()
  ticks = ticks + 1
  if ticks % 20 ~= 0 then return end

  local me = lobby.me()
  local min = m.get("Min FKDR")

  for _, name in ipairs(lobby.players()) do
    if name ~= me and not alerted[name] then
      local p = lobby.player(name)
      if p and p.loaded and not p.nicked and p.fkdr and p.fkdr >= min then
        alerted[name] = true
        chat.print(string.format("&c[FKDR] &f%s &7has &c%.2f &7FKDR (%d star)", name, p.fkdr, p.star or 0))
        if m.get("Sound") then sound.play("random.orb") end
      end
    end
  end
end)
