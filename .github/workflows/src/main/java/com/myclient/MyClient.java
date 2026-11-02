package com.myclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.myclient.gui.ClickGuiScreen;
import com.myclient.gui.StarterScreen;
import com.myclient.hud.HudRenderer;
import com.myclient.mods.ModTicker;
import com.myclient.music.MusicPlayer;
import com.myclient.sky.CursorRenderer;
import com.myclient.sky.CursorStyle;
import com.myclient.sky.Skies;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class MyClient implements ClientModInitializer {
    public static KeyMapping menuKey, zoomKey, nextKey, prevKey, playKey;

    @Override
    public void onInitializeClient() {
        ClientConfig.get();
        MusicPlayer.init(ClientConfig.get().musicVolume, ClientConfig.get().systemMusic);

        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.myclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.myclient.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, KeyMapping.Category.MISC));

        nextKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.myclient.next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, KeyMapping.Category.MISC));
        prevKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.myclient.prev", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC));
        playKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.myclient.play", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (nextKey.consumeClick()) MusicPlayer.next();
            while (prevKey.consumeClick()) MusicPlayer.prev();
            while (playKey.consumeClick()) MusicPlayer.toggle();
            HudRenderer.tick(mc);
            ModTicker.tick(mc, zoomKey);
            while (menuKey.consumeClick()) {
                if (!ScreenUtil.screenOpen()) ScreenUtil.open(new ClickGuiScreen());
            }
            // replace the vanilla title screen with our start screen
            if (mc.level != null) {
                StarterScreen.bypass = false;
            } else if (ClientConfig.get().starterScreen && !StarterScreen.bypass) {
                Object cur = ScreenUtil.current();
                if (cur != null && cur.getClass().getName().equals("net.minecraft.client.gui.screens.TitleScreen")) {
                    ScreenUtil.open(new StarterScreen());
                }
            }
        });

        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("myclient", "sky"), (g, dt) -> {
            if (Minecraft.getInstance().level != null) Skies.render(g);
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("myclient", "hud"), (g, dt) -> {
            if (Minecraft.getInstance().level != null) HudRenderer.render(g);
        });

        // custom crosshair: replace the vanilla element, no mixin needed
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, dt) -> {
            ClientConfig c = ClientConfig.get();
            if (!c.customCrosshair) { original.extractRenderState(g, dt); return; }
            CursorStyle s = c.cursor == CursorStyle.ARROW ? CursorStyle.CROSS : c.cursor;
            CursorRenderer.draw(g, g.guiWidth() / 2, g.guiHeight() / 2, s, c.cursorColor, c.cursorSize);
        });
    }
}
