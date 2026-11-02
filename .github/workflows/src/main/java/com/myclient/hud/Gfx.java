package com.myclient.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.minecraft.client.gui.Font;

import java.lang.reflect.Method;

/**
 * Smooth drawing helpers. Everything is drawn on a grid twice as fine as Minecraft's GUI pixels,
 * with anti-aliased edges. If the pose stack can't be scaled it falls back to plain blocky fills.
 */
public final class Gfx {
    private static Method poseM, pushM, popM, scaleM, translateM;
    private static boolean init, ok, threeD;

    public static boolean begin(GuiGraphicsExtractor g) { return begin(g, 0.5f); }

    private static void ensureInit(GuiGraphicsExtractor g) {
        if (init) return;
        init = true;
        try {
            poseM = g.getClass().getMethod("pose");
            Object p = poseM.invoke(g);
            try {
                pushM = p.getClass().getMethod("pushMatrix");
                popM = p.getClass().getMethod("popMatrix");
                scaleM = p.getClass().getMethod("scale", float.class, float.class);
            } catch (Throwable t) {
                pushM = p.getClass().getMethod("pushPose");
                popM = p.getClass().getMethod("popPose");
                scaleM = p.getClass().getMethod("scale", float.class, float.class, float.class);
                threeD = true;
            }
            try {
                translateM = threeD ? p.getClass().getMethod("translate", float.class, float.class, float.class)
                                    : p.getClass().getMethod("translate", float.class, float.class);
            } catch (Throwable t) { translateM = null; }
            ok = true;
        } catch (Throwable t) { ok = false; }
    }

    public static boolean begin(GuiGraphicsExtractor g, float s) {
        if (!com.myclient.ClientConfig.get().smoothUi) return false;
        ensureInit(g);
        if (!ok) return false;
        try {
            Object p = poseM.invoke(g);
            pushM.invoke(p);
            if (threeD) scaleM.invoke(p, s, s, 1f); else scaleM.invoke(p, s, s);
            return true;
        } catch (Throwable t) { ok = false; return false; }
    }

    /** Scales everything drawn until popTransform() around the point (x, y). Used for HUD element sizes. */
    public static boolean pushTransform(GuiGraphicsExtractor g, float x, float y, float s) {
        ensureInit(g);
        if (!ok || translateM == null) return false;
        try {
            Object p = poseM.invoke(g);
            pushM.invoke(p);
            if (threeD) { translateM.invoke(p, x, y, 0f); scaleM.invoke(p, s, s, 1f); translateM.invoke(p, -x, -y, 0f); }
            else { translateM.invoke(p, x, y); scaleM.invoke(p, s, s); translateM.invoke(p, -x, -y); }
            return true;
        } catch (Throwable t) { return false; }
    }

    public static void popTransform(GuiGraphicsExtractor g) { end(g); }

    public static void end(GuiGraphicsExtractor g) {
        try { popM.invoke(poseM.invoke(g)); } catch (Throwable ignored) {}
    }

