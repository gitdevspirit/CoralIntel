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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import coralintel.ui.intel.IntelManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

/**
 * Bed Wars shop helper: with "Shop: Auto Middle-Click" on, a left click on an item in one of the
 * shop's category pages buys it as usual and then sends the middle click for you, so the item is
 * also added to Quick Buy without a second manual click.
 *
 * Only items whose own tooltip offers "add to Quick Buy" are touched (never ones that would be
 * removed, never the category tabs). What it did or skipped is in ".idebug log".
 */
public class ShopQuickBuy {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** First slot of the item grid: the top rows hold the category tabs and a divider. */
    private static final int FIRST_ITEM_SLOT = 18;

    private static Field guiLeft;
    private static Field guiTop;

    // A middle click waiting for next tick (so the left click is always sent first).
    private int pendingWindow = -1;
    private int pendingSlot = -1;

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
            ContainerChest chest = (ContainerChest) container;

            Slot slot = slotUnderMouse(gui);
            if (slot == null || slot.slotNumber < FIRST_ITEM_SLOT
                    || slot.slotNumber >= chest.getLowerChestInventory().getSizeInventory()
                    || !slot.getHasStack()) {
                return;
            }

            // Judge the item, not the window title: only items whose own tooltip talks about Quick
            // Buy (and doesn't say "remove", which would take an item OUT of Quick Buy) qualify.
            String lore = loreOf(slot.getStack());
            if (!lore.contains("quick buy") || lore.contains("remove")) {
                IntelManager.dbg("[Shop] skipped '" + title(chest) + "' slot " + slot.slotNumber
                        + " (no 'add to quick buy' line in its lore)");
                return;
            }

            pendingWindow = container.windowId;
            pendingSlot = slot.slotNumber;
            IntelManager.dbg("[Shop] middle click queued for '" + title(chest) + "' slot " + slot.slotNumber);
        } catch (Exception e) {
            IntelManager.dbg("[Shop] auto middle-click failed: " + e);
        }
    }

    /** Runs the queued middle click one tick after the left click that bought the item. */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingWindow < 0) return;
        int windowId = pendingWindow;
        int slotNumber = pendingSlot;
        pendingWindow = -1;
        pendingSlot = -1;

        if (mc.thePlayer == null || mc.playerController == null || mc.thePlayer.openContainer == null
                || mc.thePlayer.openContainer.windowId != windowId) {
            return;
        }
        mc.playerController.windowClick(windowId, slotNumber, 2, 3, mc.thePlayer);
        IntelManager.dbg("[Shop] middle click sent, slot " + slotNumber);
    }

    private static String title(ContainerChest chest) {
        return chest.getLowerChestInventory().getDisplayName().getUnformattedText();
    }

    /** Lower-case, colour-free lore text of a stack. */
    private static String loreOf(ItemStack stack) {
        StringBuilder sb = new StringBuilder();
        if (stack != null && stack.hasTagCompound() && stack.getTagCompound().hasKey("display", 10)) {
            NBTTagCompound display = stack.getTagCompound().getCompoundTag("display");
            if (display.hasKey("Lore", 9)) {
                NBTTagList lore = display.getTagList("Lore", 8);
                for (int i = 0; i < lore.tagCount(); i++) {
                    sb.append(EnumChatFormatting.getTextWithoutFormattingCodes(lore.getStringTagAt(i))).append('\n');
                }
            }
        }
        return sb.toString().toLowerCase(java.util.Locale.ROOT);
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
