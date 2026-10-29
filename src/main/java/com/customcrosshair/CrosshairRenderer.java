package com.customcrosshair;

import com.customcrosshair.CrosshairConfig.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import static com.customcrosshair.CrosshairConfig.GRID;

public final class CrosshairRenderer {
    private CrosshairRenderer() { }

    /** Plain white sprite; drawn with the vanilla crosshair pipeline it gives the vanilla inverted look. */
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("customcrosshair", "hud/white");

    /** Draws the in-game crosshair in the middle of the screen. */
    public static void renderHud(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        CrosshairConfig cfg = CrosshairConfig.get();
        if (cfg.hideInThirdPerson && !mc.options.getCameraType().isFirstPerson()) return;

        boolean targeting = cfg.targetEnabled && isLookingAtTarget(mc, cfg);
        Profile look = targeting ? cfg.target : cfg.normal;
        boolean[] shape = (targeting && cfg.targetUsesNormalShape) ? cfg.normal.pixels : look.pixels;

        draw(ctx, shape, look, ctx.guiWidth() / 2f, ctx.guiHeight() / 2f, look.scale);
    }

    public static boolean isLookingAtTarget(Minecraft mc, CrosshairConfig cfg) {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY || !(hit instanceof EntityHitResult ehr)) return false;
        Entity e = ehr.getEntity();
        if (e == null) return false;
        return switch (cfg.targetMode) {
            case ALL -> true;
            case LIVING -> e instanceof LivingEntity;
            case HOSTILE -> e instanceof Enemy;
            case PLAYERS -> e instanceof Player;
        };
    }

    /**
     * Draws a crosshair drawing centred on (cx, cy) in GUI coordinates.
     * Works in real screen pixels so in-between sizes (e.g. 130%) are possible.
     * At 100% one drawn pixel = one GUI pixel, exactly like vanilla.
     */
    public static void draw(GuiGraphicsExtractor ctx, boolean[] shape, Profile look, float cx, float cy, float size) {
        Minecraft mc = Minecraft.getInstance();
        float gui = (float) mc.getWindow().getGuiScale();
        if (!(gui > 0)) gui = 1f;

        int cellPx = Math.max(1, Math.round(size * gui));        // real pixels per drawn pixel
        int total = cellPx * GRID;
        int left = Math.round(cx * gui) - total / 2;
        int top = Math.round(cy * gui) - total / 2;
        int border = Math.max(1, Math.round(gui / 2f));

        ctx.pose().pushMatrix();
        ctx.pose().scale(1f / gui, 1f / gui);

        if (look.outline) {
            for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) {
                if (!shape[y * GRID + x]) continue;
                int px = left + x * cellPx, py = top + y * cellPx;
                ctx.fill(px - border, py - border, px + cellPx + border, py + cellPx + border, look.outlineColor);
            }
        }
        for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) {
            if (!shape[y * GRID + x]) continue;
            int px = left + x * cellPx, py = top + y * cellPx;
            if (look.vanillaStyle) {
                ctx.blitSprite(RenderPipelines.CROSSHAIR, WHITE, px, py, cellPx, cellPx);
            } else {
                ctx.fill(px, py, px + cellPx, py + cellPx, look.color);
            }
        }

        ctx.pose().popMatrix();
    }
}
