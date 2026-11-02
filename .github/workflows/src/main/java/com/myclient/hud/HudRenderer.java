package com.myclient.hud;

import com.myclient.ClientConfig;
import com.myclient.music.MusicPlayer;
import com.myclient.music.NowPlaying;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class HudRenderer {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final float[] speedHist = new float[40];
    private static final float[] memHist = new float[40];
    private static double lx, lz;
    private static boolean has;
    private static float speed;
    private static int fps, frames;
    private static long fpsStamp = System.nanoTime();

    private static void push(float[] a, float v) {
        System.arraycopy(a, 1, a, 0, a.length - 1);
        a[a.length - 1] = v;
    }

    /** Called every client tick (20/s). */
    public static void tick(Minecraft mc) {
        if (mc.player == null) { has = false; return; }
        double x = mc.player.getX(), z = mc.player.getZ();
        if (has) speed = (float) (Math.hypot(x - lx, z - lz) * 20.0);
        lx = x; lz = z; has = true;
        push(speedHist, speed);
        Runtime rt = Runtime.getRuntime();
        push(memHist, (rt.totalMemory() - rt.freeMemory()) / (float) rt.maxMemory());
    }

    // ---------- shared helpers (ModHud uses these too) ----------
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        Gfx.round(g, x, y, w, h, r, color);
    }

    /** dark version of the accent colour, used for glass backgrounds */
    public static int tint(int accent, float k, int alpha) {
        int r = (int) (((accent >> 16) & 255) * k), gr = (int) (((accent >> 8) & 255) * k), b = (int) ((accent & 255) * k);
        return (alpha << 24) | (r << 16) | (gr << 8) | b;
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        panelR(g, x, y, w, h, Math.min(7, h / 2), accent);
    }

    /** Modern glass panel: soft glow, hairline border, gradient body, top shine. */
    public static void panelR(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int accent) {
        Gfx.shadow(g, x, y, w, h, r, accent, 0x30, 0, 2);
        Gfx.border(g, x, y, w, h, r, (accent & 0x00FFFFFF) | 0x66000000,
                tint(accent, 0.30f, 0xCC), tint(accent, 0.10f, 0xE0));
        Gfx.shine(g, x, y, w, r);
    }

    /** Smooth area graph (one batch, ~3 fills per sample). */
    private static void spark(GuiGraphicsExtractor g, int x, int y, int w, int h, float[] hist, float max, int color) {
        if (!Gfx.begin(g)) return;
        try {
            int n = hist.length, cw = Math.max(1, (w * 2) / n), bot = (y + h) * 2, prev = -1;
            for (int i = 0; i < n; i++) {
                int X = x * 2 + i * cw;
                int hh = Math.max(2, Math.min(h * 2 - 1, (int) (hist[i] / max * h * 2)));
                int yy = bot - hh;
                g.fillGradient(X, yy, X + cw, bot, Gfx.a(color, 0.38f), Gfx.a(color, 0f));
                g.fill(X, yy, X + cw, yy + 2, color);
                if (prev >= 0 && prev != yy) g.fill(X, Math.min(prev, yy), X + 2, Math.max(prev, yy) + 2, color);
                prev = yy;
            }
        } finally { Gfx.end(g); }
    }

    /** Middle pill of the top bar: dot, time and the song that is playing. */
    private static void nowPill(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h, int accent) {
        Font tr = mc.font;
        Gfx.circle(g, x + 11, y + h / 2f, 5f, Gfx.a(accent, 0.35f));
        Gfx.circle(g, x + 11, y + h / 2f, 2.4f, accent);
        String title = NowPlaying.title();
        if (title == null || title.isEmpty()) {
            Txt.draw(g, tr, mc.getUser().getName(), x + 22, y + (h - 8) / 2, 0xFFE8E4F5, false);
            return;
        }
        long pos = NowPlaying.positionMs() / 1000;
        String t = (pos / 60) + ":" + String.format("%02d", pos % 60);
        Txt.draw(g, tr, t, x + 22, y + (h - 8) / 2, Gfx.lerp(accent, 0xFFFFFFFF, 0.55f), false);
        int lx = x + 22 + Txt.w(tr, t) + 8, avail = x + w - 8 - lx;
        String a = NowPlaying.artist();
        String s = a == null || a.isEmpty() ? title : title + " - " + a;
        while (s.length() > 1 && Txt.w(tr, s) > avail) s = s.substring(0, s.length() - 1);
        Txt.draw(g, tr, s, lx, y + (h - 8) / 2, 0xFFFFFFFF, false);
    }

    private static String fit(Font tr, String s, int max) {
        while (s.length() > 1 && Txt.w(tr, s) > max) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ---------- main render ----------
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        ClientConfig c = ClientConfig.get();
        Font tr = mc.font;
        frames++;
        long nowNs = System.nanoTime();
        if (nowNs - fpsStamp >= 1_000_000_000L) { fps = frames; frames = 0; fpsStamp = nowNs; }
        int W = g.guiWidth(), H = g.guiHeight();
        int accent = c.accent;
        int dim = (accent & 0x00FFFFFF) | 0x55000000;

        HudLayout.begin(g);
        ModHud.render(g, mc, c);

        // ---- top bar: [FPS] [dot  time  current lyric line] [signal  ping] ----
        if (c.showTopBar) {
            int h = 20, gap = 6;
            String fpsT = fps + " FPS";
            int ping = 0;
            var conn = mc.getConnection();
            if (conn != null && mc.player != null) {
                var info = conn.getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = info.getLatency();
            }
            String pingT = ping + " ms";
            int fpsW = Txt.w(tr, fpsT) + 18, pingW = 12 + Txt.w(tr, pingT) + 18;
            boolean lp = c.showNowPlaying;
            int midW = lp ? Math.min(250, (int) (W * 0.42f)) : 0;
            int total = fpsW + gap + pingW + (lp ? gap + midW : 0);
            int[] pos = HudLayout.place("topbar", total, h, (W - total) / 2, 5, W, H);
            int x = pos[0], y = pos[1];
            panelR(g, x, y, fpsW, h, h / 2, accent);
            Txt.draw(g, tr, fpsT, x + 9, y + (h - 8) / 2, 0xFF86F7B4, false);
            x += fpsW + gap;
            if (lp) {
                panelR(g, x, y, midW, h, h / 2, accent);
                nowPill(g, mc, x, y, midW, h, accent);
                x += midW + gap;
            }
            panelR(g, x, y, pingW, h, h / 2, accent);
            int sig = ping < 80 ? 0xFF4ADE80 : ping < 150 ? 0xFFFACC15 : 0xFFF87171;
            for (int b = 0; b < 3; b++) Gfx.roundF(g, x + 9 + b * 3.4f, y + h / 2f + 3 - (2 + b * 2), 2.2f, 2 + b * 2, 1f, sig);
            Txt.draw(g, tr, pingT, x + 9 + 12, y + (h - 8) / 2, 0xFFEDEAF8, false);
        }

        // ---- speed ----
        if (c.showSpeed) {
            int[] pos = HudLayout.place("speed", 100, 32, 8, 34, W, H);
            panel(g, pos[0], pos[1], 100, 32, accent);
            Txt.draw(g, tr, String.format("Speed - %.1f b/s", speed), pos[0] + 8, pos[1] + 5, 0xFFFFFFFF, true);
            spark(g, pos[0] + 8, pos[1] + 17, 84, 10, speedHist, 12f, accent);
        }

        // ---- music card ----
        if (c.showMusic) {
            int mw = 180, mh = 50;
            int[] pos = HudLayout.place("music", mw, mh, (W - mw) / 2, 33, W, H);
            int mx = pos[0], my = pos[1];
            panelR(g, mx, my, mw, mh, 9, accent);
            Gfx.roundV(g, mx + 7, my + 8, 34, 34, 8, tint(accent, 0.95f, 0xFF), tint(accent, 0.45f, 0xFF));
            Icons.note(g, mx + 7 + 13, my + 8 + 11, 0xFFFFFFFF);
            int tx = mx + 49, tw = mw - 57;
            Txt.draw(g, tr, fit(tr, MusicPlayer.title(), tw), tx, my + 7, 0xFFFFFFFF, true);
            Txt.draw(g, tr, fit(tr, MusicPlayer.artist(), tw), tx, my + 17, 0xFFB4B4BC, false);
            int mid = tx + tw / 2;
            Icons.prev(g, mid - 22, my + 28, 0xFFFFFFFF);
            if (MusicPlayer.isPlaying()) Icons.pause(g, mid - 3, my + 27, 0xFFFFFFFF); else Icons.play(g, mid - 3, my + 27, 0xFFFFFFFF);
            Icons.next(g, mid + 14, my + 28, 0xFFFFFFFF);
            int by = my + 41;
            Gfx.roundF(g, tx, by, tw, 3, 1.5f, (accent & 0x00FFFFFF) | 0x44000000);
            float prog = MusicPlayer.progress();
            Gfx.roundF(g, tx, by, Math.max(3, tw * prog), 3, 1.5f, accent);
            Gfx.circle(g, tx + Math.max(1.5f, tw * prog), by + 1.5f, 2.6f, 0xFFFFFFFF);
        }

        // ---- coordinates ----
        if (c.showCoords && mc.player != null) {
            String s = String.format("x %d   y %d   z %d", (int) Math.floor(mc.player.getX()),
                    (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
            int w = Txt.w(tr, s) + 14 + 14, h = 17;
            int[] pos = HudLayout.place("coords", w, h, (W - w) / 2, 87, W, H);
            panelR(g, pos[0], pos[1], w, h, h / 2, accent);
            Icons.arrow(g, pos[0] + 9, pos[1] + 4, accent);
            Txt.draw(g, tr, s, pos[0] + 21, pos[1] + 5, 0xFFFFFFFF, true);
        }

        // ---- RAM ----
        if (c.showMemory) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L, max = rt.maxMemory() / 1048576L;
            int[] pos = HudLayout.place("ram", 100, 32, W - 108, 34, W, H);
            panel(g, pos[0], pos[1], 100, 32, accent);
            Txt.draw(g, tr, "RAM Usage - " + (used * 100 / max) + "%", pos[0] + 8, pos[1] + 5, 0xFFFFFFFF, true);
            spark(g, pos[0] + 8, pos[1] + 17, 84, 10, memHist, 1f, accent);
        }
        HudLayout.flush();
    }
}
