package com.customcrosshair.mixin;

import com.customcrosshair.VanillaHitboxes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces how Minecraft draws each F3+B hitbox with the mod's styled version
 * (colours, width, optional red/blue lines). F3+B still turns them on and off.
 * require = 0: if Minecraft changes this method, the game still starts (you'd just get plain F3+B boxes).
 */
@Mixin(targets = "net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer")
public abstract class EntityHitboxDebugRendererMixin {

    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcrosshair$showHitboxes(Entity entity, float partialTick, boolean isServerEntity, CallbackInfo ci) {
        ci.cancel();
        // In singleplayer Minecraft also draws a second copy using the built-in server's entity.
        // Only draw the client's own copy so there are no doubled boxes.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(entity.getId()) == entity) {
            VanillaHitboxes.draw(entity, partialTick);
        }
    }
}
