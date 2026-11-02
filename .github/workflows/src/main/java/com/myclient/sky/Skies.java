package com.myclient.sky;

import com.myclient.ClientConfig;
import com.myclient.hud.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Method;
import java.util.Random;

/**
 * Animated skies drawn above the horizon, plus an "atmosphere" colour grade over the whole screen and an optional
 * client-side time of day. The sky is an overlay (not a real skybox): the horizon follows your camera, and clouds
 * and stars are fixed to compass directions so they stay put when you turn. It fades out indoors and in other dimensions.
 */
public final class Skies {
    public static final String[] NAMES = { "Nebula", "Aurora", "Galaxy", "End Beams", "Underwater", "Meteor Shower", "Star Trails", "Sunset",
            "Thunderstorm", "Blood Moon", "Comet", "Solar Eclipse", "Neon Horizon", "Cherry Blossom", "Saturn", "Void", "Realistic" };
    public static final int REALISTIC = 16;
    /** order in the menu: Realistic first */
    public static final int[] ORDER = { 16, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
    public static final int COUNT = NAMES.length;

    /** set by the menu while hovering a card, so you see the sky live before choosing it */
    public static volatile int previewId = -1;
    /** 0 unknown, 1 time override works, -1 not supported on this version */
    public static volatile int timeStatus = 0;

    //                          top         bottom      haze        atmosphere
    private static final int[][] COL = {
            { 0xFF0A0420, 0xFF3A1580, 0xFF8A4CFF, 0xFF3A1580 },   // nebula
            { 0xFF03101A, 0xFF104030, 0xFF38FFA8, 0xFF0A3A2C },   // aurora
            { 0xFF050818, 0xFF202A70, 0xFF4C5CFF, 0xFF101850 },   // galaxy
            { 0xFF0B0614, 0xFF2A0F40, 0xFFB04CFF, 0xFF2A0F40 },   // end beams
            { 0xFF052A3A, 0xFF1D8FA0, 0xFF40D0D0, 0xFF0A4A5A },   // underwater
            { 0xFF040612, 0xFF18204A, 0xFF6070FF, 0xFF0A1030 },   // meteor shower
            { 0xFF03040C, 0xFF121838, 0xFF5060C0, 0xFF0A0E24 },   // star trails
            { 0xFF2A1858, 0xFFFF8A4C, 0xFFFFB070, 0xFF603018 },   // sunset
            { 0xFF1C212B, 0xFF444A58, 0xFF8A93A8, 0xFF202632 },   // thunderstorm
            { 0xFF1A0306, 0xFF5A0A12, 0xFFFF3030, 0xFF3A0508 },   // blood moon
            { 0xFF040818, 0xFF14204A, 0xFF6080FF, 0xFF0A1230 },   // comet
            { 0xFF05060E, 0xFF303A58, 0xFFFFA860, 0xFF101420 },   // solar eclipse
            { 0xFF140428, 0xFFE0309A, 0xFFFF60C0, 0xFF50105A },   // neon horizon
            { 0xFFD08AB8, 0xFFFFD6E8, 0xFFFFE0F0, 0xFF8A3A62 },   // cherry blossom
            { 0xFF05061A, 0xFF1C1040, 0xFF6A4CC0, 0xFF140A30 },   // saturn
            { 0xFF000000, 0xFF14081E, 0xFF5A2A8A, 0xFF08040C },   // void
            { 0xFF2A6CC8, 0xFFB8D4F0, 0xFFFFD6A0, 0xFF203050 } }; // realistic
    private static final int[] STARS = { 80, 60, 90, 40, 0, 90, 0, 20, 0, 40, 90, 20, 30, 0, 80, 30, 0 };
    private static final float[] TOPA = { 0.82f, 0.82f, 0.82f, 0.85f, 0.55f, 0.82f, 0.85f, 0.62f, 0.88f, 0.80f, 0.82f, 0.86f, 0.78f, 0.50f, 0.82f, 0.92f, 0.5f };
    private static final float[] BOTA = { 0.18f, 0.16f, 0.18f, 0.20f, 0.35f, 0.18f, 0.16f, 0.60f, 0.45f, 0.30f, 0.18f, 0.40f, 0.55f, 0.45f, 0.18f, 0.20f, 0.3f };

    private static final int N = 90, B = 16;
    private static final float[] sAz = new float[N], sEl = new float[N], sPh = new float[N];
    private static final float[][] BLOB = new float[B][5];   // az, el, widthDeg, heightDeg, colour index
    private static float k, halfW, halfH, thetaUp, yawDeg, fade;
    private static long lastNs = System.nanoTime();

    static {
        Random r = new Random(7);
        for (int i = 0; i < N; i++) { sAz[i] = r.nextFloat() * 360f - 180f; sEl[i] = 2f + r.nextFloat() * 82f; sPh[i] = r.nextFloat() * 6.28f; }
        for (int i = 0; i < B; i++) {
            BLOB[i][0] = r.nextFloat() * 340f - 170f; BLOB[i][1] = 8f + r.nextFloat() * 52f;
            BLOB[i][2] = 40f + r.nextFloat() * 50f;  BLOB[i][3] = 8f + r.nextFloat() * 14f;
            BLOB[i][4] = r.nextInt(3);
        }
    }

    private static float wrap(float d) { return ((d + 180f) % 360f + 360f) % 360f - 180f; }

    private static final float[] camR = new float[3], camU = new float[3], camF = new float[3];

    private static void setupCamera(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        float sy = (float) Math.sin(y), cy = (float) Math.cos(y), sp = (float) Math.sin(p), cp = (float) Math.cos(p);
        camF[0] = -sy * cp; camF[1] = -sp;     camF[2] = cy * cp;
        camR[0] = -cy;      camR[1] = 0;       camR[2] = -sy;
        camU[0] = -sy * sp; camU[1] = cp;      camU[2] = cy * sp;
    }

    /** world direction -> screen position. false if it is behind you. */
    private static boolean projDir(float dx, float dy, float dz, float[] out) {
        float zc = dx * camF[0] + dy * camF[1] + dz * camF[2];
        if (zc < 0.06f) return false;
        float xc = dx * camR[0] + dy * camR[1] + dz * camR[2];
        float yc = dx * camU[0] + dy * camU[1] + dz * camU[2];
        out[0] = halfW + xc / zc * k;
        out[1] = halfH - yc / zc * k;
        return true;
    }

    private static boolean proj(float az, float el, float[] out) {
        double a = Math.toRadians(az), e = Math.toRadians(el);
        float ce = (float) Math.cos(e);
        return projDir((float) (-Math.sin(a)) * ce, (float) Math.sin(e), (float) Math.cos(a) * ce, out);
    }

    // ---------------------------------------------------------------- client side time of day
    public static void tickTime(Minecraft mc) {
        ClientConfig c = ClientConfig.get();
        if (!(c.skyOn || previewId >= 0) || c.skyTime <= 0 || c.skyTime > 4 || mc.level == null || timeStatus == -1) return;
        long[] t = { 0, 6000, 12700, 14500, 18000 };
        Object lvl = mc.level;
        try { lvl.getClass().getMethod("setDayTime", long.class).invoke(lvl, t[c.skyTime]); timeStatus = 1; return; } catch (Throwable ignored) {}
        try { lvl.getClass().getMethod("setTimeFromServer", long.class, long.class, boolean.class).invoke(lvl, mc.level.getGameTime(), t[c.skyTime], false); timeStatus = 1; return; } catch (Throwable ignored) {}
        try {
            Object data = lvl.getClass().getMethod("getLevelData").invoke(lvl);
            for (Method m : data.getClass().getMethods()) {
                if (m.getName().equals("setDayTime") && m.getParameterCount() == 1) { m.setAccessible(true); m.invoke(data, t[c.skyTime]); timeStatus = 1; return; }
            }
        } catch (Throwable ignored) {}
        timeStatus = -1;
    }

    // ---------------------------------------------------------------- shared pieces
    private static void stars(GuiGraphicsExtractor g, float hy, int count, float t, float in) {
        float[] o = new float[2];
        for (int i = 0; i < count && i < N; i++) {
            if (!proj(sAz[i], sEl[i], o) || o[1] > hy - 6) continue;
            float tw = 0.55f + 0.45f * (float) Math.sin(t * 1.7f + sPh[i]);
            int sz = i % 9 == 0 ? 2 : 1;
            g.fill((int) o[0], (int) o[1], (int) o[0] + sz, (int) o[1] + sz, Gfx.a(0xFFFFFFFF, tw * in * 0.9f));
        }
    }

    private static void pills(GuiGraphicsExtractor g, float hy, float t, int[] cols, float alpha, float scale, float ppd) {
        float[] o = new float[2];
        for (int i = 0; i < B; i++) {
            float az = BLOB[i][0] + (float) Math.sin(t * 0.05f + i) * 4f;
            if (!proj(az, BLOB[i][1], o) || o[1] > hy - 8) continue;
            float w = BLOB[i][2] * ppd * scale, h = BLOB[i][3] * ppd * scale;
            int col = cols[(int) BLOB[i][4] % cols.length];
            Gfx.roundF(g, o[0] - w / 2, o[1] - h / 2, w, h, h / 2, Gfx.a(col, alpha));
            Gfx.roundF(g, o[0] - w * 0.3f, o[1] - h * 0.3f, w * 0.6f, h * 0.6f, h * 0.3f, Gfx.a(col, alpha * 1.1f));
        }
    }

    // ---------------------------------------------------------------- main render
    public static void render(GuiGraphicsExtractor g) {
        ClientConfig c = ClientConfig.get();
        int id = previewId >= 0 ? previewId : (c.skyOn ? c.skyId : -1);
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        if (id < 0 || id >= COUNT || mc.player == null || mc.level == null) { fade = Math.max(0f, fade - dt * 3f); return; }

        // only in the overworld and only when the sky is actually above you (no more sky inside caves and houses)
        boolean overworld = String.valueOf(mc.level.dimension()).contains("overworld");
        boolean sees = overworld && mc.level.canSeeSky(mc.player.blockPosition());
        fade += ((sees ? 1f : 0f) - fade) * Math.min(1f, dt * 4f);
        if (fade < 0.02f) return;

        int W = g.guiWidth(), H = g.guiHeight();
        float fov = mc.options.fov().get();
        halfW = W / 2f; halfH = H / 2f;
        k = (float) (halfH / Math.tan(Math.toRadians(fov / 2.0)));
        float pitch = Math.max(-88f, Math.min(88f, mc.player.getXRot()));
        thetaUp = -pitch;
        yawDeg = mc.player.getYRot();
        setupCamera(yawDeg, pitch);
        float hy = halfH + (float) Math.tan(Math.toRadians(thetaUp)) * k;
        float t = c.skyAnimate ? now / 1e9f : 0f;
        float in = c.skyIntensity * fade, atm = c.skyAtmosphere * fade;
        float ppd = (float) (k * Math.PI / 180.0);
        int[] col = COL[id];

        if (id == REALISTIC) { realistic(g, W, H, hy, (int) Math.min(Math.max(hy, 0), H), t, in, ppd, mc, c); return; }

        // atmosphere: a colour grade over the whole picture so the world matches the sky
        if (atm > 0.01f) g.fillGradient(0, 0, W, H, Gfx.a(col[3], 0.15f * atm), Gfx.a(col[3], 0.22f * atm));

        if (hy < 6) return;
        int hyi = (int) Math.min(hy, H);

        // sky body, then a soft fade that continues just below the horizon so there is no hard line
        g.fillGradient(0, 0, W, hyi, Gfx.a(col[0], TOPA[id] * in), Gfx.a(col[1], BOTA[id] * in));
        if (hyi < H) g.fillGradient(0, hyi, W, Math.min(H, hyi + 34), Gfx.a(col[1], BOTA[id] * in), Gfx.a(col[1], 0f));
        // horizon haze on both sides of the horizon
        int hh = Math.max(10, (int) (hyi * 0.32f));
        g.fillGradient(0, hyi - hh, W, hyi, Gfx.a(col[2], 0f), Gfx.a(col[2], 0.30f * in));
        if (hyi < H) g.fillGradient(0, hyi, W, Math.min(H, hyi + 26), Gfx.a(col[2], 0.30f * in), Gfx.a(col[2], 0f));

        if (STARS[id] > 0) stars(g, hy, STARS[id], t, in * (id == 7 ? 0.6f : 1f));

        float[] o = new float[2];
        switch (id) {
            case 0 -> pills(g, hy, t, new int[] { 0xFF7A3CFF, 0xFFC04CFF, 0xFF4C5CFF }, 0.11f * in, 1.0f, ppd);
            case 1 -> aurora(g, W, hy, t, in, ppd);
            case 2 -> {
                for (int j = -9; j <= 9; j++) {
                    float el = 26f + 18f * (float) Math.sin(j * 0.35);
                    if (!proj(j * 18f, el, o) || o[1] > hy - 8) continue;
                    float w = 36 * ppd, h = 12 * ppd;
                    Gfx.roundF(g, o[0] - w / 2, o[1] - h / 2, w, h, h / 2, Gfx.a(j % 2 == 0 ? 0xFF5C6CFF : 0xFFD36CFF, 0.09f * in));
                    Gfx.roundF(g, o[0] - w / 4, o[1] - h / 4, w / 2, h / 2, h / 4, Gfx.a(0xFFFFFFFF, 0.05f * in));
                }
            }
            case 3 -> {
                for (int i = 0; i < 9; i++) {
                    if (!proj(-170f + i * 42f, 10f, o)) continue;
                    float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.8f + i);
                    int x = (int) o[0];
                    g.fillGradient(x - 3, 0, x + 3, hyi, Gfx.a(0xFFB04CFF, 0f), Gfx.a(0xFFD070FF, 0.34f * in * pulse));
                    g.fillGradient(x - 1, 0, x + 1, hyi, Gfx.a(0xFFFFFFFF, 0f), Gfx.a(0xFFFFFFFF, 0.25f * in * pulse));
                }
            }
            case 4 -> {
                for (int i = 0; i < 12; i++) {
                    int x = (int) (i * W / 12f + Math.sin(t * 0.4 + i) * 10);
                    g.fillGradient(x, 0, x + 14, hyi, Gfx.a(0xFFB0F0FF, 0.10f * in), Gfx.a(0xFFB0F0FF, 0f));
                }
                for (int i = 0; i < 18; i++) {
                    float bx = (((i * 47 + (float) Math.sin(t + i) * 6) % W) + W) % W;
                    float by = hy - ((t * 25f * (0.5f + (i % 4) * 0.25f) + i * 31) % Math.max(10f, hy));
                    g.fill((int) bx, (int) by, (int) bx + 2, (int) by + 2, Gfx.a(0xFFE0FFFF, 0.35f * in));
                }
            }
            case 5 -> {
                for (int m = 0; m < 5; m++) {
                    float ph = (t / (5f + m * 1.3f) + m * 0.37f) % 1f;
                    if (ph > 0.30f || !proj(-70f + m * 35f, 55f - m * 6f, o)) continue;
                    float u = ph / 0.30f, sx = o[0] - u * 90f, sy = o[1] + u * 60f;
                    for (int j = 0; j < 14; j++) {
                        float fx = sx + j * 2.5f, fy = sy - j * 1.7f;
                        if (fy > hy - 4) continue;
                        g.fill((int) fx, (int) fy, (int) fx + 2, (int) fy + 2, Gfx.a(0xFFFFFFFF, (1f - j / 14f) * 0.9f * in * (1f - u * 0.5f)));
                    }
                }
            }
            case 6 -> {
                float px = halfW + wrap(0f - yawDeg) * ppd, py = halfH - (48f - thetaUp) * ppd;
                for (int i = 0; i < 44; i++) {
                    float r = 18f + ((i * 37) % 170), th = i * 2.39996f + t * 0.04f;
                    for (int j = 0; j < 6; j++) {
                        float a2 = th - j * 0.045f;
                        float sx = px + (float) Math.cos(a2) * r, sy = py + (float) Math.sin(a2) * r * 0.85f;
                        if (sy > hy - 4 || sy < 0 || sx < 0 || sx > W) continue;
                        g.fill((int) sx, (int) sy, (int) sx + 1, (int) sy + 1, Gfx.a(0xFFDDE6FF, (1f - j / 6f) * 0.8f * in));
                    }
                }
            }
            case 7 -> {
                if (proj(0f, 8f, o) && o[1] < hy - 12) {
                    float r = 6f * ppd;
                    Gfx.circle(g, o[0], o[1], r * 2.4f, Gfx.a(0xFFFF9A5C, 0.08f * in));
                    Gfx.circle(g, o[0], o[1], r * 1.6f, Gfx.a(0xFFFFB070, 0.14f * in));
                    Gfx.circle(g, o[0], o[1], r, Gfx.a(0xFFFFE0A0, in));
                }
                for (int i = 0; i < 10; i++) {
                    if (!proj(-150f + i * 35f + (float) Math.sin(t * 0.04f + i) * 3f, 5f + (i * 7) % 12, o) || o[1] > hy - 3) continue;
                    float w = 30 * ppd, h = 3.5f * ppd;
                    Gfx.roundF(g, o[0] - w / 2, o[1] - h / 2, w, h, h / 2, Gfx.a(i % 2 == 0 ? 0xFFFF9A5C : 0xFFE06A8A, 0.20f * in));
                }
            }
            case 8 -> {
                pills(g, hy, t * 3f, new int[] { 0xFF10131A, 0xFF2A303C, 0xFF3A404E }, 0.22f * in, 1.3f, ppd);
                for (int i = 0; i < 50; i++) {
                    float rx = (((i * 37 + t * 40f) % W) + W) % W, ry = (t * 520f * (0.7f + (i % 5) * 0.1f) + i * 61) % H;
                    g.fill((int) rx, (int) ry, (int) rx + 1, (int) ry + 7, Gfx.a(0xFFAAB8D0, 0.30f * in));
                }
                if (t % 11f < 0.14f) g.fillGradient(0, 0, W, hyi, Gfx.a(0xFFFFFFFF, 0.28f * in), Gfx.a(0xFFFFFFFF, 0f));
            }
            case 9 -> {
                if (proj(-20f, 32f, o) && o[1] < hy - 20) {
                    float r = 6.5f * ppd;
                    Gfx.circle(g, o[0], o[1], r * 1.9f, Gfx.a(0xFFFF3030, 0.06f * in));
                    Gfx.circle(g, o[0], o[1], r * 1.4f, Gfx.a(0xFFFF3030, 0.10f * in));
                    Gfx.circle(g, o[0], o[1], r, Gfx.a(0xFFC02020, in));
                    Gfx.circle(g, o[0] - r * 0.3f, o[1] - r * 0.2f, r * 0.28f, Gfx.a(0xFF8A1010, 0.8f * in));
                    Gfx.circle(g, o[0] + r * 0.35f, o[1] + r * 0.3f, r * 0.2f, Gfx.a(0xFF8A1010, 0.8f * in));
                }
            }
            case 10 -> {
                float az = -35f + (float) Math.sin(t * 0.02f) * 5f;
                if (proj(az, 34f, o) && o[1] < hy - 8) {
                    for (int j = 22; j >= 1; j--) {
                        float fx = o[0] - j * 5f, fy = o[1] + j * 1.7f;
                        if (fy > hy - 4) continue;
                        g.fill((int) fx, (int) fy, (int) fx + 2, (int) fy + 2, Gfx.a(0xFF9CC4FF, (1f - j / 23f) * 0.6f * in));
                    }
                    Gfx.circle(g, o[0], o[1], 3.4f, Gfx.a(0xFFFFFFFF, in));
                    Gfx.circle(g, o[0], o[1], 6f, Gfx.a(0xFF9CC4FF, 0.25f * in));
                }
            }
            case 11 -> {
                if (proj(15f, 45f, o) && o[1] < hy - 20) {
                    float r = 5.4f * ppd;
                    Gfx.circle(g, o[0], o[1], r * 1.9f, Gfx.a(0xFFFFFFFF, 0.06f * in));
                    Gfx.circle(g, o[0], o[1], r * 1.5f, Gfx.a(0xFFFFFFFF, 0.10f * in));
                    Gfx.circle(g, o[0], o[1], r * 1.08f, Gfx.a(0xFFFFFFFF, 0.85f * in));
                    Gfx.circle(g, o[0], o[1], r, 0xFF000000);
                }
            }
            case 12 -> {
                if (proj(0f, 10f, o) && o[1] < hy - 12) {
                    float r = 9f * ppd;
                    Gfx.circle(g, o[0], o[1], r * 1.6f, Gfx.a(0xFFFF60C0, 0.14f * in));
                    Gfx.roundV(g, Math.round(o[0] - r), Math.round(o[1] - r), Math.round(2 * r), Math.round(2 * r), Math.round(r),
                            Gfx.a(0xFFFFE066, in), Gfx.a(0xFFFF4DB8, in));
                    for (int j = 0; j < 5; j++) {
                        float yy = o[1] + r * 0.08f + j * r * 0.17f;
                        if (yy >= o[1] + r) break;
                        g.fill(Math.round(o[0] - r), (int) yy, Math.round(o[0] + r), (int) (yy + 1 + j * 0.7f), Gfx.a(0xFFB0207A, 0.9f * in));
                    }
                }
            }
            case 13 -> {
                for (int i = 0; i < 34; i++) {
                    float px = (((i * 53 + t * (8 + (i % 3) * 4) + (float) Math.sin(t * 0.7 + i) * 10) % W) + W) % W;
                    float py = (t * (14 + (i % 4) * 5) + i * 37) % H;
                    g.fill((int) px, (int) py, (int) px + 3, (int) py + 2, Gfx.a(0xFFFFB0CC, 0.85f * in));
                    g.fill((int) px + 1, (int) py + 2, (int) px + 2, (int) py + 3, Gfx.a(0xFFFF80AA, 0.7f * in));
                }
            }
            case 14 -> {
                if (proj(30f, 26f, o) && o[1] < hy - 20) {
                    float r = 7f * ppd;
                    Gfx.roundF(g, o[0] - r * 2.3f, o[1] - r * 0.28f, r * 4.6f, r * 0.56f, r * 0.28f, Gfx.a(0xFFD9B27A, 0.55f * in));
                    Gfx.circle(g, o[0], o[1], r, Gfx.a(0xFF9A6A3A, in));
                    Gfx.circle(g, o[0] - r * 0.15f, o[1] - r * 0.15f, r * 0.85f, Gfx.a(0xFFE9C58F, in));
                    Gfx.roundF(g, o[0] - r * 2.3f, o[1] - r * 0.02f, r * 4.6f, r * 0.30f, r * 0.15f, Gfx.a(0xFFD9B27A, 0.9f * in));
                }
                if (proj(-40f, 40f, o) && o[1] < hy - 10) Gfx.circle(g, o[0], o[1], 2.2f * ppd, Gfx.a(0xFFB8B8D0, 0.8f * in));
            }
            default -> { }   // void: just darkness and a few stars
        }
    }

