package com.myclient.gui;

import com.myclient.ClientConfig;
import com.myclient.ScreenUtil;
import com.myclient.hud.Gfx;
import com.myclient.hud.HudRenderer;
import com.myclient.hud.Icons;
import com.myclient.hud.Txt;
import com.myclient.mods.ModsManager;
import com.myclient.music.MusicPlayer;
import com.myclient.music.NowPlaying;
import com.myclient.sky.CursorRenderer;
import com.myclient.sky.CursorStyle;
import com.myclient.sky.Skies;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Sidebar menu in a violet glass style. Right Shift opens it. */
public class ClickGuiScreen extends Screen {
    private static final int SB = 100;
    private static final String[] PAGES = { "Overview", "Skies", "Music", "HUD", "PvP", "Mods", "Utility", "Cursor", "Settings" };
    private static final String[] SUBS = { "Welcome to Spared", "Pick your own animated sky.", "What is playing", "Move and resize overlays", "Combat tools",
            "Your installed mods", "Quality of life", "Pointer and aim", "Colors and options" };
    private static final int[] COLORS = { 0xFF8B5CF6, 0xFFE8B931, 0xFF60A5FA, 0xFF2DD4BF, 0xFF4ADE80, 0xFFFB7185, 0xFFFB923C, 0xFFE879F9 };
    private static final int TXT = 0xFFF3EFFF;

