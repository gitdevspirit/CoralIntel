package coralintel.module.modules;

import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.LoadWorldEvent;
import coralintel.events.TickEvent;
import coralintel.module.BooleanSetting;
import coralintel.module.Module;
import coralintel.module.SliderSetting;
import coralintel.ui.intel.BlacklistManager;
import coralintel.ui.intel.IntelManager;
import coralintel.util.ChatUtil;
import coralintel.util.SoundUtil;
import coralintel.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;

import java.util.*;

/**
 * Real-time heuristic anticheat — flags players who exhibit suspicious
 * movement/combat patterns (NoSlow, AutoBlock, Sprint, Velocity, Rotation,
 * Scaffold) using the same class of checks server-side anticheats use. This
 * only ever informs you about other players; it doesn't change your own
 * client's behavior in any way.
 *
 * Ported from the original client's AC module — logic is unchanged, but
 * flags now also feed into LobbyIntel's shared notification log (visible in
 * the ClickGUI's "RECENT FLAGS" section alongside Coral and blacklist
 * flags), and there's an optional auto-blacklist tie-in: once a check's
 * violation level crosses a much higher confirmation threshold, the player
 * can be blacklisted automatically so they carry the "B" tag and elevated
 * threat score into future lobbies too.
 */
public class AntiCheat extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Per-player state maps
    private final Map<EntityPlayer, Map<String, Object>> playerData = new HashMap<>();

    // Settings — per check: enabled, VL threshold, cooldown
    public final BooleanSetting ignoreTeam   = register(new BooleanSetting("Ignore Team",    true));
    public final BooleanSetting pingOnFlag   = register(new BooleanSetting("Sound Alert",    true));
    public final BooleanSetting autoBlacklist = register(new BooleanSetting("Auto-Blacklist on Confirm", false));
    public final SliderSetting  confirmMultiplier = register(new SliderSetting("Confirm VL Multiplier", 3.0, 1.5, 6.0, 0.5));

    // NoSlow
    public final BooleanSetting noSlowOn     = register(new BooleanSetting("NoSlow",         true));
    public final SliderSetting  noSlowVL     = register(new SliderSetting("NoSlow VL",       10, 1, 50, 1));
    public final SliderSetting  noSlowCD     = register(new SliderSetting("NoSlow CD (s)",   3,  0, 20, 1));

    // AutoBlock
    public final BooleanSetting autoBlockOn  = register(new BooleanSetting("AutoBlock",      true));
    public final SliderSetting  autoBlockVL  = register(new SliderSetting("AutoBlock VL",    8,  1, 50, 1));
    public final SliderSetting  autoBlockCD  = register(new SliderSetting("AutoBlock CD (s)",3,  0, 20, 1));

    // Sprint
    public final BooleanSetting sprintOn     = register(new BooleanSetting("Sprint",         true));
    public final SliderSetting  sprintVL     = register(new SliderSetting("Sprint VL",       10, 1, 50, 1));
    public final SliderSetting  sprintCD     = register(new SliderSetting("Sprint CD (s)",   3,  0, 20, 1));

    // Velocity
    public final BooleanSetting velocityOn   = register(new BooleanSetting("Velocity",       true));
    public final SliderSetting  velocityVL   = register(new SliderSetting("Velocity VL",     10, 1, 50, 1));
    public final SliderSetting  velocityCD   = register(new SliderSetting("Velocity CD (s)", 3,  0, 20, 1));

    // Rotation
    public final BooleanSetting rotationOn   = register(new BooleanSetting("Rotation",       true));
    public final SliderSetting  rotationVL   = register(new SliderSetting("Rotation VL",     5,  1, 50, 1));
    public final SliderSetting  rotationCD   = register(new SliderSetting("Rotation CD (s)", 3,  0, 20, 1));

    // Scaffold
    public final BooleanSetting scaffoldOn   = register(new BooleanSetting("Scaffold",       true));
    public final SliderSetting  scaffoldVL   = register(new SliderSetting("Scaffold VL",     8,  1, 50, 1));
    public final SliderSetting  scaffoldCD   = register(new SliderSetting("Scaffold CD (s)", 3,  0, 20, 1));

    // Tracks which checks have already hit the confirm threshold for a
    // player this session, so auto-blacklist only fires once per player.
    private final Set<String> confirmedThisSession = new HashSet<>();

    public AntiCheat() { super("AntiCheat", false); }

    @Override public void onDisabled() { playerData.clear(); }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent e) { playerData.clear(); confirmedThisSession.clear(); }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        for (Object obj : new ArrayList<>(mc.theWorld.playerEntities)) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.thePlayer) continue;
            if (p.isDead) continue;
            if (ignoreTeam.getValue() && TeamUtil.isSameTeam(p)) continue;

            Map<String, Object> data = playerData.computeIfAbsent(p, k -> new HashMap<>());
            updateData(p, data);

            if ((int) data.getOrDefault("ticksExisted", 0) < 20) continue;

            if (noSlowOn.getValue())    checkNoSlow(p, data);
            if (autoBlockOn.getValue()) checkAutoBlock(p, data);
            if (sprintOn.getValue())    checkSprint(p, data);
            if (velocityOn.getValue())  checkVelocity(p, data);
            if (rotationOn.getValue())  checkRotation(p, data);
            if (scaffoldOn.getValue())  checkScaffold(p, data);
        }

        // Clean up players who left
        playerData.keySet().removeIf(p -> !mc.theWorld.playerEntities.contains(p));
    }

    // ── Data collection ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void updateData(EntityPlayer p, Map<String, Object> d) {
        int tick = p.ticksExisted;

        // Save last-frame values
        boolean lastSprinting  = (boolean) d.getOrDefault("sprinting",  false);
        boolean lastUsing      = (boolean) d.getOrDefault("using",      false);
        boolean lastOnGround   = (boolean) d.getOrDefault("onGround",   false);
        double  lastDeltaY     = (double)  d.getOrDefault("lastDeltaY", 0.0);
        ItemStack lastHeld     = (ItemStack) d.getOrDefault("heldItem",  null);
        int lastSwing          = (int)     d.getOrDefault("swingProgress", 0);

        // Current values
        boolean isSprinting    = p.isSprinting();
        boolean isUsing        = p.isUsingItem();
        boolean onGround       = p.onGround;
        float   yaw            = p.rotationYaw;
        float   pitch          = p.rotationPitch;
        float   prevYaw        = p.prevRotationYaw;
        int     hurtTime       = p.hurtTime;
        int     maxHurtTime    = p.maxHurtTime;
        int     swingProgress  = p.swingProgressInt;
        boolean isBurning      = p.isBurning();
        ItemStack heldItem     = p.getCurrentEquippedItem();

        double posX = p.posX, posY = p.posY, posZ = p.posZ;
        double lastPosX = (double) d.getOrDefault("posX", posX);
        double lastPosY = (double) d.getOrDefault("posY", posY);
        double lastPosZ = (double) d.getOrDefault("posZ", posZ);
        double deltaX   = posX - lastPosX;
        double deltaY   = posY - lastPosY;
        double deltaZ   = posZ - lastPosZ;

        float moveYaw   = getMoveYaw(deltaX, deltaZ, yaw);

        // Store last-tick deltas before overwriting position
        d.put("lastDeltaY",     deltaY);
        d.put("lastDeltaX",     deltaX);
        d.put("lastDeltaZ",     deltaZ);
        d.put("lastPosX",       lastPosX);
        d.put("lastPosY",       lastPosY);
        d.put("lastPosZ",       lastPosZ);
        d.put("posX",           posX);
        d.put("posY",           posY);
        d.put("posZ",           posZ);

        // Store current state
        d.put("ticksExisted",   tick);
        d.put("sprinting",      isSprinting);
        d.put("using",          isUsing);
        d.put("onGround",       onGround);
        d.put("yaw",            yaw);
        d.put("pitch",          pitch);
        d.put("prevYaw",        prevYaw);
        d.put("hurtTime",       hurtTime);
        d.put("maxHurtTime",    maxHurtTime);
        d.put("swingProgress",  swingProgress);
        d.put("burning",        isBurning);
        d.put("heldItem",       heldItem);
        d.put("moveYaw",        moveYaw);
        d.put("lastSprinting",  lastSprinting);
        d.put("lastUsing",      lastUsing);
        d.put("lastOnGround",   lastOnGround);

        // Swing tick
        if (lastSwing == 0 && swingProgress == 1) {
            d.put("lastSwingTick", tick);
            d.put("lastSwingItem", heldItem);
        }
        // Using item start/stop
        if (isUsing && !lastUsing)  { d.put("lastUsingTick",     tick); }
        if (!isUsing && lastUsing)  { d.put("lastStopUsingTick", tick); d.put("lastStopUsingItem", lastHeld); }
        // Sprinting start
        if (isSprinting && !lastSprinting) { d.put("lastSprintingTick", tick); }
        // Item change
        if (itemChanged(heldItem, lastHeld)) { d.put("lastItemChangeTick", tick); }
        // Falling
        if (deltaY < -0.1 && lastDeltaY >= -0.1) { d.put("lastStartFallingTick", tick); }
        if (onGround && !lastOnGround)             { d.put("lastStopFallingTick", tick); }
        if (!onGround && lastOnGround)             { d.put("lastOnGroundTick", tick); }
        // Crouch
        boolean isCrouching  = p.isSneaking();
        boolean wasCrouching = (boolean) d.getOrDefault("crouching", false);
        if (isCrouching && !wasCrouching)  { d.put("lastCrouchedTick",       tick); }
        if (!isCrouching && wasCrouching)  { d.put("lastStopCrouchingTick",  tick); }
        d.put("crouching", isCrouching);

        // Position history (last 20)
        @SuppressWarnings("unchecked")
        List<double[]> hist = (List<double[]>) d.getOrDefault("posHistory", new ArrayList<>());
        hist.add(0, new double[]{posX, posY, posZ});
        if (hist.size() > 20) hist.remove(hist.size() - 1);
        d.put("posHistory", hist);
    }

    // ── Checks ─────────────────────────────────────────────────────────────────

    // NoSlow: sprinting while using item
    private void checkNoSlow(EntityPlayer p, Map<String, Object> d) {
        int vl = (int) d.getOrDefault("NoSlow_VL", 0);
        long lastAlert = (long) d.getOrDefault("NoSlow_LA", 0L);
        long cd = (long)(noSlowCD.getValue() * 1000);

        boolean isSprinting = (boolean) d.get("sprinting");
        boolean isUsing     = (boolean) d.get("using");
        int tick            = (int) d.get("ticksExisted");
        int lastUsingTick   = (int) d.getOrDefault("lastUsingTick",     0);
        int lastItemChange  = (int) d.getOrDefault("lastItemChangeTick",0);
        int lastStopUsing   = (int) d.getOrDefault("lastStopUsingTick", 0);

        if (isUsing && isSprinting && lastUsingTick - lastItemChange > 1) {
            boolean freshStart = (tick - lastStopUsing) > 7;
            if (freshStart) {
                vl++;
                if (vl >= (int) noSlowVL.getValue() && now() - lastAlert > cd) {
                    d.put("NoSlow_LA", now());
                    flag(p, "NoSlow", vl, (int) noSlowVL.getValue());
                }
            }
        } else {
            vl = Math.max(0, vl - 1);
        }
        d.put("NoSlow_VL", vl);
    }

    // AutoBlock: swinging while blocking with a sword
    private void checkAutoBlock(EntityPlayer p, Map<String, Object> d) {
        int vl = (int) d.getOrDefault("AutoBlock_VL", 0);
        long lastAlert = (long) d.getOrDefault("AutoBlock_LA", 0L);
        long cd = (long)(autoBlockCD.getValue() * 1000);

        boolean isUsing    = (boolean) d.get("using");
        int swingProgress  = (int) d.get("swingProgress");
        ItemStack heldItem = (ItemStack) d.get("heldItem");

        boolean holdingSword = heldItem != null && heldItem.getItem() instanceof ItemSword;

        if (isUsing && holdingSword && swingProgress != 0) {
            vl++;
            if (vl >= (int) autoBlockVL.getValue() && now() - lastAlert > cd) {
                d.put("AutoBlock_LA", now());
                flag(p, "AutoBlock", vl, (int) autoBlockVL.getValue());
            }
        } else {
            vl = Math.max(0, vl - 5);
        }
        d.put("AutoBlock_VL", vl);
    }

    // Sprint: sprinting sideways/backwards at high speed
    private void checkSprint(EntityPlayer p, Map<String, Object> d) {
        int vl = (int) d.getOrDefault("Sprint_VL", 0);
        long lastAlert = (long) d.getOrDefault("Sprint_LA", 0L);
        long cd = (long)(sprintCD.getValue() * 1000);

        boolean isSprinting = (boolean) d.get("sprinting");
        boolean onGround    = (boolean) d.get("onGround");
        float   moveYaw     = (float)   d.get("moveYaw");
        double  px = (double) d.get("posX"), lx = (double) d.get("lastPosX");
        double  pz = (double) d.get("posZ"), lz = (double) d.get("lastPosZ");
        double  speed = Math.max(Math.abs(px - lx), Math.abs(pz - lz));

        if (isSprinting && onGround && Math.abs(moveYaw) > 90 && speed >= 0.2) {
            vl++;
            if (vl >= (int) sprintVL.getValue() && now() - lastAlert > cd) {
                d.put("Sprint_LA", now());
                flag(p, "Sprint", vl, (int) sprintVL.getValue());
            }
        } else {
            vl = Math.max(0, vl - 1);
        }
        d.put("Sprint_VL", vl);
    }

    // Velocity: took damage but didn't move horizontally
    private void checkVelocity(EntityPlayer p, Map<String, Object> d) {
        int vl = (int) d.getOrDefault("Velocity_VL", 0);
        long lastAlert = (long) d.getOrDefault("Velocity_LA", 0L);
        long cd = (long)(velocityCD.getValue() * 1000);

        int  hurtTime    = (int)     d.get("hurtTime");
        int  maxHurtTime = (int)     d.get("maxHurtTime");
        boolean burning  = (boolean) d.get("burning");
        double  px = (double) d.get("posX"), lx = (double) d.get("lastPosX");
        double  pz = (double) d.get("posZ"), lz = (double) d.get("lastPosZ");
        int tick         = (int) d.get("ticksExisted");
        int startFall    = (int) d.getOrDefault("lastStartFallingTick", 0);
        int stopFall     = (int) d.getOrDefault("lastStopFallingTick",  0);
        boolean recentFall = (stopFall - startFall >= 6) && (tick - stopFall <= 6);

        double dXZ = Math.sqrt((px-lx)*(px-lx) + (pz-lz)*(pz-lz));

        if (!burning && hurtTime > 0 && hurtTime < maxHurtTime && !recentFall && dXZ < 1e-8) {
            boolean collided = checkSurroundingBlocks(px, p.posY, pz);
            if (!collided) {
                vl++;
                if (vl >= (int) velocityVL.getValue() && now() - lastAlert > cd) {
                    d.put("Velocity_LA", now());
                    flag(p, "Velocity", vl, (int) velocityVL.getValue());
                }
            }
        } else {
            vl = Math.max(0, vl - 1);
        }
        d.put("Velocity_VL", vl);
    }

    // Rotation: impossible pitch angle
    private void checkRotation(EntityPlayer p, Map<String, Object> d) {
        int vl = (int) d.getOrDefault("Rotation_VL", 0);
        long lastAlert = (long) d.getOrDefault("Rotation_LA", 0L);
        long cd = (long)(rotationCD.getValue() * 1000);

        float pitch = (float) d.get("pitch");

        if (Math.abs(pitch) > 90) {
            vl++;
            if (vl >= (int) rotationVL.getValue() && now() - lastAlert > cd) {
                d.put("Rotation_LA", now());
                flag(p, "Rotation", vl, (int) rotationVL.getValue());
            }
        } else {
            vl = Math.max(0, vl - 1);
        }
        d.put("Rotation_VL", vl);
    }

    // Scaffold: placing blocks while looking down + sneak-spamming
    @SuppressWarnings("unchecked")
    private void checkScaffold(EntityPlayer p, Map<String, Object> d) {
        int vl = (int) d.getOrDefault("Scaffold_VL", 0);
        long lastAlert = (long) d.getOrDefault("Scaffold_LA", 0L);
        long cd = (long)(scaffoldCD.getValue() * 1000);

        int tick            = (int)     d.get("ticksExisted");
        float pitch         = (float)   d.get("pitch");
        boolean onGround    = (boolean) d.get("onGround");
        int lastSwing       = (int)     d.getOrDefault("lastSwingTick",        0);
        int lastStartCrouch = (int)     d.getOrDefault("lastCrouchedTick",     0);
        int lastStopCrouch  = (int)     d.getOrDefault("lastStopCrouchingTick",0);
        ItemStack swingItem = (ItemStack) d.get("lastSwingItem");
        boolean holdingBlock = swingItem != null && swingItem.getItem() instanceof ItemBlock;
        boolean lookingDown  = pitch >= 70f;

        // ScaffoldA: sneak-spam scaffold
        if (lookingDown && holdingBlock && lastSwing == tick
                && lastStopCrouch >= tick - 1 && lastStopCrouch - lastStartCrouch <= 2 && onGround) {
            vl++;
            if (vl >= (int) scaffoldVL.getValue() && now() - lastAlert > cd) {
                d.put("Scaffold_LA", now());
                flag(p, "Scaffold", vl, (int) scaffoldVL.getValue());
            }
        // ScaffoldC: sideways scaffold at speed
        } else {
            List<double[]> hist = (List<double[]>) d.getOrDefault("posHistory", new ArrayList<>());
            float moveYaw = (float) d.get("moveYaw");
            if (lookingDown && holdingBlock && tick - lastSwing <= 10
                    && tick - lastStopCrouch > 30 && Math.abs(moveYaw) >= 90
                    && hist.size() >= 10) {

                double speedSum = 0;
                for (int i = 0; i < hist.size() - 1; i++) {
                    double[] cur = hist.get(i), prev = hist.get(i+1);
                    speedSum += Math.max(Math.abs(cur[0]-prev[0]), Math.abs(cur[2]-prev[2]));
                }
                double avgSpeed = speedSum / (hist.size() - 1);
                double[] first = hist.get(hist.size()-1), last2 = hist.get(0);
                double totalDist = Math.sqrt(Math.pow(last2[0]-first[0],2)+Math.pow(last2[2]-first[2],2));

                if (avgSpeed >= 0.14 && totalDist > 3.4) {
                    vl++;
                    if (vl >= (int) scaffoldVL.getValue() && now() - lastAlert > cd) {
                        d.put("Scaffold_LA", now());
                        flag(p, "Scaffold", vl, (int) scaffoldVL.getValue());
                    }
                } else { vl = Math.max(0, vl - 1); }
            } else { vl = Math.max(0, vl - 1); }
        }
        d.put("Scaffold_VL", vl);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void flag(EntityPlayer p, String check, int vl, int alertThreshold) {
        String name = p.getName();
        ChatUtil.sendFormatted("&8[&cAntiCheat&8] &f" + name + " &7flagged &c" + check + " &8(VL: &c" + vl + "&8)");
        if (pingOnFlag.getValue()) SoundUtil.playSound("note.pling");

        int color = 0xFFFF6644;
        IntelManager.getInstance().addRecentFlag(name, "AC: " + check + " (VL " + vl + ")", color);

        // Auto-blacklist once a check is confirmed well past its alert
        // threshold — a much higher bar than the alert itself, meant to
        // cut down on false positives being blacklisted automatically.
        double confirmThreshold = alertThreshold * confirmMultiplier.getValue();
        String key = name.toLowerCase() + ":" + check;

        if (autoBlacklist.getValue() && vl >= confirmThreshold && !confirmedThisSession.contains(key)) {
            confirmedThisSession.add(key);

            if (!BlacklistManager.getInstance().isBlacklisted(name)) {
                String reason = "AutoCheat: " + check + " (VL " + vl + ")";
                BlacklistManager.getInstance().blacklist(name, reason);

                coralintel.ui.intel.IntelPlayer live = IntelManager.getInstance().getPlayer(name);
                if (live != null) {
                    live.blacklisted = true;
                    live.blacklistReason = reason;
                    live.computeThreat();
                }

                ChatUtil.sendFormatted("&9⚑ &fAuto-blacklisted &f" + name + " &7— " + reason);
            }
        }
    }

    private boolean checkSurroundingBlocks(double x, double y, double z) {
        int baseY = (int) Math.floor(y);
        double[][] offsets = {{0.5,0},{-0.5,0},{0,0.5},{0,-0.5}};
        for (double[] off : offsets) {
            try {
                BlockPos leg   = new BlockPos((int)Math.floor(x+off[0]), baseY,   (int)Math.floor(z+off[1]));
                BlockPos torso = new BlockPos((int)Math.floor(x+off[0]), baseY+1, (int)Math.floor(z+off[1]));
                if (mc.theWorld.getBlockState(leg).getBlock()   != net.minecraft.init.Blocks.air) return true;
                if (mc.theWorld.getBlockState(torso).getBlock() != net.minecraft.init.Blocks.air) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private float getMoveYaw(double dx, double dz, float playerYaw) {
        if (Math.abs(dx) < 1e-8 && Math.abs(dz) < 1e-8) return 0f;
        float angle = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float rel   = ((angle - playerYaw) % 360f + 360f) % 360f;
        return rel > 180f ? rel - 360f : rel;
    }

    private boolean itemChanged(ItemStack a, ItemStack b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return a.getMetadata() != b.getMetadata() || !a.getUnlocalizedName().equals(b.getUnlocalizedName());
    }

    private long now() { return System.currentTimeMillis(); }
}