    // elevation keyframes: { sun elevation in degrees, r, g, b } or { elevation, alpha }
    private static final float[][] HZ = { { 25, .72f, .86f, 1f }, { 10, .98f, .85f, .62f }, { 3, 1f, .62f, .32f }, { -1, .95f, .45f, .35f },
            { -5, .72f, .36f, .50f }, { -9, .40f, .30f, .55f }, { -14, .14f, .17f, .34f }, { -20, .06f, .08f, .18f } };
    private static final float[][] ZN = { { 25, .20f, .42f, .82f }, { 8, .22f, .36f, .68f }, { 0, .18f, .25f, .52f }, { -6, .10f, .12f, .34f },
            { -12, .04f, .06f, .20f }, { -20, .01f, .02f, .08f } };
    private static final float[][] AH = { { 25, 0 }, { 18, .10f }, { 8, .30f }, { 1, .52f }, { -4, .45f }, { -10, .25f }, { -16, .10f }, { -24, .06f } };
    private static final float[][] AZ = { { 25, 0 }, { 10, .03f }, { 0, .16f }, { -6, .26f }, { -12, .30f }, { -24, .30f } };
    private static final float[][] CLD = new float[8][3];   // az, el, size
    public static volatile int timeRead = 0;                 // 1 = the real time of day is read, -1 = not possible

