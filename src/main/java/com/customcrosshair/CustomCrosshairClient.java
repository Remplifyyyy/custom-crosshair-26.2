package com.customcrosshair;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomCrosshairClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("remplify-crosshair-indicator");

    private static KeyMapping openEditor;
    private static KeyMapping toggleHitboxes;

    @Override
    public void onInitializeClient() {
        CrosshairConfig.load();

        // Both keys appear in Options > Controls > Key Binds under "Remplify Crosshair Indicator".
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("customcrosshair", "main"));
        openEditor = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.customcrosshair.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        toggleHitboxes = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.customcrosshair.hitboxes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditor.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new CrosshairEditorScreen(null));
            }
            while (toggleHitboxes.consumeClick()) {
                // exactly the same switch as F3+B
                VanillaHitboxes.toggle();
                boolean on = VanillaHitboxes.isOn();
                if (CrosshairConfig.get().showToggleFeedback) client.gui.hud.setOverlayMessage(Component.literal("Hitboxes: ")
                        .append(on
                                ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                                : Component.literal("OFF").withStyle(ChatFormatting.RED)), false);
            }
        });

        // Swap the vanilla crosshair for ours.
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR,
                original -> (graphics, deltaTracker) -> CrosshairRenderer.renderHud(graphics));
    }
}
