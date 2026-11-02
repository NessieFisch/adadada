package com.myclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Reflection keeps this working whether 26.2 exposes gui.setScreen() or setScreenAndShow(). */
public final class ScreenUtil {
    public static void open(Screen s) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Object gui = mc.getClass().getField("gui").get(mc);
            gui.getClass().getMethod("setScreen", Screen.class).invoke(gui, s);
            return;
        } catch (Throwable ignored) {}
        for (String n : new String[]{"setScreenAndShow", "setScreen"}) {
            try { mc.getClass().getMethod(n, Screen.class).invoke(mc, s); return; } catch (Throwable ignored) {}
        }
        throw new IllegalStateException("Could not find a way to open a screen on this Minecraft build");
    }

    public static boolean screenOpen() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Object gui = mc.getClass().getField("gui").get(mc);
            return gui.getClass().getMethod("screen").invoke(gui) != null;
        } catch (Throwable ignored) {}
        try { return mc.getClass().getField("screen").get(mc) != null; } catch (Throwable ignored) {}
        return false;
    }

    public static Object current() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Object gui = mc.getClass().getField("gui").get(mc);
            return gui.getClass().getMethod("screen").invoke(gui);
        } catch (Throwable ignored) {}
        try { return mc.getClass().getField("screen").get(mc); } catch (Throwable ignored) {}
        return null;
    }
}
