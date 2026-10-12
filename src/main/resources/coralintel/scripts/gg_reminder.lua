-- GG Reminder
-- When a game ends (VICTORY! / GAME OVER! title), remind YOU to type gg.
-- Scripts can't send chat to the server (by design), so this is a reminder, not an auto-gg.

local m = module.register{
  name = "GG Reminder",
  description = "Reminds you to say gg when a game ends",
  settings = {
    { type = "text",   name = "Reminder", default = "&aGame over - type &f&lgg&a!" },
    { type = "toggle", name = "Sound",    default = true },
  },
}

m.on("title", function(kind, text)
  if kind ~= "title" then return end
  if text:find("VICTORY") or text:find("GAME OVER") then
    chat.print(m.get("Reminder"))
    if m.get("Sound") then sound.play("note.pling") end
  end
end)
