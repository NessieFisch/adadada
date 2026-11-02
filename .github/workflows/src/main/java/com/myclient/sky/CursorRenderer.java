package com.myclient.sky;

import com.myclient.hud.Gfx;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CursorRenderer {
    public static void draw(GuiGraphicsExtractor g, int cx, int cy, CursorStyle style, int color, float size) {
        switch (style) {
            case DOT -> Gfx.circle(g, cx, cy, Math.max(1.2f, 2f * size), color);
            case RING -> {
                Gfx.ring(g, cx, cy, 5f * size, 2, color);
            }
            case CROSS -> {
                float gap = 2f * size, len = 5f * size;
                Gfx.rectF(g, cx - gap - len, cy - 0.5f, len, 1f, color);
                Gfx.rectF(g, cx + gap, cy - 0.5f, len, 1f, color);
                Gfx.rectF(g, cx - 0.5f, cy - gap - len, 1f, len, color);
                Gfx.rectF(g, cx - 0.5f, cy + gap, 1f, len, color);
            }
            case ARROW -> Gfx.pointer(g, cx, cy, size, color);
        }
    }
}
