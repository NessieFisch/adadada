package com.myclient.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/** Draws an item icon. Looks the method up by shape so it survives small renames. Returns false if none found. */
public final class ItemIcons {
    private static Method m;
    private static boolean tried;

    public static boolean draw(GuiGraphicsExtractor g, ItemStack s, int x, int y) {
        if (!tried) {
            tried = true;
            for (String n : new String[] { "item", "renderItem", "fakeItem" }) {
                try { m = GuiGraphicsExtractor.class.getMethod(n, ItemStack.class, int.class, int.class); break; }
                catch (Throwable ignored) {}
            }
        }
        if (m == null || s.isEmpty()) return false;
        try { m.invoke(g, s, x, y); return true; } catch (Throwable t) { m = null; return false; }
    }
}
