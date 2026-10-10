package coralintel.render;

import coralintel.CoralIntel;
import coralintel.module.modules.LobbyIntel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Bed Wars shop helper: with "Shop: Auto Middle-Click" on, a left click on an item in one of the
 * shop's category pages buys it as usual and then sends the middle click for you, so the item is
 * also added to Quick Buy without a second manual click.
 *
 * Only the item grid of the category pages is touched (never the Quick Buy page itself, never the
 * category tabs), and only when the screen title is one of the shop pages.
 */
public class ShopQuickBuy {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Category pages of the Bed Wars item shop (Quick Buy is deliberately not in here). */
    private static final Set<String> PAGES = new HashSet<>(Arrays.asList(
            "blocks", "melee", "armor", "tools", "ranged", "potions", "utility"));

    /** First slot of the item grid: the top rows hold the category tabs and a divider. */
    private static final int FIRST_ITEM_SLOT = 18;

    private static Field guiLeft;
    private static Field guiTop;

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChest)) return;
        if (!Mouse.getEventButtonState() || Mouse.getEventButton() != 0) return;
        if (mc.thePlayer == null || mc.playerController == null) return;

        LobbyIntel li = CoralIntel.moduleManager == null ? null
                : (LobbyIntel) CoralIntel.moduleManager.getModule(LobbyIntel.class);
        if (li == null || !li.shopAutoMiddle.getValue()) return;

        try {
            GuiChest gui = (GuiChest) event.gui;
            Container container = gui.inventorySlots;
            if (!(container instanceof ContainerChest)) return;

            String title = ((ContainerChest) container).getLowerChestInventory()
                    .getDisplayName().getUnformattedText();
            if (title == null || !PAGES.contains(title.trim().toLowerCase(java.util.Locale.ROOT))) return;

            Slot slot = slotUnderMouse(gui);
            if (slot == null || slot.slotNumber < FIRST_ITEM_SLOT
                    || slot.slotNumber >= ((ContainerChest) container).getLowerChestInventory().getSizeInventory()
                    || !slot.getHasStack()) {
                return;
            }

            final int windowId = container.windowId;
            final int slotNumber = slot.slotNumber;

            // The left click goes through untouched (it buys); the middle click follows next tick.
            mc.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (mc.thePlayer == null || mc.thePlayer.openContainer == null
                            || mc.thePlayer.openContainer.windowId != windowId) {
                        return;
                    }
                    mc.playerController.windowClick(windowId, slotNumber, 2, 3, mc.thePlayer);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static Slot slotUnderMouse(GuiContainer gui) throws Exception {
        if (guiLeft == null) {
            guiLeft = ReflectionHelper.findField(GuiContainer.class, "guiLeft", "field_147003_i");
            guiTop = ReflectionHelper.findField(GuiContainer.class, "guiTop", "field_147009_r");
            guiLeft.setAccessible(true);
            guiTop.setAccessible(true);
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int mouseX = Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
        int mouseY = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;
        int left = guiLeft.getInt(gui);
        int top = guiTop.getInt(gui);

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            int x = mouseX - left;
            int y = mouseY - top;
            if (x >= slot.xDisplayPosition - 1 && x < slot.xDisplayPosition + 17
                    && y >= slot.yDisplayPosition - 1 && y < slot.yDisplayPosition + 17) {
                return slot;
            }
        }
        return null;
    }
}
