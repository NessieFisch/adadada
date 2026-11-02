package com.myclient.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Smooth vector-style icons built from the Gfx primitives. */
public final class Icons {
    private static final int DARK = 0xFF15110A;

    public static void avatar(GuiGraphicsExtractor g, Font tr, int x, int y, String name, int accent) {
        Gfx.circle(g, x + 7, y + 7, 7, accent);
        Gfx.circle(g, x + 7, y + 7, 6.2f, 0xFF17130B);
        String l = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        Txt.draw(g, tr, l, x + 7 - Txt.w(tr, l) / 2, y + 3, accent, false);
    }

    public static void monitor(GuiGraphicsExtractor g, int x, int y, int col) {
        Gfx.roundF(g, x, y, 11, 7, 1.5f, col);
        Gfx.roundF(g, x + 1, y + 1, 9, 5, 1f, 0x55000000);
        Gfx.rectF(g, x + 4.5f, y + 7, 2, 1.2f, col);
        Gfx.roundF(g, x + 2.5f, y + 8, 6, 1, 0.5f, col);
    }

    public static void cloud(GuiGraphicsExtractor g, int x, int y, int col) {
        Gfx.circle(g, x + 3.5f, y + 5f, 2.6f, col);
        Gfx.circle(g, x + 6.3f, y + 3.6f, 3.2f, col);
        Gfx.circle(g, x + 9f, y + 5.2f, 2.4f, col);
        Gfx.roundF(g, x + 1, y + 5f, 10, 2.8f, 1.4f, col);
    }

    public static void clock(GuiGraphicsExtractor g, int x, int y, int col) {
        Gfx.circle(g, x + 5, y + 5, 5, col);
        Gfx.rectF(g, x + 4.75f, y + 1.8f, 0.5f, 3.4f, DARK);
        Gfx.rectF(g, x + 4.75f, y + 4.75f, 2.6f, 0.5f, DARK);
    }

    public static void arrow(GuiGraphicsExtractor g, int x, int y, int col) {
        Gfx.tri(g, x, y, 7, 8, 2, col);
        Gfx.tri(g, x + 1.5f, y + 5, 4, 3, 3, DARK);
    }

    public static void prev(GuiGraphicsExtractor g, int x, int y, int col) {
        Gfx.roundF(g, x, y, 1.2f, 7, 0.5f, col);
        Gfx.tri(g, x + 2, y, 6, 7, 1, col);
    }

    public static void next(GuiGraphicsExtractor g, int x, int y, int col) {
        Gfx.tri(g, x, y, 6, 7, 0, col);
        Gfx.roundF(g, x + 6.8f, y, 1.2f, 7, 0.5f, col);
    }

    public static void pause(GuiGraphicsExtractor g, int x, int y, int col) {
        Gfx.roundF(g, x, y, 2.2f, 8, 1f, col);
        Gfx.roundF(g, x + 4, y, 2.2f, 8, 1f, col);
    }

    public static void play(GuiGraphicsExtractor g, int x, int y, int col) {
        Gfx.tri(g, x, y, 7, 8, 0, col);
    }

    public static void note(GuiGraphicsExtractor g, int x, int y, int col) {
        Gfx.circle(g, x + 2.5f, y + 8.5f, 2.3f, col);
        Gfx.rectF(g, x + 4.3f, y, 1, 8.6f, col);
        Gfx.roundF(g, x + 4.3f, y, 4, 2.2f, 1.1f, col);
    }

    /** Chess king, drawn inside a 22 x 22 box at (x, y). */
    public static void king(GuiGraphicsExtractor g, int x, int y, int col) {
        float cx = x + 11f;
        Gfx.rectF(g, cx - 0.8f, y + 2.0f, 1.6f, 6.0f, col);      // cross, vertical
        Gfx.rectF(g, cx - 2.6f, y + 3.6f, 5.2f, 1.6f, col);      // cross, horizontal
        Gfx.roundF(g, cx - 4.6f, y + 8.4f, 9.2f, 4.2f, 2.1f, col); // crown
        Gfx.circle(g, cx - 4.4f, y + 10.4f, 1.9f, col);          // crown sides
        Gfx.circle(g, cx + 4.4f, y + 10.4f, 1.9f, col);
        Gfx.roundF(g, cx - 3.2f, y + 12.0f, 6.4f, 4.6f, 1.2f, col); // body
        Gfx.roundF(g, cx - 5.4f, y + 15.6f, 10.8f, 2.4f, 1.2f, col); // flare
        Gfx.roundF(g, cx - 6.8f, y + 17.8f, 13.6f, 2.6f, 1.3f, col); // base
    }
}