    // ---------- colour helpers ----------
    public static int a(int c, float f) {
        int al = Math.min(255, Math.max(0, Math.round(((c >>> 24) & 0xFF) * f)));
        return (al << 24) | (c & 0x00FFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int al = Math.round(((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int gr = Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (al << 24) | (r << 16) | (gr << 8) | b;
    }

    // ---------- core: rounded rect in half-units (X,Y,W,H,R are all in 1/2 GUI pixels) ----------
    // Rows with the same corner inset are merged into one slab, so a rounded rect is ~15 fills.
    private static void slab(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int top, int bot, int Y, int H) {
        if (x2 <= x1 || y2 <= y1) return;
        int c1 = lerp(top, bot, (y1 - Y) / (float) H), c2 = lerp(top, bot, (y2 - Y) / (float) H);
        if (c1 == c2) g.fill(x1, y1, x2, y2, c1); else g.fillGradient(x1, y1, x2, y2, c1, c2);
    }

    private static void rr2(GuiGraphicsExtractor g, int X, int Y, int W, int H, int R, int top, int bot) {
        if (W <= 0 || H <= 0) return;
        R = Math.max(0, Math.min(R, Math.min(W, H) / 2));
        if (R == 0) { slab(g, X, Y, X + W, Y + H, top, bot, Y, H); return; }
        int[] ins = new int[R];
        for (int i = 0; i < R; i++) {
            double dy = R - (i + 0.5);
            ins[i] = (int) Math.round(R - Math.sqrt(Math.max(0, (double) R * R - dy * dy)));
        }
        int i = 0;
        while (i < R) {
            int j = i;
            while (j + 1 < R && ins[j + 1] == ins[i]) j++;
            slab(g, X + ins[i], Y + i, X + W - ins[i], Y + j + 1, top, bot, Y, H);
            i = j + 1;
        }
        if (H - R > R) slab(g, X, Y + R, X + W, Y + H - R, top, bot, Y, H);
        int k = 0;
        while (k < R) {
            int idx = R - 1 - k, j = k;
            while (j + 1 < R && ins[R - 1 - (j + 1)] == ins[idx]) j++;
            slab(g, X + ins[idx], Y + H - R + k, X + W - ins[idx], Y + H - R + j + 1, top, bot, Y, H);
            k = j + 1;
        }
    }

    private static void blocky(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int inset = 0;
            if (i < r) inset = r - (int) Math.sqrt(r * r - (r - i) * (r - i));
            else if (i >= h - r) { int j = h - 1 - i; inset = r - (int) Math.sqrt(r * r - (r - j) * (r - j)); }
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    // ---------- public API (GUI units) ----------
    public static void roundV2(GuiGraphicsExtractor g, int X, int Y, int W, int H, int R, int top, int bot) {
        if (begin(g)) { try { rr2(g, X, Y, W, H, R, top, bot); } finally { end(g); } }
        else blocky(g, X / 2, Y / 2, W / 2, H / 2, R / 2, top);
    }

    public static void roundV(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bot) {
        roundV2(g, x * 2, y * 2, w * 2, h * 2, r * 2, top, bot);
    }

    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        roundV2(g, x * 2, y * 2, w * 2, h * 2, r * 2, color, color);
    }

    public static void roundF(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
        roundV2(g, Math.round(x * 2), Math.round(y * 2), Math.max(1, Math.round(w * 2)), Math.max(1, Math.round(h * 2)),
                Math.round(r * 2), color, color);
    }

    public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
        roundF(g, cx - r, cy - r, 2 * r, 2 * r, r, color);
    }

    public static void rectF(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        if (begin(g)) {
            try {
                int X = Math.round(x * 2), Y = Math.round(y * 2);
                g.fill(X, Y, X + Math.max(1, Math.round(w * 2)), Y + Math.max(1, Math.round(h * 2)), color);
            } finally { end(g); }
        } else g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    /** Filled fine-grid rectangle in half-units. */
    public static void fill2(GuiGraphicsExtractor g, int X1, int Y1, int X2, int Y2, int color) {
        if (begin(g)) { try { g.fill(X1, Y1, X2, Y2, color); } finally { end(g); } }
        else g.fill(X1 / 2, Y1 / 2, X2 / 2, Y2 / 2, color);
    }

    public static void gradient2(GuiGraphicsExtractor g, int X1, int Y1, int X2, int Y2, int c1, int c2) {
        if (begin(g)) { try { g.fillGradient(X1, Y1, X2, Y2, c1, c2); } finally { end(g); } }
        else g.fillGradient(X1 / 2, Y1 / 2, X2 / 2, Y2 / 2, c1, c2);
    }

    /** Rounded rect with a thin (half pixel) border. */
    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int borderCol, int top, int bot) {
        if (begin(g)) {
            try {
                int X = x * 2, Y = y * 2, W = w * 2, H = h * 2, R = r * 2;
                rr2(g, X, Y, W, H, R, borderCol, borderCol);
                rr2(g, X + 1, Y + 1, W - 2, H - 2, Math.max(0, R - 1), top, bot);
            } finally { end(g); }
        } else {
            blocky(g, x, y, w, h, r, borderCol);
            blocky(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), top);
        }
    }

    /** Soft drop shadow / glow behind a rounded rect (layers = 2..4). */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int rgb, int maxAlpha, int yOff2, int layers) {
        if (!begin(g)) return;
        try {
            for (int k = layers; k >= 1; k--) {
                float t = (layers + 1 - k) / (float) (layers + 1);
                int col = ((int) (maxAlpha * t * t) << 24) | (rgb & 0x00FFFFFF);
                int d = k * 3;
                rr2(g, x * 2 - d, y * 2 - d + yOff2, w * 2 + d * 2, h * 2 + d * 2, r * 2 + d, col, col);
            }
        } finally { end(g); }
    }

    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int rgb, int maxAlpha, int yOff2) {
        shadow(g, x, y, w, h, r, rgb, maxAlpha, yOff2, 3);
    }

