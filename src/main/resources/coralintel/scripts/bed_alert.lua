-- Bed Alert
-- Loud alert when YOUR bed is destroyed, and an optional quiet notice for other beds.
-- Matches Hypixel's "BED DESTRUCTION > <Team> Bed was destroyed by <player>!" line.

local m = module.register{
  name = "Bed Alert",
  description = "Alerts when your bed (or any bed) is destroyed",
  settings = {
    { type = "toggle", name = "Sound",       default = true },
    { type = "toggle", name = "Other beds",  default = false },
    { type = "text",   name = "Message",     default = "&c&lYOUR BED WAS DESTROYED &7by &f" },
  },
}

m.on("chat", function(text)
  if not text:find("BED DESTRUCTION >", 1, true) then return end

  local breaker = text:match("destroyed by ([%w_]+)")
  if text:find("Your Bed", 1, true) then
    chat.print(m.get("Message") .. (breaker or "?"))
    if m.get("Sound") then sound.play("random.anvil_land") end
  elseif m.get("Other beds") then
    local team = text:match("BED DESTRUCTION > (%a+) Bed")
    chat.print(string.format("&e[Bed] &f%s &7bed broken by &f%s", team or "A", breaker or "?"))
    if m.get("Sound") then sound.play("random.orb") end
  end
end)
