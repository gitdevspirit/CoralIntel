package coralintel.ui.clickgui;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class RoundedUtils {

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        float a = (color >> 24 & 0xFF) / 255f;

        // Clamp so radius can never exceed half the smaller dimension —
        // otherwise the old quad-based approach could invert its center
        // quad's coordinates and silently drop it depending on GL culling
        // state, which is what made small HUD rows render dead square.
        float maxRadius = Math.min(width, height) / 2f;
        float rad = Math.max(0f, Math.min(radius, maxRadius));

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        // The 3D world pass that runs right before this 2D overlay pass
        // typically leaves GL_CULL_FACE enabled. This fan's winding order
        // isn't guaranteed to match whatever front-face state was left
        // active, so without explicitly disabling culling here the whole
        // fill can get silently backface-culled — invisible, while the
        // outline (drawn as GL_LINE_STRIP, which culling doesn't touch)
        // still renders fine. That's exactly what "border shows but
        // background doesn't" looks like.
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ZERO
        );
        GL11.glColor4f(r, g, b, a);

        if (rad < 0.5f) {
            // No meaningful rounding possible — plain rect, no seams to worry about.
            drawQuad(x, y, x + width, y + height);
        } else {
            // Single continuous fan around the whole perimeter — avoids any
            // seam between separately-drawn quads/corner arcs.
            float cx = x + width / 2f;
            float cy = y + height / 2f;

            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(cx, cy);

            addArc(x + width - rad, y + rad,          rad, 270, 360);
            addArc(x + width - rad, y + height - rad, rad,   0,  90);
            addArc(x + rad,         y + height - rad, rad,  90, 180);
            addArc(x + rad,         y + rad,           rad, 180, 270);

            // GL_TRIANGLE_FAN never auto-closes back to the first perimeter
            // vertex — without this, the last triangle (hub -> last point ->
            // first point) is simply never drawn. For a tall shape that's a
            // barely-visible sliver, but for a wide/short one (like these
            // panel headers) the first and last perimeter points both sit
            // near the top edge at opposite horizontal ends, so the "missing
            // triangle" is actually a huge wedge spanning nearly the whole
            // width — exactly the V-shaped gap in the screenshot.
            GL11.glVertex2f(x + width - rad, y);

            GL11.glEnd();
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GL11.glColor4f(1f, 1f, 1f, 1f); // reset color
        GlStateManager.popMatrix();
    }

    private static void addArc(float cx, float cy, float radius, int startAngle, int endAngle) {
        for (int i = startAngle; i <= endAngle; i += 5) {
            double angle = Math.toRadians(i);
            GL11.glVertex2f(cx + (float) (Math.cos(angle) * radius),
                    cy + (float) (Math.sin(angle) * radius));
        }
    }

    public static void drawRoundedOutline(float x, float y, float width, float height, float radius, float thickness, int color) {
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8  & 0xFF) / 255f;
        float b = (color       & 0xFF) / 255f;
        float a = (color >> 24 & 0xFF) / 255f;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ZERO
        );
        GL11.glColor4f(r, g, b, a);
        GL11.glLineWidth(thickness);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        GL11.glBegin(GL11.GL_LINE_STRIP);
        // top-left corner
        for (int i = 180; i <= 270; i += 5) {
            double angle = Math.toRadians(i);
            GL11.glVertex2f(x + radius + (float)(Math.cos(angle) * radius),
                            y + radius + (float)(Math.sin(angle) * radius));
        }
        // top-right corner
        for (int i = 270; i <= 360; i += 5) {
            double angle = Math.toRadians(i);
            GL11.glVertex2f(x + width - radius + (float)(Math.cos(angle) * radius),
                            y + radius + (float)(Math.sin(angle) * radius));
        }
        // bottom-right corner
        for (int i = 0; i <= 90; i += 5) {
            double angle = Math.toRadians(i);
            GL11.glVertex2f(x + width - radius + (float)(Math.cos(angle) * radius),
                            y + height - radius + (float)(Math.sin(angle) * radius));
        }
        // bottom-left corner
        for (int i = 90; i <= 180; i += 5) {
            double angle = Math.toRadians(i);
            GL11.glVertex2f(x + radius + (float)(Math.cos(angle) * radius),
                            y + height - radius + (float)(Math.sin(angle) * radius));
        }
        // close back to top-left
        double angle = Math.toRadians(180);
        GL11.glVertex2f(x + radius + (float)(Math.cos(angle) * radius),
                        y + radius + (float)(Math.sin(angle) * radius));
        GL11.glEnd();

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private static void drawQuad(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x1, y2);
        GL11.glEnd();
    }
}
