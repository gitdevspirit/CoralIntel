package coralintel.ui.intel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IntelDebugGui extends GuiScreen {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final String title;
    private final String content;
    private final List<String> lines = new ArrayList<>();
    private int scrollOff = 0;

    public IntelDebugGui(String title, String content) {
        this.title   = title;
        this.content = content;
        // Pre-wrap lines
        for (String raw : content.split("\n")) {
            lines.addAll(wrapLine(raw));
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int pad = 20, fontH = mc.fontRendererObj.FONT_HEIGHT + 2;

        // Background
        drawRect(0, 0, sw, sh, 0xEE05050A);

        // Header
        drawRect(0, 0, sw, 28, 0xFF0A0A14);
        drawRect(0, 27, sw, 1, 0x33E991B8);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f,1f,1f,1f);
        mc.fontRendererObj.drawString(title, pad, 10, 0xFFE991B8, false);
        String hint = "ESC close   SCROLL navigate";
        mc.fontRendererObj.drawString(hint, sw - mc.fontRendererObj.getStringWidth(hint) - pad, 10, 0xFF444455, false);

        // Content area
        int contentY = 34, contentH = sh - contentY - 10;
        int lineW    = sw - pad * 2;
        int total    = lines.size() * fontH;
        int maxScroll = Math.max(0, total - contentH);
        scrollOff = Math.max(0, Math.min(scrollOff, maxScroll));

        // Scissor
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, (sh - contentY - contentH) * scale, sw * scale, contentH * scale);

        for (int i = 0; i < lines.size(); i++) {
            int y = contentY + i * fontH - scrollOff;
            if (y + fontH < contentY || y > contentY + contentH) continue;
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f,1f,1f,1f);
            mc.fontRendererObj.drawString(lines.get(i), pad, y, 0xFFCCCCCC, false);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scrollbar
        if (maxScroll > 0) {
            int sbH = Math.max(20, contentH * contentH / total);
            int sbY = contentY + (int)((float) scrollOff / maxScroll * (contentH - sbH));
            drawRect(sw - 5, contentY, 3, contentH, 0x22FFFFFF);
            drawRect(sw - 5, sbY, 3, sbH, 0x66E991B8);
        }

        super.drawScreen(mx, my, pt);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) scrollOff -= dw > 0 ? 20 : -20;
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == 1) { mc.displayGuiScreen(null); return; }
        super.keyTyped(c, key);
    }

    private List<String> wrapLine(String line) {
        List<String> out = new ArrayList<>();
        ScaledResolution sr = new ScaledResolution(mc);
        int maxW = sr.getScaledWidth() - 40;
        while (mc.fontRendererObj.getStringWidth(line) > maxW && line.length() > 1) {
            int cut = line.length() - 1;
            while (cut > 0 && mc.fontRendererObj.getStringWidth(line.substring(0, cut)) > maxW) cut--;
            out.add(line.substring(0, cut));
            line = "  " + line.substring(cut);
        }
        out.add(line);
        return out;
    }
}