    private record Hit(int x, int y, int w, int h, DoubleConsumer fn, boolean slider) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record Feature(String cat, String name, String desc, String id, Supplier<Boolean> get, Consumer<Boolean> set, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Feature> features = new ArrayList<>();
    private final HashMap<String, Float> anims = new HashMap<>();
    private Hit dragging;
    private static int page = 0, skyTab = 0;
    private List<ModsManager.Entry> mods;
    private String modMsg = "";
    private int scroll, skyScroll, mx, my, x0, y0, pw, ph;
    private long lastNs = System.nanoTime(), openNs = System.nanoTime();
    private float dt;

    public ClickGuiScreen() {
        super(Component.literal("Client"));
        buildFeatures();
    }

    private static ClientConfig c() { return ClientConfig.get(); }

    private void add(String cat, String name, String desc, String id, Supplier<Boolean> g, Consumer<Boolean> s) {
        features.add(new Feature(cat, name, desc, id, g, s, null));
    }

    private void action(String cat, String name, String desc, Runnable r) {
        features.add(new Feature(cat, name, desc, null, null, null, r));
    }

    private void buildFeatures() {
        action("HUD", "Edit layout", "Drag every HUD element where you want it", () -> ScreenUtil.open(new HudEditorScreen()));
        action("HUD", "Reset layout", "Put positions and sizes back to default", () -> { c().hudPos.clear(); c().hudScale.clear(); });
        add("HUD", "Top bar", "FPS, song and ping", "topbar", () -> c().showTopBar, v -> c().showTopBar = v);
        add("HUD", "Song in top bar", "Show what is playing in the middle pill", null, () -> c().showNowPlaying, v -> c().showNowPlaying = v);
        add("HUD", "Music card", "Now playing with controls", "music", () -> c().showMusic, v -> c().showMusic = v);
        add("HUD", "Speed", "Blocks per second with a graph", "speed", () -> c().showSpeed, v -> c().showSpeed = v);
        add("HUD", "Coordinates", "Your position", "coords", () -> c().showCoords, v -> c().showCoords = v);
        add("HUD", "RAM usage", "Memory with a graph", "ram", () -> c().showMemory, v -> c().showMemory = v);
        add("HUD", "Direction", "Facing and angle", "direction", () -> c().showDirection, v -> c().showDirection = v);
        add("HUD", "Health / Food", "Health and hunger numbers", "stats", () -> c().showStats, v -> c().showStats = v);
        add("HUD", "Held item", "Item in hand and its durability", "held", () -> c().showHeld, v -> c().showHeld = v);
        add("HUD", "Ping box", "Separate latency box", "ping", () -> c().showPing, v -> c().showPing = v);
        add("HUD", "Session timer", "How long you have played", "session", () -> c().showSession, v -> c().showSession = v);
        add("HUD", "Sprint status", "Sprinting / sneaking / walking", "status", () -> c().showStatus, v -> c().showStatus = v);

        add("PvP", "Keystrokes", "WASD, mouse buttons and space", "keys", () -> c().showKeys, v -> c().showKeys = v);
        add("PvP", "CPS counter", "Clicks per second", "cps", () -> c().showCps, v -> c().showCps = v);
        add("PvP", "Combo counter", "Hits in a row without taking damage", "combo", () -> c().showCombo, v -> c().showCombo = v);
        add("PvP", "Target HUD", "Name, health bar and distance of your target", "target", () -> c().showTarget, v -> c().showTarget = v);
        add("PvP", "Reach", "Distance of your last entity hit", "reach", () -> c().showReach, v -> c().showReach = v);
        add("PvP", "Armor icons", "Armor with icons and durability bars", "armoricons", () -> c().showArmorIcons, v -> c().showArmorIcons = v);
        add("PvP", "Armor status", "Armor durability as text", "armor", () -> c().showArmor, v -> c().showArmor = v);
        add("PvP", "Potion timer", "Effects with countdown", "potions", () -> c().showPotions, v -> c().showPotions = v);
        add("PvP", "Item counter", "Gapples, pearls, totems, pots, crystals", "items", () -> c().showItems, v -> c().showItems = v);
        add("PvP", "Saturation", "Gold bars under the hunger icons", null, () -> c().showSaturation, v -> c().showSaturation = v);
        add("PvP", "Low health warning", "Pulsing red edges when you are low", null, () -> c().lowHealthWarn, v -> c().lowHealthWarn = v);
        add("PvP", "No hurt cam", "No camera shake when you take damage", null, () -> c().noHurtCam, v -> c().noHurtCam = v);
        add("PvP", "No death animation", "Dead players vanish instantly", null, () -> c().noDeathAnim, v -> c().noDeathAnim = v);

        add("Utility", "Toggle sprint", "Sprint automatically while moving forward", null, () -> c().toggleSprint, v -> c().toggleSprint = v);
        add("Utility", "Fullbright", "See in the dark (client side)", null, () -> c().fullbright, v -> c().fullbright = v);
        add("Cursor", "Custom crosshair", "Replace the in-game crosshair", null, () -> c().customCrosshair, v -> c().customCrosshair = v);
    }

    // ---------- lifecycle ----------
    @Override
    protected void init() {
        openNs = System.nanoTime();
        mods = null;
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public void removed() {
        Skies.previewId = -1;
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        ClientConfig.get().save();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------- theme ----------
    private int ui() { return c().accent; }
    private int bg(float k) { return HudRenderer.tint(ui(), k, 0xFF); }
    private int sub() { return Gfx.lerp(ui(), 0xFF9C98AA, 0.65f); }

    private float anim(String key, float target) {
        float v = anims.getOrDefault(key, target);
        v += (target - v) * Math.min(1f, dt * 16f);
        if (Math.abs(target - v) < 0.003f) v = target;
        anims.put(key, v);
        return v;
    }

    private boolean in(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private String fit(String s, int max) {
        while (s.length() > 1 && Txt.w(font, s) > max) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float hv) {
        Gfx.border(g, x, y, w, h, r, Gfx.a(ui(), 0.26f + 0.2f * hv), Gfx.lerp(bg(0.24f), bg(0.32f), hv), Gfx.lerp(bg(0.17f), bg(0.23f), hv));
    }

    private void toggle(GuiGraphicsExtractor g, int x, int y, boolean on, String key) {
        float t = anim("tg" + key, on ? 1f : 0f);
        Gfx.roundV(g, x, y, 28, 14, 7, Gfx.lerp(bg(0.38f), Gfx.lerp(ui(), 0xFFFFFFFF, 0.2f), t), Gfx.lerp(bg(0.30f), ui(), t));
        Gfx.circle(g, x + 7 + 14 * t, y + 7.4f, 5.2f, 0x33000000);
        Gfx.circle(g, x + 7 + 14 * t, y + 7, 5f, 0xFFFFFFFF);
    }

    private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean active, Runnable r) {
        float hv = anim("bt" + x + "," + y + "," + page, in(x, y, w, h) ? 1f : 0f);
        int top = active ? Gfx.lerp(ui(), 0xFFFFFFFF, 0.18f) : Gfx.lerp(bg(0.22f), bg(0.34f), hv);
        int bot = active ? ui() : Gfx.lerp(bg(0.15f), bg(0.25f), hv);
        Gfx.border(g, x, y, w, h, 7, active ? 0x00000000 : Gfx.a(ui(), 0.30f), top, bot);
        Txt.draw(g, font, label, x + (w - Txt.w(font, label)) / 2, y + (h - 8) / 2, active ? 0xFFFFFFFF : TXT, false);
        hits.add(new Hit(x, y, w, h, d -> r.run(), false));
    }

    private void segmented(GuiGraphicsExtractor g, int x, int y, int w, int h, String[] labels, int sel, IntConsumer set) {
        Gfx.border(g, x, y, w, h, h / 2, Gfx.a(ui(), 0.25f), bg(0.12f), bg(0.09f));
        int sw = w / labels.length;
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            float sv = anim("sg" + x + "," + y + "," + i, i == sel ? 1f : 0f);
            if (sv > 0.01f) Gfx.roundV(g, x + i * sw + 2, y + 2, sw - 4, h - 4, (h - 4) / 2, Gfx.a(Gfx.lerp(ui(), 0xFFFFFFFF, 0.18f), sv), Gfx.a(ui(), sv));
            Txt.draw(g, font, labels[i], x + i * sw + (sw - Txt.w(font, labels[i])) / 2, y + (h - 8) / 2, i == sel ? 0xFFFFFFFF : sub(), false);
            hits.add(new Hit(x + i * sw, y, sw, h, d -> set.accept(idx), false));
        }
    }

    private void slider(GuiGraphicsExtractor g, String label, String valueText, int x, int y, int w, float min, float max, float val, Consumer<Float> set) {
        Txt.draw(g, font, label, x, y, TXT, false);
        Txt.draw(g, font, valueText, x + w - Txt.w(font, valueText), y, sub(), false);
        int ty = y + 14;
        Gfx.roundF(g, x, ty, w, 4, 2, bg(0.34f));
        float fw = w * (val - min) / (max - min);
        Gfx.roundV(g, x, ty, Math.max(4, (int) fw), 4, 2, Gfx.lerp(ui(), 0xFFFFFFFF, 0.25f), ui());
        Gfx.circle(g, x + Math.max(2f, fw), ty + 2.6f, 6f, 0x33000000);
        Gfx.circle(g, x + Math.max(2f, fw), ty + 2f, 5.2f, 0xFFFFFFFF);
        hits.add(new Hit(x, ty - 6, w, 16, d -> {
            float t = (float) Math.max(0, Math.min(1, (d - x) / w));
            set.accept(min + t * (max - min));
        }, true));
    }

    private void dots(GuiGraphicsExtractor g, int x, int y, int[] palette, float r, int spacing, int current, Consumer<Integer> set) {
        for (int i = 0; i < palette.length; i++) {
            float cx = x + i * spacing + r, cy = y + r;
            int col = palette[i];
            if (col == current) Gfx.circle(g, cx, cy, r + 1.8f, 0xFFFFFFFF);
            Gfx.circle(g, cx, cy, col == current ? r - 0.2f : r, col);
            hits.add(new Hit((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r), d -> set.accept(col), false));
        }
    }

    /** small [-] 1.0x [+] size control, 78 px wide */
    private void sizeControl(GuiGraphicsExtractor g, int x, int y, String id) {
        float sc = c().scaleOf(id);
        Gfx.circle(g, x + 8, y + 8, 8f, bg(0.40f));
        Txt.draw(g, font, "-", x + 8 - Txt.w(font, "-") / 2, y + 4, TXT, false);
        hits.add(new Hit(x, y, 16, 16, d -> c().setScale(id, c().scaleOf(id) - 0.1f), false));
        String t = String.format("%.1fx", sc);
        Txt.draw(g, font, t, x + 39 - Txt.w(font, t) / 2, y + 4, Math.abs(sc - 1f) < 0.01f ? sub() : 0xFFFFFFFF, false);
        hits.add(new Hit(x + 17, y, 44, 16, d -> c().setScale(id, 1f), false));
        Gfx.circle(g, x + 70, y + 8, 8f, bg(0.40f));
        Txt.draw(g, font, "+", x + 70 - Txt.w(font, "+") / 2, y + 4, TXT, false);
        hits.add(new Hit(x + 62, y, 16, 16, d -> c().setScale(id, c().scaleOf(id) + 0.1f), false));
    }

    private void drawRow(GuiGraphicsExtractor g, Feature f, int x, int y, int w) {
        float hv = anim("row" + f.name() + page, in(x, y, w, 28) ? 1f : 0f);
        card(g, x, y, w, 28, 8, hv);
        int textW = w - 100 - (f.id() != null ? 82 : 0);
        Txt.draw(g, font, f.name(), x + 12, y + 6, TXT, false);
        Txt.draw(g, font, fit(f.desc(), textW), x + 12, y + 16, sub(), false);
        if (f.action() != null) {
            Gfx.roundV(g, x + w - 56, y + 6, 46, 16, 8, Gfx.lerp(ui(), 0xFFFFFFFF, 0.2f), ui());
            Txt.draw(g, font, "Open", x + w - 56 + (46 - Txt.w(font, "Open")) / 2, y + 10, 0xFFFFFFFF, false);
            hits.add(new Hit(x, y, w, 28, d -> f.action().run(), false));
        } else {
            boolean on = f.get().get();
            toggle(g, x + w - 42, y + 7, on, f.name());
            hits.add(new Hit(x + w - 90, y, 90, 28, d -> f.set().accept(!on), false));
            hits.add(new Hit(x, y, w - 90 - (f.id() != null ? 82 : 0), 28, d -> f.set().accept(!on), false));
            if (f.id() != null) sizeControl(g, x + w - 42 - 10 - 78, y + 6, f.id());
        }
    }

    private int featureRows(GuiGraphicsExtractor g, int x, int y, int w, String cat, int visible, boolean scrollable) {
        List<Feature> list = new ArrayList<>();
        for (Feature f : features) if (f.cat().equals(cat)) list.add(f);
        int start = 0;
        if (scrollable) {
            start = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible)));
            scroll = start;
        }
        boolean needArrows = scrollable && list.size() > visible;
        int rw = w - (needArrows ? 20 : 0);
        int rows = Math.min(visible, list.size() - start);
        for (int i = 0; i < rows; i++) drawRow(g, list.get(start + i), x, y + i * 31, rw);
        if (needArrows) {
            button(g, "^", x + w - 16, y, 16, 16, false, () -> scroll--);
            button(g, "v", x + w - 16, y + visible * 31 - 19, 16, 16, false, () -> scroll++);
        }
        return y + rows * 31;
    }

    // ---------- main render ----------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hits.clear();
        mx = mouseX; my = mouseY;
        Skies.previewId = -1;
        long now = System.nanoTime();
        dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        float open = Math.min(1f, (now - openNs) / 200_000_000f);
        float ease = 1f - (1f - open) * (1f - open) * (1f - open);

        ClientConfig c = c();
        int ui = ui();
        pw = Math.min(470, width - 12);
        ph = Math.min(262, height - 8);
        x0 = (width - pw) / 2;
        y0 = (height - ph) / 2 + (int) ((1f - ease) * 14f);

        // lighter dimming on the Skies page so you can see the sky you hover
        float dim = page == 1 ? 0.45f : 1f;
        g.fillGradient(0, 0, width, height, Gfx.a(0x55000000, ease * dim), Gfx.a(0xAA000000, ease * dim));
        Gfx.shadow(g, x0, y0, pw, ph, 12, ui, 0x40, 4, 3);
        Gfx.roundV2(g, x0 * 2, y0 * 2, pw * 2, ph * 2, 24, Gfx.a(ui, 0.70f) | 0xFF000000, Gfx.a(ui, 0.35f) | 0xFF000000);
        Gfx.roundV2(g, x0 * 2 + 1, y0 * 2 + 1, pw * 2 - 2, ph * 2 - 2, 23, bg(0.085f), bg(0.06f));
        Gfx.roundV2(g, (x0 + SB) * 2, y0 * 2 + 1, (pw - SB) * 2 - 1, ph * 2 - 2, 23, bg(0.16f), bg(0.10f));
        Gfx.fill2(g, (x0 + SB) * 2, y0 * 2 + 1, (x0 + SB + 14) * 2, (y0 + 14) * 2, bg(0.16f));
        Gfx.fill2(g, (x0 + SB) * 2, (y0 + ph - 14) * 2, (x0 + SB + 14) * 2, y0 * 2 + ph * 2 - 1, bg(0.10f));
        Gfx.fill2(g, (x0 + SB) * 2, (y0 + 12) * 2, (x0 + SB) * 2 + 1, (y0 + ph - 12) * 2, Gfx.a(ui, 0.28f));

        // brand
        Gfx.roundV(g, x0 + 9, y0 + 9, 26, 26, 8, Gfx.lerp(ui, 0xFFFFFFFF, 0.3f), ui);
        Icons.king(g, x0 + 11, y0 + 11, 0xFFFFFFFF);
        Txt.draw(g, font, "Spared", x0 + 41, y0 + 12, 0xFFFFFFFF, true);
        Txt.draw(g, font, "Client 26.2", x0 + 41, y0 + 23, sub(), false);

        // pages
        for (int i = 0; i < PAGES.length; i++) {
            final int idx = i;
            int ey = y0 + 46 + i * 21;
            boolean sel = page == i;
            float sv = anim("pg" + i, sel ? 1f : 0f);
            float hv = anim("ph" + i, in(x0 + 6, ey, SB - 12, 19) ? 1f : 0f);
            if (sv > 0.01f) {
                Gfx.roundV(g, x0 + 7, ey, SB - 14, 19, 7, Gfx.a(ui, 0.40f * sv), Gfx.a(ui, 0.20f * sv));
                Gfx.roundF(g, x0 + 7, ey + 4, 2.2f, 11, 1.1f, Gfx.a(ui, sv));
            } else if (hv > 0.01f) {
                Gfx.round(g, x0 + 7, ey, SB - 14, 19, 7, Gfx.a(ui, 0.10f * hv));
            }
            Txt.draw(g, font, PAGES[i], x0 + 18, ey + 5, sel ? 0xFFFFFFFF : 0xFFD0CBE6, false);
            hits.add(new Hit(x0 + 6, ey, SB - 12, 19, d -> { page = idx; scroll = 0; skyScroll = 0; }, false));
        }
        Txt.draw(g, font, "Spared v1.7", x0 + 12, y0 + ph - 14, Gfx.a(sub(), 0.7f), false);

        // header
        int cx = x0 + SB + 14, cw = pw - SB - 28, cy = y0 + 44;
        Txt.draw(g, font, PAGES[page], cx, y0 + 11, 0xFFFFFFFF, true);
        Txt.draw(g, font, SUBS[page], cx, y0 + 23, sub(), false);
        String tag = "Your game, your rules.";
        int tw = Txt.w(font, tag);
        Gfx.roundF(g, cx + cw - tw - 8, y0 + 12, 1.6f, 18, 0.8f, ui);
        Txt.draw(g, font, tag, cx + cw - tw, y0 + 17, TXT, false);
        Gfx.fill2(g, cx * 2, (y0 + 37) * 2, (cx + cw) * 2, (y0 + 37) * 2 + 1, Gfx.a(ui, 0.35f));

        switch (page) {
            case 0 -> overviewPage(g, cx, cy, cw);
            case 1 -> skiesPage(g, cx, cy - 4, cw);
            case 2 -> musicPage(g, cx, cy, cw);
            case 3 -> featureRows(g, cx, cy, cw, "HUD", 6, true);
            case 4 -> featureRows(g, cx, cy, cw, "PvP", 6, true);
            case 5 -> modsPage(g, cx, cy, cw);
            case 6 -> utilityPage(g, cx, cy, cw);
            case 7 -> cursorPage(g, cx, cy, cw);
            default -> settingsPage(g, cx, cy, cw);
        }

        CursorRenderer.draw(g, mouseX, mouseY, c.cursor, c.cursorColor, c.cursorSize);
    }

    // ---------- Overview ----------
    private void planet(GuiGraphicsExtractor g, float cx, float cy, float s) {
        Gfx.roundF(g, cx - 30 * s, cy - 4 * s, 60 * s, 8 * s, 4 * s, Gfx.a(0xFFD9B27A, 0.55f));
        Gfx.circle(g, cx, cy, 13 * s, 0xFF9A6A3A);
        Gfx.circle(g, cx - 2 * s, cy - 2 * s, 11 * s, 0xFFE9C58F);
        Gfx.roundF(g, cx - 30 * s, cy - 0.5f * s, 60 * s, 4 * s, 2 * s, Gfx.a(0xFFD9B27A, 0.9f));
    }

    private void overviewPage(GuiGraphicsExtractor g, int x, int y, int w) {
        int bh = 50;
        Gfx.border(g, x, y, w, bh, 10, Gfx.a(ui(), 0.40f), bg(0.34f), bg(0.18f));
        Gfx.roundV(g, x + 10, y + 9, 32, 32, 9, Gfx.lerp(ui(), 0xFFFFFFFF, 0.3f), ui());
        Icons.king(g, x + 15, y + 14, 0xFFFFFFFF);
        Txt.draw(g, font, "Spared", x + 52, y + 12, 0xFFFFFFFF, true);
        Txt.draw(g, font, "More than just a client.", x + 52, y + 25, sub(), false);
        planet(g, x + w - 56, y + 25, 0.85f);

        String[][] cards = { { "Skies", "Animated skies", "1" }, { "Music", "What is playing", "2" }, { "HUD", "Move and resize", "3" },
                { "PvP", "Combat tools", "4" }, { "Mods", "Your installed mods", "5" }, { "Settings", "Colors, options", "8" } };
        int cw3 = (w - 12) / 3, ch = 36, cy = y + bh + 8;
        for (int i = 0; i < cards.length; i++) {
            int cx = x + (i % 3) * (cw3 + 6), yy = cy + (i / 3) * (ch + 6);
            final int target = Integer.parseInt(cards[i][2]);
            float hv = anim("ov" + i, in(cx, yy, cw3, ch) ? 1f : 0f);
            card(g, cx, yy, cw3, ch, 9, hv);
            Gfx.circle(g, cx + 15, yy + ch / 2f, 8.5f, Gfx.a(ui(), 0.30f + 0.2f * hv));
            Txt.draw(g, font, cards[i][0].substring(0, 1), cx + 15 - Txt.w(font, cards[i][0].substring(0, 1)) / 2, yy + ch / 2 - 4, 0xFFFFFFFF, false);
            Txt.draw(g, font, cards[i][0], cx + 29, yy + 8, TXT, false);
            Txt.draw(g, font, fit(cards[i][1], cw3 - 34), cx + 29, yy + 19, sub(), false);
            hits.add(new Hit(cx, yy, cw3, ch, d -> { page = target; scroll = 0; }, false));
        }
        nowPlaying(g, x, cy + 2 * (ch + 6) + 2, w);
    }

    private void nowPlaying(GuiGraphicsExtractor g, int x, int y, int w) {
        int h = 44;
        card(g, x, y, w, h, 9, 0f);
        Gfx.roundV(g, x + 8, y + 7, 30, 30, 8, Gfx.lerp(ui(), 0xFFFFFFFF, 0.25f), Gfx.lerp(ui(), 0xFF000000, 0.35f));
        Icons.note(g, x + 8 + 11, y + 7 + 9, 0xFFFFFFFF);
        boolean has = NowPlaying.has();
        String t = has ? NowPlaying.title() : "Nothing playing";
        String a = has ? NowPlaying.artist() : (MusicPlayer.isSystem() ? "Play a song in Spotify or YouTube Music" : "Add songs in the Music tab");
        Txt.draw(g, font, fit(t, w - 150), x + 46, y + 6, TXT, true);
        Txt.draw(g, font, fit(a == null ? "" : a, w - 150), x + 46, y + 17, sub(), false);
        if (has) {
            long p = NowPlaying.positionMs() / 1000, d = NowPlaying.durationMs() / 1000;
            String tm = (p / 60) + ":" + String.format("%02d", p % 60) + (d > 0 ? " / " + (d / 60) + ":" + String.format("%02d", d % 60) : "");
            Txt.draw(g, font, tm, x + 46, y + 29, sub(), false);
        }
        int cxr = x + w - 14, cyy = y + 17;
        Gfx.circle(g, cxr - 52, cyy, 8f, bg(0.38f));
        Icons.prev(g, cxr - 52 - 4, cyy - 3, 0xFFFFFFFF);
        hits.add(new Hit(cxr - 60, cyy - 8, 16, 16, d -> MusicPlayer.prev(), false));
        Gfx.circle(g, cxr - 28, cyy, 10f, ui());
        if (MusicPlayer.isPlaying()) Icons.pause(g, cxr - 28 - 3, cyy - 4, 0xFFFFFFFF); else Icons.play(g, cxr - 28 - 3, cyy - 4, 0xFFFFFFFF);
        hits.add(new Hit(cxr - 38, cyy - 10, 20, 20, d -> MusicPlayer.toggle(), false));
        Gfx.circle(g, cxr - 4, cyy, 8f, bg(0.38f));
        Icons.next(g, cxr - 4 - 3, cyy - 3, 0xFFFFFFFF);
        hits.add(new Hit(cxr - 12, cyy - 8, 16, 16, d -> MusicPlayer.next(), false));
        long dur = NowPlaying.durationMs();
        float prog = dur > 0 ? Math.min(1f, NowPlaying.positionMs() / (float) dur) : 0f;
        Gfx.roundF(g, x + 46, y + h - 8, w - 58, 3, 1.5f, bg(0.40f));
        Gfx.roundF(g, x + 46, y + h - 8, Math.max(3, (w - 58) * prog), 3, 1.5f, ui());
    }

    // ---------- Skies (grid like the reference: Sky on + Skies/Options tabs) ----------
    private void skiesPage(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        float on = anim("skyon", c.skyOn ? 1f : 0f);
        Gfx.border(g, x, y, 100, 22, 11, Gfx.a(ui(), 0.30f), bg(0.20f), bg(0.14f));
        toggle(g, x + 8, y + 4, c.skyOn, "skyon");
        Txt.draw(g, font, c.skyOn ? "Sky on" : "Sky off", x + 42, y + 7, on > 0.5f ? 0xFFFFFFFF : sub(), false);
        hits.add(new Hit(x, y, 100, 22, d -> c().skyOn = !c().skyOn, false));
        segmented(g, x + w - 150, y, 150, 22, new String[] { "Skies", "Options" }, skyTab, v -> skyTab = v);
        int gy = y + 30;
        if (skyTab == 0) skyGrid(g, x, gy, w); else skyOptions(g, x, gy, w);
    }

    private void skyGrid(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        int cols = 4, gap = 6, gridW = w - 20, cw = (gridW - gap * (cols - 1)) / cols, ch = 52, visibleRows = 3;
        int totalRows = (Skies.COUNT + cols - 1) / cols;
        skyScroll = Math.max(0, Math.min(skyScroll, Math.max(0, totalRows - visibleRows)));
        int hover = -1;
        for (int r = 0; r < visibleRows; r++) {
            for (int col = 0; col < cols; col++) {
                int i = (skyScroll + r) * cols + col;
                if (i >= Skies.COUNT) break;
                final int idx = i;
                int cx = x + col * (cw + gap), cy = y + r * (ch + 5);
                boolean sel = c.skyOn && c.skyId == i, hov = in(cx, cy, cw, ch);
                if (hov) hover = i;
                float hv = anim("sk" + i, hov ? 1f : 0f);
                Gfx.border(g, cx, cy, cw, ch, 9, sel ? ui() : Gfx.a(ui(), 0.25f + 0.2f * hv), Gfx.lerp(bg(0.24f), bg(0.32f), hv), bg(0.16f));
                Skies.drawPreview(g, i, cx + 4, cy + 4, cw - 8, 32);
                if (hov) {
                    int pw2 = Txt.w(font, "Preview") + 14;
                    Gfx.roundF(g, cx + 8, cy + 8, pw2, 12, 6, 0x99000000);
                    Gfx.circle(g, cx + 14, cy + 14, 1.8f, 0xFFFFFFFF);
                    Txt.draw(g, font, "Preview", cx + 19, cy + 10, 0xFFFFFFFF, false);
                }
                Txt.draw(g, font, Skies.NAMES[i], cx + 7, cy + ch - 12, sel ? 0xFFFFFFFF : TXT, false);
                hits.add(new Hit(cx, cy, cw, ch, d -> { c().skyId = idx; c().skyOn = true; }, false));
            }
        }
        Skies.previewId = hover;
        if (totalRows > visibleRows) {
            button(g, "^", x + w - 16, y, 16, 16, false, () -> skyScroll--);
            button(g, "v", x + w - 16, y + visibleRows * (ch + 5) - 21, 16, 16, false, () -> skyScroll++);
        }
    }

    private void skyOptions(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        int half = (w - 12) / 2;
        slider(g, "Intensity", String.format("%d%%", Math.round(c.skyIntensity * 100)), x, y, half, 0.2f, 1.2f, c.skyIntensity, v -> c().skyIntensity = v);
        slider(g, "Atmosphere", String.format("%d%%", Math.round(c.skyAtmosphere * 100)), x + half + 12, y, half, 0f, 1f, c.skyAtmosphere, v -> c().skyAtmosphere = v);
        Txt.draw(g, font, "Time of day", x, y + 36, TXT, false);
        segmented(g, x, y + 48, w, 22, new String[] { "Auto", "Day", "Dusk", "Night", "Midnight" }, c.skyTime, v -> c().skyTime = v);
        button(g, "Animation: " + (c.skyAnimate ? "ON" : "OFF"), x, y + 80, 130, 20, c.skyAnimate, () -> c().skyAnimate = !c().skyAnimate);
        String ts = Skies.timeStatus == -1 ? "Time of day is not supported on this version (the sky still works)."
                : "Atmosphere tints the whole picture. Time of day only changes what you see.";
        Txt.draw(g, font, ts, x, y + 110, sub(), false);
        Txt.draw(g, font, "Skies fade out in caves, indoors, the Nether and the End.", x, y + 122, sub(), false);
    }

    // ---------- Music ----------
    private void musicPage(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        button(g, "|<", x, y, 40, 20, false, MusicPlayer::prev);
        button(g, MusicPlayer.isPlaying() ? "Pause" : "Play", x + 46, y, 64, 20, MusicPlayer.isPlaying(), MusicPlayer::toggle);
        button(g, ">|", x + 116, y, 40, 20, false, MusicPlayer::next);
        button(g, "Rescan", x + 162, y, 56, 20, false, () -> { MusicPlayer.rescan(); scroll = 0; });
        button(g, c.systemMusic ? "Windows media" : "Local files", x + 224, y, Math.min(110, w - 224), 20, c.systemMusic, () -> {
            c().systemMusic = !c().systemMusic;
            MusicPlayer.setSystem(c().systemMusic);
        });
        if (c.systemMusic) {
            Txt.draw(g, font, "Shows what plays on your PC: Spotify, or YouTube Music", x, y + 34, TXT, false);
            Txt.draw(g, font, "in Chrome/Edge (music.youtube.com). The buttons above", x, y + 46, TXT, false);
            Txt.draw(g, font, "are your keyboard media keys. Volume: use the player.", x, y + 58, sub(), false);
            Txt.draw(g, font, "Windows only. Next song: F6 (rebind in Controls).", x, y + 70, sub(), false);
            return;
        }
        Txt.draw(g, font, "Songs: .minecraft/myclient_music  (.mp3 / .wav)", x, y + 26, sub(), false);
        slider(g, "Volume", String.format("%d%%", Math.round(c.musicVolume * 100)), x, y + 44, w, 0f, 1f, c.musicVolume, v -> { c().musicVolume = v; MusicPlayer.setVolume(v); });
        List<String> names = MusicPlayer.names();
        if (names.isEmpty()) {
            Txt.draw(g, font, "No songs found yet.", x, y + 84, TXT, true);
            return;
        }
        int start = Math.max(0, Math.min(scroll, Math.max(0, names.size() - 4)));
        scroll = start;
        for (int i = 0; i < 4; i++) {
            final int k = start + i;
            if (k >= names.size()) break;
            button(g, fit(names.get(k), w - 50), x, y + 76 + i * 22, w - 24, 20, k == MusicPlayer.currentIndex(), () -> MusicPlayer.play(k));
        }
        button(g, "^", x + w - 20, y + 76, 20, 20, false, () -> scroll--);
        button(g, "v", x + w - 20, y + 100, 20, 20, false, () -> scroll++);
    }

    // ---------- Mods (everything in your mods folder) ----------
    private void modsPage(GuiGraphicsExtractor g, int x, int y, int w) {
        if (mods == null) mods = ModsManager.list();
        int visible = 6;
        int start = Math.max(0, Math.min(scroll, Math.max(0, mods.size() - visible)));
        scroll = start;
        boolean arrows = mods.size() > visible;
        int rw = w - (arrows ? 20 : 0);
        for (int i = 0; i < visible && start + i < mods.size(); i++) {
            ModsManager.Entry e = mods.get(start + i);
            int yy = y + i * 31;
            float hv = anim("md" + e.id(), in(x, yy, rw, 28) ? 1f : 0f);
            card(g, x, yy, rw, 28, 8, hv);
            Gfx.circle(g, x + 16, yy + 14, 9f, Gfx.a(e.enabled() ? ui() : 0xFF6B6B78, 0.35f));
            String l = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase();
            Txt.draw(g, font, l, x + 16 - Txt.w(font, l) / 2, yy + 10, 0xFFFFFFFF, false);
            Txt.draw(g, font, fit(e.name(), rw - 120), x + 32, yy + 6, e.enabled() ? TXT : sub(), false);
            Txt.draw(g, font, fit("v" + e.version() + "  -  " + e.id(), rw - 120), x + 32, yy + 16, sub(), false);
            if (e.locked()) {
                Txt.draw(g, font, "Required", x + rw - 12 - Txt.w(font, "Required"), yy + 10, sub(), false);
            } else {
                toggle(g, x + rw - 42, yy + 7, e.enabled(), "md" + e.id());
                hits.add(new Hit(x, yy, rw, 28, d -> { modMsg = ModsManager.setEnabled(e, !e.enabled()); mods = ModsManager.list(); }, false));
            }
        }
        if (arrows) {
            button(g, "^", x + w - 16, y, 16, 16, false, () -> scroll--);
            button(g, "v", x + w - 16, y + visible * 31 - 19, 16, 16, false, () -> scroll++);
        }
        String note = !modMsg.isEmpty() ? modMsg : (mods.isEmpty() ? "No other mods found." : "A mod turns off when you close the game. Turning it on again works the same way.");
        Txt.draw(g, font, fit(note, w), x, y0 + ph - 13, !modMsg.isEmpty() ? 0xFFFFFFFF : Gfx.a(sub(), 0.8f), false);
    }

    // ---------- Utility / Cursor / Settings ----------
    private void utilityPage(GuiGraphicsExtractor g, int x, int y, int w) {
        int ny = featureRows(g, x, y, w, "Utility", 6, false);
        slider(g, "Zoom FOV (hold C)", String.format("%.0f", c().zoomFov), x, ny + 10, w, 30f, 90f, c().zoomFov, v -> c().zoomFov = v);
        Txt.draw(g, font, "Rebind zoom in Options > Controls", x, ny + 40, sub(), false);
    }

    private void cursorPage(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        Txt.draw(g, font, "Style", x, y, TXT, false);
        CursorStyle[] all = CursorStyle.values();
        int bw = (w - 3 * 6) / 4;
        for (int i = 0; i < all.length; i++) {
            CursorStyle s = all[i];
            button(g, s.label, x + i * (bw + 6), y + 12, bw, 20, c.cursor == s, () -> c().cursor = s);
        }
        Txt.draw(g, font, "Color", x, y + 40, TXT, false);
        dots(g, x, y + 54, new int[] { 0xFFFFFFFF, 0xFFE8B931, 0xFF8B5CF6, 0xFF60A5FA, 0xFF2DD4BF, 0xFF4ADE80, 0xFFFB7185, 0xFFFB923C }, 8f, 26, c.cursorColor, col -> c().cursorColor = col);
        slider(g, "Size", String.format("%.1fx", c.cursorSize), x, y + 82, w, 0.5f, 3f, c.cursorSize, v -> c().cursorSize = v);
        int ny = featureRows(g, x, y + 112, w, "Cursor", 2, false);
        Txt.draw(g, font, "Preview:", x, ny + 10, sub(), false);
        CursorRenderer.draw(g, x + 60, ny + 14, c.cursor, c.cursorColor, c.cursorSize);
    }

    private void settingsPage(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        Txt.draw(g, font, "Accent color (menu and HUD)", x, y, TXT, false);
        dots(g, x, y + 14, COLORS, 8f, 26, c.accent, col -> c().accent = col);
        int by = y + 42;
        button(g, "Smooth graphics: " + (c.smoothUi ? "ON" : "OFF"), x, by, 170, 20, c.smoothUi, () -> c().smoothUi = !c().smoothUi);
        button(g, "Smooth font: " + (c.smoothFont ? "ON" : "OFF"), x + 176, by, 160, 20, c.smoothFont, () -> c().smoothFont = !c().smoothFont);
        button(g, "Start screen: " + (c.starterScreen ? "ON" : "OFF"), x, by + 26, 170, 20, c.starterScreen, () -> c().starterScreen = !c().starterScreen);
        button(g, "Reset all settings", x + 176, by + 26, 160, 20, false, ClientConfig::reset);
        Txt.draw(g, font, "Right Shift: menu    C (hold): zoom    F6: next song", x, by + 60, sub(), false);
        Txt.draw(g, font, "Config: .minecraft/config/myclient.json", x, by + 72, sub(), false);
        Txt.draw(g, font, "Fonts: Carlito and others (SIL Open Font License)", x, by + 84, sub(), false);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x(), my = e.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(mx, my)) {
                h.fn().accept(mx);
                if (h.slider()) dragging = h;
                return true;
            }
        }
        return super.mouseClicked(e, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) { dragging.fn().accept(e.x()); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = null;
        return super.mouseReleased(e);
    }

    // no @Override on purpose: if the signature differs in a version this is simply unused
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        int d = -(int) Math.signum(sy);
        if (page == 1) skyScroll += d; else scroll += d;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) { onClose(); return true; }
        return super.keyPressed(e);
    }
}
