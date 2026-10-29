package com.customcrosshair;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Ties the mod's hitboxes to Minecraft's own F3+B hitboxes.
 * On/off is the same switch F3+B uses, and the mod restyles those boxes
 * (colour for the entity you're looking at, line width, extra lines, filters).
 */
public final class VanillaHitboxes {
    private VanillaHitboxes() { }

    private static Identifier entryId;
    private static Method alwaysOnTop;
    private static boolean lookedForAlwaysOnTop;

    /** The id of the "entity hitboxes" debug option that F3+B toggles. */
    private static Identifier id() {
        if (entryId == null) {
            // Look the id up by name so a rename in Minecraft doesn't break the build.
            try {
                for (Field f : DebugScreenEntries.class.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) && f.getType() == Identifier.class
                            && f.getName().toUpperCase().contains("HITBOX")) {
                        f.setAccessible(true);
                        entryId = (Identifier) f.get(null);
                        break;
                    }
                }
            } catch (Exception ignored) { }
            if (entryId == null) entryId = Identifier.withDefaultNamespace("entity_hitboxes");
        }
        return entryId;
    }

    private static DebugScreenEntryList entries() {
        return Minecraft.getInstance().debugEntries;
    }

    public static boolean isOn() {
        DebugScreenEntryList list = entries();
        return list != null && list.isCurrentlyEnabled(id());
    }

    /** Same as pressing F3+B. */
    public static void toggle() {
        DebugScreenEntryList list = entries();
        if (list != null) list.toggleStatus(id());
    }

    /**
     * Called instead of Minecraft's hitbox drawing for each entity.
     * Runs inside Minecraft's own drawing pass, so the boxes appear exactly where F3+B's would.
     */
    public static void draw(Entity e, float tick) {
        CrosshairConfig cfg = CrosshairConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        switch (cfg.hitboxTargets) {
            case PLAYERS -> { if (!(e instanceof Player)) return; }
            case LIVING -> { if (!(e instanceof LivingEntity)) return; }
            default -> { }
        }

        // Minecraft only reports an entity here when it's under your crosshair AND within hit range.
        boolean targeted = mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() == e;
        if (!targeted && !cfg.hitboxShowOutOfRange) return;

        Vec3 pos = e.getPosition(tick);
        AABB box = e.getBoundingBox().move(pos.subtract(e.position()));

        int color = targeted ? cfg.hitboxColor : cfg.hitboxOutColor;
        show(Gizmos.cuboid(box, GizmoStyle.stroke(color, cfg.hitboxWidth)), cfg);

        if (cfg.vanillaHitboxDirection) {
            double eyeY = pos.y + e.getEyeHeight();
            // red eye-height line
            show(Gizmos.cuboid(new AABB(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ),
                    GizmoStyle.stroke(0xFFFF0000)), cfg);
            // blue looking-direction line
            Vec3 eye = new Vec3(pos.x, eyeY, pos.z);
            show(Gizmos.arrow(eye, eye.add(e.getViewVector(tick).scale(2.0)), 0xFF0000FF), cfg);
        }
    }

    /** "Through walls": finds Minecraft's always-on-top switch for gizmos by name. */
    private static void show(GizmoProperties gizmo, CrosshairConfig cfg) {
        if (!cfg.hitboxThroughWalls || gizmo == null) return;
        if (!lookedForAlwaysOnTop) {
            lookedForAlwaysOnTop = true;
            for (Method m : GizmoProperties.class.getMethods()) {
                String n = m.getName().toLowerCase();
                if (m.getParameterCount() == 0 && (n.contains("top") || n.contains("occlu") || n.contains("depth"))) {
                    alwaysOnTop = m;
                    break;
                }
            }
        }
        if (alwaysOnTop != null) {
            try { alwaysOnTop.invoke(gizmo); } catch (Exception ignored) { }
        }
    }
}