    static {
        Random r = new Random(21);
        for (int i = 0; i < CLD.length; i++) { CLD[i][0] = r.nextFloat() * 360f - 180f; CLD[i][1] = 16f + r.nextFloat() * 36f; CLD[i][2] = 0.8f + r.nextFloat() * 0.8f; }
    }

    private static float[] key(float e, float[][] K) {
        int n = K[0].length - 1;
        float[] out = new float[n];
        if (e >= K[0][0]) { System.arraycopy(K[0], 1, out, 0, n); return out; }
        for (int i = 0; i < K.length - 1; i++) {
            if (e >= K[i + 1][0]) {
                float t = (K[i][0] - e) / (K[i][0] - K[i + 1][0]);
                for (int j = 0; j < n; j++) out[j] = K[i][j + 1] + (K[i + 1][j + 1] - K[i][j + 1]) * t;
                return out;
            }
        }
        System.arraycopy(K[K.length - 1], 1, out, 0, n);
        return out;
    }

    private static int rgb(float[] c) { return 0xFF000000 | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255); }

    private static long readDayTime(Minecraft mc) {
        Object lvl = mc.level;
        for (String n : new String[] { "getDayTime", "getOverworldClockTime", "getTimeOfDay" }) {
            try {
                Object v = lvl.getClass().getMethod(n).invoke(lvl);
                if (v instanceof Number num) { timeRead = 1; return num.longValue(); }
            } catch (Throwable ignored) {}
        }
        timeRead = -1;
        return 6000;
    }

    private static void glowDisc(GuiGraphicsExtractor g, float dx, float dy, float dz, float radiusDeg, int color, float alpha, float ppd) {
        float[] o = new float[2];
        if (!projDir(dx, dy, dz, o)) return;
        float r = Math.min(120f, radiusDeg * ppd);
        Gfx.circle(g, o[0], o[1], r, Gfx.a(color, alpha));
    }

    /** Sky that follows the real time of day: blue day, golden hour, sunset, twilight, night with Milky Way. */
    private static void realistic(GuiGraphicsExtractor g, int W, int H, float hy, int hyi, float t, float in, float ppd, Minecraft mc, ClientConfig c) {
        long dayTime = c.skyTime > 0 && c.skyTime <= 4 ? new long[] { 0, 6000, 12500, 15500, 18000 }[c.skyTime] : readDayTime(mc);
        double ang = (dayTime % 24000L) / 24000.0 * Math.PI * 2;
        float sx = (float) Math.cos(ang), sy = (float) Math.sin(ang);
        float e = (float) Math.toDegrees(Math.asin(sy));          // sun elevation
        float[] hc = key(e, HZ), zc = key(e, ZN);
        int hcol = rgb(hc), zcol = rgb(zc);
        float ah = key(e, AH)[0] * in, az = key(e, AZ)[0] * in;
        float night = Math.max(0f, Math.min(1f, (-e - 4f) / 12f));
        float sunset = Math.max(0f, Math.min(1f, 1f - Math.abs(e - 2f) / 14f));

        if (hy >= 6) {
            // colour of the sky; at noon this is almost invisible, so the normal blue stays
            g.fillGradient(0, 0, W, hyi, Gfx.a(zcol, az), Gfx.a(hcol, ah));
            if (hyi < H) g.fillGradient(0, hyi, W, Math.min(H, hyi + 40), Gfx.a(hcol, ah * 0.85f), Gfx.a(hcol, 0f));
            // aerial haze at the horizon
            float hazeA = 0.10f * in + 0.5f * ah;
            int hh = Math.max(10, (int) (hyi * 0.22f));
            g.fillGradient(0, hyi - hh, W, hyi, Gfx.a(hcol, 0f), Gfx.a(hcol, hazeA));
            if (hyi < H) g.fillGradient(0, hyi, W, Math.min(H, hyi + 24), Gfx.a(hcol, hazeA), Gfx.a(hcol, 0f));

            // warm glow on the side of the sky where the sun is
            if (e > -9f && e < 20f) {
                float[] o = new float[2];
                float hx = sx >= 0 ? 1f : -1f;
                float flick = 0.94f + 0.06f * (float) Math.sin(t * 0.35f);
                if (projDir(hx, 0.05f, 0f, o) && o[1] < hy + 4) {
                    float cy = Math.min(o[1], hy - 2);
                    for (int i = 0; i < 3; i++) {
                        float w = (70 - i * 22) * ppd, h = (15 - i * 4) * ppd * 0.35f;
                        Gfx.roundF(g, o[0] - w / 2, cy - h, w, h, h / 2, Gfx.a(hcol, (0.10f + 0.07f * i) * sunset * in * flick));
                    }
                }
            }
            // sun bloom
            if (sy > -0.05f) {
                float warm = Math.max(0f, Math.min(1f, 1f - e / 28f));
                int bloom = Gfx.lerp(0xFFFFF4D8, 0xFFFFA860, warm);
                glowDisc(g, sx, sy, 0f, 13f, bloom, 0.05f * in, ppd);
                glowDisc(g, sx, sy, 0f, 7f, bloom, 0.08f * in, ppd);
                glowDisc(g, sx, sy, 0f, 3f, bloom, 0.12f * in, ppd);
            }
            // moon halo
            if (-sy > 0.02f) {
                glowDisc(g, -sx, -sy, 0f, 10f, 0xFFCFD8FF, 0.05f * night * in, ppd);
                glowDisc(g, -sx, -sy, 0f, 4.5f, 0xFFE8EEFF, 0.09f * night * in, ppd);
            }
        }

        // soft drifting clouds (high up only, so they do not paint over hills)
        float[] o = new float[2];
        int cc = Gfx.lerp(0xFFFFFFFF, hcol, Math.min(1f, sunset * 1.1f));
        cc = Gfx.lerp(cc, 0xFF4A5878, night);
        float ca = (0.13f - 0.07f * night) * in;
        for (int i = 0; i < CLD.length; i++) {
            float az0 = CLD[i][0] + t * 0.35f;
            if (!proj(az0, CLD[i][1], o) || o[1] > hy - 12) continue;
            float s0 = CLD[i][2], w = 34 * ppd * s0, h = 6.5f * ppd * s0;
            Gfx.roundF(g, o[0] - w / 2, o[1] - h / 2, w, h, h / 2, Gfx.a(cc, ca));
            Gfx.roundF(g, o[0] - w * 0.30f, o[1] - h * 0.95f, w * 0.55f, h * 0.9f, h * 0.45f, Gfx.a(cc, ca * 0.9f));
            Gfx.roundF(g, o[0] + w * 0.05f, o[1] - h * 0.75f, w * 0.38f, h * 0.7f, h * 0.35f, Gfx.a(cc, ca * 0.8f));
        }

        // night: more stars, Milky Way band, shooting stars
        if (night > 0.05f && hy >= 6) {
            stars(g, hy, (int) (N * night), t, in * night);
            for (int i = -5; i <= 5; i++) {
                float s1 = i / 5f, az1 = s1 * 130f, el1 = 62f - 48f * s1 * s1;
                if (!proj(az1 + 40f, el1, o) || o[1] > hy - 10) continue;
                float w = 30 * ppd, h = 7.5f * ppd;
                Gfx.roundF(g, o[0] - w / 2, o[1] - h / 2, w, h, h / 2, Gfx.a(0xFFB8C4FF, 0.050f * night * in));
                Gfx.roundF(g, o[0] - w / 3, o[1] - h / 4, w * 0.66f, h * 0.5f, h * 0.25f, Gfx.a(0xFFF2F0FF, 0.055f * night * in));
            }
            float ph = (t / 9f) % 1f;
            if (ph < 0.10f) {
                int seed = (int) (t / 9f);
                float u = ph / 0.10f;
                if (proj(((seed * 97) % 200) - 100f, 38f + (seed * 31) % 25, o)) {
                    float sx0 = o[0] - u * 70f, sy0 = o[1] + u * 40f;
                    for (int j = 0; j < 12; j++) {
                        float fx = sx0 + j * 2.6f, fy = sy0 - j * 1.5f;
                        if (fy > hy - 4) continue;
                        g.fill((int) fx, (int) fy, (int) fx + 2, (int) fy + 2, Gfx.a(0xFFFFFFFF, (1f - j / 12f) * 0.9f * night * in));
                    }
                }
            }
        }
    }

    private static void aurora(GuiGraphicsExtractor g, int W, float hy, float t, float in, float ppd) {
        for (int r = 0; r < 2; r++) {
            int col = r == 0 ? 0xFF38FFA8 : 0xFF9B5CFF;
            for (int x = 0; x < W; x += 8) {
                float xw = x + yawDeg * ppd;
                float wave = (float) (Math.sin(xw * 0.018 + t * 0.7 + r * 1.9) * 14 + Math.sin(xw * 0.045 + t * 0.4) * 6);
                float mid = hy * (0.30f + 0.10f * r) + wave, half = 38 + 14 * r;
                int y1 = (int) Math.max(0, mid - half), y2 = (int) mid, y3 = (int) Math.min(hy - 2, mid + half * 0.7f);
                if (y3 <= y2) continue;
                g.fillGradient(x, y1, x + 9, y2, Gfx.a(col, 0f), Gfx.a(col, 0.36f * in));
                g.fillGradient(x, y2, x + 9, y3, Gfx.a(col, 0.36f * in), Gfx.a(col, 0f));
            }
        }
    }

    // ---------------------------------------------------------------- small thumbnails for the menu
    public static void drawPreview(GuiGraphicsExtractor g, int id, int x, int y, int w, int h) {
        int[] c = COL[id];
        int top = Gfx.lerp(c[0], 0xFF000000, id == 13 || id == 7 || id == 4 ? 0f : 0.15f);
        Gfx.roundV(g, x, y, w, h, 5, top | 0xFF000000, c[1] | 0xFF000000);
        int cx = x + w / 2, by = y + h - 10;
        if (STARS[id] > 0 || id == 6 || id == 5) {
            for (int i = 0; i < 9; i++) g.fill(x + 4 + (i * 37) % Math.max(4, w - 8), y + 3 + (i * 53) % Math.max(4, h / 2), x + 5 + (i * 37) % Math.max(4, w - 8), y + 4 + (i * 53) % Math.max(4, h / 2), 0xAAFFFFFF);
        }
        switch (id) {
            case 0 -> Gfx.roundF(g, x + w * 0.2f, y + h * 0.25f, w * 0.6f, h * 0.22f, h * 0.11f, 0x66C04CFF);
            case 1 -> { for (int i = 0; i < 7; i++) g.fillGradient(x + 6 + i * (w - 12) / 7, y + 4, x + 9 + i * (w - 12) / 7, y + h - 10, 0x0038FFA8, 0x8838FFA8); }
            case 2 -> Gfx.roundF(g, x + w * 0.1f, y + h * 0.3f, w * 0.8f, h * 0.2f, h * 0.1f, 0x555C6CFF);
            case 3 -> { for (int i = 0; i < 4; i++) g.fillGradient(x + 8 + i * (w - 16) / 4, y + 2, x + 10 + i * (w - 16) / 4, y + h - 6, 0x00D070FF, 0xAAD070FF); }
            case 5 -> { for (int j = 0; j < 8; j++) g.fill(x + w - 12 - j * 3, y + 5 + j * 2, x + w - 10 - j * 3, y + 7 + j * 2, Gfx.a(0xFFFFFFFF, 1f - j / 8f)); }
            case 7 -> Gfx.circle(g, cx, by, 7, 0xFFFFE0A0);
            case 9 -> Gfx.circle(g, cx + 6, y + h * 0.4f, 6, 0xFFC02020);
            case 10 -> { for (int j = 0; j < 10; j++) g.fill(x + 8 + j * 4, y + h - 14 - j * 2, x + 10 + j * 4, y + h - 12 - j * 2, Gfx.a(0xFF9CC4FF, 0.3f + j / 14f)); }
            case 11 -> { Gfx.circle(g, cx, y + h * 0.42f, 7, 0xAAFFFFFF); Gfx.circle(g, cx, y + h * 0.42f, 5.6f, 0xFF000000); }
            case 12 -> Gfx.roundV(g, cx - 8, by - 8, 16, 16, 8, 0xFFFFE066, 0xFFFF4DB8);
            case 13 -> { for (int i = 0; i < 8; i++) g.fill(x + 5 + (i * 29) % Math.max(4, w - 10), y + 4 + (i * 17) % Math.max(4, h - 12), x + 8 + (i * 29) % Math.max(4, w - 10), y + 6 + (i * 17) % Math.max(4, h - 12), 0xFFFFB0CC); }
            case 14 -> { Gfx.roundF(g, cx - 12, y + h * 0.4f - 1.5f, 24, 3, 1.5f, 0xAAD9B27A); Gfx.circle(g, cx, y + h * 0.4f, 6, 0xFFE9C58F); }
            case 16 -> { g.fillGradient(x + 2, y + h - 14, x + w - 2, y + h - 2, 0x00FFC080, 0xAAFFC080); Gfx.circle(g, x + w * 0.70f, y + h * 0.36f, 5, 0xFFFFF0C0); }
            default -> { }
        }
    }
}
