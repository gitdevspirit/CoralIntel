package coralintel.util;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Trimmed from the original client's RenderUtil — that file also had a large
 * collection of ESP-box / bounding-box / corner-ESP / frustum helpers used by
 * the combat modules. None of that is needed here; BedwarsTag only ever calls
 * lerpDouble, enableRenderState/disableRenderState, drawRect, and setColor.
 */
public class RenderUtil {

    public static void drawRect(float x1, float y1, float x2, float y2, int color) {
        if (color == 0) {
            return;
        }
        RenderUtil.setColor(color);
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    /**
     * Sets up alpha-blended 2D-style rendering for a quad drawn in 3D space
     * (e.g. the tag's background panel). Depth testing is deliberately left
     * alone here (unlike the original client's version) so the panel is
     * occluded by walls just like the text drawn next to it.
     */
    public static void enableRenderState() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
    }

    public static void disableRenderState() {
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void setColor(int argb) {
        float a = (float) (argb >> 24 & 0xFF) / 255.0f;
        float r = (float) (argb >> 16 & 0xFF) / 255.0f;
        float g = (float) (argb >> 8 & 0xFF) / 255.0f;
        float b = (float) (argb & 0xFF) / 255.0f;
        GlStateManager.color(r, g, b, a);
    }

    public static double lerpDouble(double current, double previous, double t) {
        return previous + (current - previous) * t;
    }
}
