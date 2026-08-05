package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.clickgui.ClickGui;
import net.minecraft.client.Minecraft;

/** .clickgui / .cgui — opens the ClickGUI directly. */
public class ClickGuiCommand extends Command {

    public ClickGuiCommand() {
        super("clickgui", "cgui");
        setDescription("Opens the CoralIntel ClickGUI.");
    }

    @Override
    public void execute(String[] args) {
        Minecraft.getMinecraft().addScheduledTask(() ->
                Minecraft.getMinecraft().displayGuiScreen(new ClickGui())
        );
    }
}
