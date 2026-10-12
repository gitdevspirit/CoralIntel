-- Blacklist Alert
-- Warns once per game when a player on your blacklist is in the lobby, and
-- (optionally) when a player has Coral tags. Checks twice a second.

local m = module.register{
  name = "Blacklist Alert",
  description = "Alerts when blacklisted or tagged players are in your lobby",
  settings = {
    { type = "toggle", name = "Tagged players", default = true },
    { type = "toggle", name = "Sound",          default = true },
  },
}

local seen = {}
local ticks = 0

m.on("world_load", function()
  seen = {}
end)

m.on("tick", function()
  ticks = ticks + 1
  if ticks % 10 ~= 0 then return end

  local me = lobby.me()
  for _, name in ipairs(lobby.players()) do
    if name ~= me and not seen[name] then
      local p = lobby.player(name)
      if p then
        if p.blacklisted then
          seen[name] = true
          chat.print("&4[Blacklist] &c" .. name .. " &7is on your blacklist and in this lobby!")
          if m.get("Sound") then sound.play("note.pling") end
        elseif m.get("Tagged players") and p.tags and #p.tags > 0 and not p.safelisted then
          seen[name] = true
          chat.print("&6[Tags] &f" .. name .. " &7has tags: &e" .. table.concat(p.tags, ", "))
          if m.get("Sound") then sound.play("random.orb") end
        end
      end
    end
  end
end)
