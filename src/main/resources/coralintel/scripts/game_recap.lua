-- Game Recap
-- Prints one recap line per game, triggered by the "You earned N Bed Wars XP" message,
-- and keeps an all-time XP / game count in this script's own saved data.

local m = module.register{
  name = "Game Recap",
  description = "One recap line after each game",
  settings = {
    { type = "toggle", name = "Show all-time total", default = true },
  },
}

m.on("chat", function(text)
  local xp = text:match("^You earned ([%d,]+) Bed Wars XP")
  if not xp then return end
  xp = tonumber((xp:gsub(",", "")))
  if not xp then return end

  local games = data.get("games", 0) + 1
  local total = data.get("total_xp", 0) + xp
  data.set("games", games)
  data.set("total_xp", total)

  local s = session.get()
  local line = string.format("&b[Recap] &7+&f%d &7XP &8| &7session &f%dW &7- &f%dL &8| &7FKDR &f%.2f",
    xp, s.wins, s.losses, s.fkdr)
  if m.get("Show all-time total") then
    line = line .. string.format(" &8| &7recorded: &f%d XP &7in &f%d games", total, games)
  end
  chat.print(line)
end)