    /** Subtle light streak along the top of a panel. */
    public static void shine(GuiGraphicsExtractor g, int x, int y, int w, int r) {
        gradient2(g, x * 2 + r * 2, y * 2 + 2, (x + w) * 2 - r * 2, y * 2 + 12, 0x2CFFFFFF, 0x00FFFFFF);
    }

    /** dir: 0 right, 1 left, 2 up, 3 down */
    public static void tri(GuiGraphicsExtractor g, float x, float y, float w, float h, int dir, int color) {
        if (!begin(g)) return;
        try {
            int X = Math.round(x * 2), Y = Math.round(y * 2), W = Math.max(1, Math.round(w * 2)), H = Math.max(1, Math.round(h * 2));
            if (dir < 2) {
                for (int j = 0; j < W; j++) {
                    float f = dir == 0 ? (j + 0.5f) / W : 1 - (j + 0.5f) / W;
                    int hh = Math.round((1 - f) * H / 2f);
                    if (hh <= 0) continue;
                    g.fill(X + j, Y + H / 2 - hh, X + j + 1, Y + H / 2 + hh, color);
                }
            } else {
                for (int i = 0; i < H; i++) {
                    float f = dir == 2 ? (i + 0.5f) / H : 1 - (i + 0.5f) / H;
                    int hw = Math.round(f * W / 2f);
                    if (hw <= 0) continue;
                    g.fill(X + W / 2 - hw, Y + i, X + W / 2 + hw, Y + i + 1, color);
                }
            }
        } finally { end(g); }
    }

    /** Thin ring; thick is in half-units. */
    public static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, int thick, int color) {
        if (!begin(g)) {
            for (int k = 0; k < 32; k++) {
                double ang = k * Math.PI * 2 / 32;
                int px = (int) Math.round(cx + Math.cos(ang) * r), py = (int) Math.round(cy + Math.sin(ang) * r);
                g.fill(px, py, px + 1, py + 1, color);
            }
            return;
        }
        try {
            int n = Math.max(32, (int) (2 * Math.PI * r * 2));
            for (int k = 0; k < n; k++) {
                double ang = k * Math.PI * 2 / n;
                int px = (int) Math.round(cx * 2 + Math.cos(ang) * r * 2), py = (int) Math.round(cy * 2 + Math.sin(ang) * r * 2);
                g.fill(px - thick / 2, py - thick / 2, px - thick / 2 + thick, py - thick / 2 + thick, color);
            }
        } finally { end(g); }
    }

    /** Mouse-pointer arrow with its tip at (x, y). */
    public static void pointer(GuiGraphicsExtractor g, float x, float y, float size, int color) {
        if (!begin(g)) {
            int H = Math.round(10 * size);
            for (int i = 0; i < H; i++) g.fill(Math.round(x), Math.round(y) + i, Math.round(x) + (int) (i * 0.6) + 1, Math.round(y) + i + 1, color);
            return;
        }
        try {
            int X = Math.round(x * 2), Y = Math.round(y * 2), H = Math.round(13 * size * 2);
            for (int i = 0; i < H; i++) {
                int w = (int) (i * 0.62) + 1;
                g.fill(X, Y + i, X + w, Y + i + 1, color);
            }
        } finally { end(g); }
    }

    /** Text drawn at a larger scale, horizontally centered on cx. */
    public static void bigText(GuiGraphicsExtractor g, Font tr, String text, float cx, float y, float s, int color, boolean shadow) {
        if (begin(g, s)) {
            try {
                int w = Txt.w(tr, text);
                Txt.draw(g, tr, text, Math.round(cx / s - w / 2f), Math.round(y / s), color, shadow);
            } finally { end(g); }
        } else {
            Txt.draw(g, tr, text, Math.round(cx - Txt.w(tr, text) / 2f), Math.round(y), color, shadow);
        }
    }

    /** Text at a larger scale, left edge at x, in a given font id (null = Minecraft's). */
    public static void textAt(GuiGraphicsExtractor g, Font tr, String fontId, String text, float x, float y, float s, int color, boolean shadow) {
        if (begin(g, s)) {
            try { Txt.drawF(g, tr, fontId, text, Math.round(x / s), Math.round(y / s), color, shadow); }
            finally { end(g); }
        } else {
            Txt.drawF(g, tr, fontId, text, Math.round(x), Math.round(y), color, shadow);
        }
    }
}
