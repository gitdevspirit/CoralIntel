package coralintel.module.modules;

import coralintel.event.EventTarget;
import coralintel.events.Render3DEvent;
import coralintel.mixin.IAccessorRenderManager;
import coralintel.module.BooleanSetting;
import coralintel.module.DropdownSetting;
import coralintel.module.Module;
import coralintel.module.SliderSetting;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.util.ColorUtil;
import coralintel.util.PrestigeUtil;
import coralintel.util.RenderUtil;
import coralintel.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Renders a BedWars star tag above players' heads using stats already
 * cached in IntelManager — no extra API calls needed.
 *
 * Unlike the original client's version, this tag respects normal depth
 * testing: it's a floating 3D-world label, not an ESP, so it's occluded
 * by walls and terrain like any other in-world object. All the original
 * info (health, name color, star, FKDR, threat) is still shown — only
 * the "see through walls" behavior has been removed.
 */
public class BedwarsTag extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormatter = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));

    public final BooleanSetting  showStar   = register(new BooleanSetting("Show Star",    true));
    public final DropdownSetting healthMode = register(new DropdownSetting("Health", 1, "NONE", "HP", "HEARTS", "TAB"));
    public final BooleanSetting  showFkdr   = register(new BooleanSetting("Show FKDR",    false));
    public final BooleanSetting  showThreat = register(new BooleanSetting("Show Threat",  false));
    public final BooleanSetting  selfTag    = register(new BooleanSetting("Show Self",     false));
    public final BooleanSetting  autoScale  = register(new BooleanSetting("Auto Scale",    true));
    public final SliderSetting   scale      = register(new SliderSetting("Scale",          1.0, 0.5, 2.0, 0.05));
    public final BooleanSetting  background = register(new BooleanSetting("Background",    true));
    public final BooleanSetting  onlyIntel  = register(new BooleanSetting("Intel Only",    false));
    public final BooleanSetting  throughWalls = register(new BooleanSetting("Through Walls", false));

    public BedwarsTag() { super("BedWarsTag", false); }

    /** Last stats seen per player, so a tag doesn't blank out while the roster is being rebuilt. */
    private final java.util.Map<String, IntelPlayer> lastKnown = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Stats for this player: the live roster entry (including manually added / chat-tracked players),
     * or, while the roster is being rebuilt (new world, /who, game start), the last entry seen.
     */
    private IntelPlayer intelFor(EntityPlayer player) {
        String key = player.getName().toLowerCase(Locale.ROOT);
        IntelPlayer live = IntelManager.getInstance().getPlayer(player.getName());
        if (live != null) {
            lastKnown.put(key, live);
            return live;
        }
        return lastKnown.get(key);
    }

    /**
     * True when there is something real to show. A player being RE-fetched is flagged "loading" again
     * but still holds the stats from before, so that must not turn the tag gray ("[?]") or hide it.
     */
    private static boolean hasData(IntelPlayer intel) {
        return intel != null && (!intel.loading || intel.star > 0 || intel.statsComplete);
    }

    /**
     * True if this module will actually draw its own custom tag above this
     * player this frame — used by MixinRendererLivingEntity to decide
     * whether to suppress the vanilla nametag. This has to mirror
     * onRender3D()'s skip conditions exactly (self/dead/distance/intel-only),
     * not just "is the module enabled" — otherwise a player skipped by one
     * of those filters would end up with neither tag at all: the vanilla one
     * suppressed unconditionally, and the custom one never drawn.
     */
    public boolean willRenderTagFor(EntityPlayer player) {
        if (!isEnabled()) return false;
        if (!selfTag.getValue() && player == mc.thePlayer) return false;
        if (player == mc.thePlayer && StreamerMode.disablesOwnNametag()) return false;
        if (player.deathTime > 0) return false;
        if (mc.getRenderViewEntity() == null
                || mc.getRenderViewEntity().getDistanceToEntity(player) > 64f) return false;

        if (onlyIntel.getValue() && !hasData(intelFor(player))) return false;

        return true;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.theWorld == null || mc.thePlayer == null) return;

        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();

        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null) return;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) entity;

            if (!selfTag.getValue() && player == mc.thePlayer) continue;
            if (player == mc.thePlayer && StreamerMode.disablesOwnNametag()) continue;
            if (player.deathTime > 0) continue;
            if (viewEntity.getDistanceToEntity(player) > 64f) continue;

            // Look up intel data
            IntelPlayer intel = intelFor(player);

            // If intel-only mode and no data yet, skip
            if (onlyIntel.getValue() && !hasData(intel)) continue;

            // ── 3D billboard setup ─────────────────────────────────────────
            double px = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks()) - rm.coralintel$getRenderPosX();
            double py = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks()) - rm.coralintel$getRenderPosY();
            double pz = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks()) - rm.coralintel$getRenderPosZ();
            double dist = viewEntity.getDistanceToEntity(player);

            // Position above head — offset above vanilla nametag
            double nametagY = py + player.getEyeHeight() + (player.isSneaking() ? 0.225 : 0.4);

            GlStateManager.pushMatrix();
            try {
            GlStateManager.translate(px, nametagY, pz);

            // Billboard — face camera
            GlStateManager.rotate(mc.getRenderManager().playerViewY * -1f, 0f, 1f, 0f);
            float view = mc.gameSettings.thirdPersonView == 2 ? -1f : 1f;
            GlStateManager.rotate(mc.getRenderManager().playerViewX, view, 0f, 0f);

            // Scale — auto-scale with distance
            double tagScale = Math.pow(Math.min(Math.max(
                    autoScale.getValue() ? dist : 6.0, 6.0), 128.0), 0.75)
                    * 0.0065 * scale.getValue();
            GlStateManager.scale(-tagScale, -tagScale, 1.0);

            // Streamer mode (yourself only): optional custom name, no stats, no tags.
            boolean ownStatsHidden = StreamerMode.hidesStatsFor(player.getName());
            boolean ownTagsHidden  = StreamerMode.hidesTagsFor(player.getName());
            String shownName = StreamerMode.aliasFor(player.getName());

            String[] parts = buildParts(intel, shownName);
            String starPart   = ownStatsHidden ? "" : parts[0]; // e.g. "☆8"
            String urchinPart = ownTagsHidden ? "" : parts[2];  // e.g. "CC" or ""
            String healthPart = buildHealthText(player); // e.g. " 20" or " 10.0" or " 20"(tab)
            String fkdrPart   = ownStatsHidden ? "" : buildFkdrText(intel);

            // In an active Bedwars match (team scoreboard assigned by the
            // server) — color the name by team instead of showing rank.
            // In a lobby (no team yet) — show the Hypixel rank prefix instead.
            String namePart;
            int nameColor;

            if (isMatchTeam(player)) {
                namePart = shownName;
                nameColor = TeamUtil.getTeamColor(player, 1f).getRGB() | 0xFF000000;
            } else {
                // The rank prefix comes straight from the tab display name and ends in a
                // colour code (non-ranked players are just "\u00A77"). Left as-is that colour
                // bleeds into the name and paints it flat gray, so reset formatting after
                // the rank and let the name use the normal white.
                String plainRank = (intel != null && intel.rankPrefix != null)
                        ? net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(intel.rankPrefix) : null;
                String rank = (plainRank != null && !plainRank.trim().isEmpty())
                        ? intel.rankPrefix + "\u00A7r " : "";
                namePart = rank + shownName;
                nameColor = 0xFFFFFFFF;
            }

            int starColor   = getStarColor(intel);
            int urchinColor = getUrchinColor(intel);
            int healthColor = getHealthColor(player);
            int fkdrColor   = getFkdrColor(intel);

            int gap = 3;
            int starW   = mc.fontRendererObj.getStringWidth(starPart);
            int nameW   = mc.fontRendererObj.getStringWidth(namePart);
            int healthW = healthPart.isEmpty() ? 0 : mc.fontRendererObj.getStringWidth(healthPart);
            int fkdrW   = fkdrPart.isEmpty() ? 0 : mc.fontRendererObj.getStringWidth(fkdrPart);
            int urchinW = urchinPart.isEmpty() ? 0 : mc.fontRendererObj.getStringWidth(urchinPart);
            int totalW  = starW + gap + nameW
                    + (healthW > 0 ? gap + healthW : 0)
                    + (fkdrW > 0 ? gap + fkdrW : 0)
                    + (urchinW > 0 ? gap + urchinW : 0);

            float ty = -(float) mc.fontRendererObj.FONT_HEIGHT;
            float tx = -totalW / 2f;

            // Depth WRITES off, depth TEST left alone (still on, from the
            // earlier walls-occlusion fix). This is exactly what vanilla's
            // own nametag rendering does — depth test enabled so terrain
            // still hides the tag, but not writing to the depth buffer
            // avoids z-fighting against the player model sitting right next
            // to it, which was causing the tag to flicker in and out.
            // Set the full state we rely on, exactly like vanilla's nametag: this runs at the very end of
            // world rendering, and whatever the renderers before us left behind (lighting on, a different
            // blend/alpha function, depth func) made tags come out gray or not appear at all.
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(516, 0.1f);
            GlStateManager.depthFunc(515);
            if (throughWalls.getValue()) {
                GlStateManager.disableDepth();
            } else {
                GlStateManager.enableDepth();
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.depthMask(false);

            // Background — no longer disables depth, so it's occluded by walls too.
            if (background.getValue()) {
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(tx - 1, ty - 1, tx + totalW + 1, 0, 0x66000000);
                RenderUtil.disableRenderState();
            }

            // Text — depth testing left enabled (unlike the original), so the
            // tag is hidden behind walls/terrain like any other world object.
            mc.fontRendererObj.drawString(starPart, tx, ty, starColor, true);
            float cursor = tx + starW + gap;
            mc.fontRendererObj.drawString(namePart, cursor, ty, nameColor, true);
            cursor += nameW;
            if (!healthPart.isEmpty()) {
                cursor += gap;
                mc.fontRendererObj.drawString(healthPart, cursor, ty, healthColor, true);
                cursor += healthW;
            }
            if (!fkdrPart.isEmpty()) {
                cursor += gap;
                mc.fontRendererObj.drawString(fkdrPart, cursor, ty, fkdrColor, true);
                cursor += fkdrW;
            }
            if (!urchinPart.isEmpty()) {
                cursor += gap;
                mc.fontRendererObj.drawString(urchinPart, cursor, ty, urchinColor, true);
            }

            } catch (RuntimeException e) {
                IntelManager.dbg("[BedWarsTag] tag for " + player.getName() + " failed: " + e);
            } finally {
                GlStateManager.depthMask(true);
                GlStateManager.enableDepth();
                GlStateManager.popMatrix();
            }
        }
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static final java.util.regex.Pattern MATCH_TEAM_PREFIX =
            java.util.regex.Pattern.compile("^\\[?\\p{Lu}\\]?\\s*$");

    /**
     * True only for a real Bed Wars match team (its scoreboard prefix is the team letter, e.g. "R ").
     * Hypixel also puts players in scoreboard teams in lobbies (for tab sorting) with a plain gray or
     * white rank colour, which used to be mistaken for a match team and turned the whole name gray.
     */
    private static boolean isMatchTeam(EntityPlayer player) {
        if (!(player.getTeam() instanceof net.minecraft.scoreboard.ScorePlayerTeam)) return false;
        String prefix = ((net.minecraft.scoreboard.ScorePlayerTeam) player.getTeam()).getColorPrefix();
        String plain = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(prefix);
        return plain != null && MATCH_TEAM_PREFIX.matcher(plain).matches();
    }

    // Builds the health suffix text based on the Health dropdown (NONE/HP/HEARTS/TAB)
    private String buildHealthText(EntityPlayer player) {
        switch (healthMode.getIndex()) {
            case 1: { // HP
                float health     = player.getHealth();
                float absorption = player.getAbsorptionAmount();
                if (absorption > 0.0F) {
                    return String.format("%d+%d", (int) health, (int) absorption);
                }
                return String.format("%d", (int) health);
            }
            case 2: { // HEARTS
                float health     = player.getHealth();
                float absorption = player.getAbsorptionAmount();
                if (absorption > 0.0F) {
                    return String.format("%s+%s",
                            healthFormatter.format((double) health / 2.0),
                            healthFormatter.format((double) absorption / 2.0));
                }
                return healthFormatter.format((double) health / 2.0);
            }
            case 3: { // TAB — read from the "below name" scoreboard objective
                Scoreboard sb = mc.theWorld.getScoreboard();
                if (sb != null) {
                    ScoreObjective obj = sb.getObjectiveInDisplaySlot(2);
                    if (obj != null) {
                        Score score = sb.getValueFromObjective(player.getName(), obj);
                        if (score != null) return String.valueOf(score.getScorePoints());
                    }
                }
                return "";
            }
            default: // NONE
                return "";
        }
    }

    private int getHealthColor(EntityPlayer player) {
        if (healthMode.getIndex() == 3) return 0xFFFFD700; // gold, matches TAB mode
        float health     = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        float max        = player.getMaxHealth();
        float percent    = Math.min(Math.max((health + absorption) / max, 0.0F), 1.0F);
        return ColorUtil.getHealthBlend(percent).getRGB();
    }

    // Builds the "FKDR X.X" suffix when the Show FKDR setting is on — was
    // registered as a setting in the original client but never actually
    // wired into rendering, so toggling it never did anything.
    private String buildFkdrText(IntelPlayer intel) {
        if (!showFkdr.getValue() || !hasData(intel)) return "";
        return String.format(java.util.Locale.ROOT, "FKDR %.1f", intel.fkdr);
    }

    private int getFkdrColor(IntelPlayer intel) {
        if (!hasData(intel)) return 0xFFAAAAAA;
        return coralintel.ui.intel.IntelColors.getStatColor(intel.fkdr, 3, 6);
    }

    // Returns [starText, name, tag] — rendered separately. The star text carries
    // its own per-character prestige color codes (PrestigeUtil / Nevada table).
    private String[] buildParts(IntelPlayer intel, String playerName) {
        String star = !hasData(intel)
                ? "\u00A77[?\u272B]"
                : PrestigeUtil.format(intel.star);
        String tag = "";

        if (hasData(intel)) {
            if (intel.isNicked) {
                tag = "NICK";
            } else {
                // Single source of truth: B / BC / CCC (confirmed cheater) /
                // CC (closet cheater) / S / R — same labels as tab, HUD, GUI, .bw.
                tag = intel.getTagBadge();
            }
        }

        return new String[]{ star, playerName, tag };
    }

    private int getStarColor(IntelPlayer intel) {
        return 0xFFFFFFFF; // real colors come from the §-codes in the star text
    }

    private int getUrchinColor(IntelPlayer intel) {
        if (intel == null) return 0xFFFF8844;
        if (intel.isNicked) return 0xFFAA00AA; // dark purple, matches the tab [NICK] tag
        return intel.getTagColor();
    }
}
