-- Lobby Summary
-- Once per game, after most stats have loaded, prints a short threat summary:
-- average FKDR, how many players are nicked, and the top FKDRs in the lobby.

local m = module.register{
  name = "Lobby Summary",
  description = "One-shot summary of the lobby's FKDRs and nicks",
  settings = {
    { type = "slider", name = "Top players", default = 3, min = 1, max = 5, step = 1 },
    { type = "slider", name = "Max wait (s)", default = 25, min = 10, max = 60, step = 5 },
    { type = "toggle", name = "Sound",        default = false },
  },
}

local ticks = 0
local done = false

m.on("world_load", function()
  ticks = 0
  done = false
end)

m.on("tick", function()
  if done then return end
  ticks = ticks + 1
  if ticks % 20 ~= 0 then return end

  local me = lobby.me()
  local total, loaded, nicked, sum = 0, 0, 0, 0
  local ready = {}

  for _, name in ipairs(lobby.players()) do
    if name ~= me then
      local p = lobby.player(name)
      if p then
        total = total + 1
        if p.nicked then
          nicked = nicked + 1
          loaded = loaded + 1
        elseif p.loaded then
          loaded = loaded + 1
          sum = sum + (p.fkdr or 0)
          ready[#ready + 1] = p
        end
      end
    end
  end

  if total < 2 then return end
  local waited = ticks / 20 >= m.get("Max wait (s)")
  if loaded < total and not waited then return end
  done = true

  table.sort(ready, function(a, b) return (a.fkdr or 0) > (b.fkdr or 0) end)

  local avg = #ready > 0 and sum / #ready or 0
  chat.print(string.format("&b[Lobby] &7%d players &8| &7avg FKDR &f%.2f &8| &7nicked &f%d",
    total, avg, nicked))
  for i = 1, math.min(m.get("Top players"), #ready) do
    local p = ready[i]
    chat.print(string.format("&7  %d. &f%s &7- &c%.2f &7FKDR (%d star)", i, p.name, p.fkdr or 0, p.star or 0))
  end
  if m.get("Sound") then sound.play("random.orb") end
end)
