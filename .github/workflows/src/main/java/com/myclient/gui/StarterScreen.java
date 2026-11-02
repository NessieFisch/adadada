package com.myclient.gui;

import com.myclient.ClientConfig;
import com.myclient.ScreenUtil;
import com.myclient.hud.Gfx;
import com.myclient.hud.HudRenderer;
import com.myclient.hud.Txt;
import com.myclient.sky.CursorRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/** Our own main menu. The "Vanilla menu" button brings back Minecraft's. */
public class StarterScreen extends Screen {
    public static boolean bypass;
    private static final String[] LABELS = { "Singleplayer", "Multiplayer", "Options", "Quit Game" };

    private record Hit(int x, int y, int w, int h, Runnable fn) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final long openNs = System.nanoTime();
    private long lastNs = System.nanoTime();
    private final float[] hover = new float[5];
    private int mx, my;

    public StarterScreen() { super(Component.literal("Spared Client")); }

    @Override
    protected void init() {
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public void removed() {
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    @Override
    public void onClose() { /* nothing to go back to */ }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------- opening other screens by reflection (constructors differ between versions) ----------
    private void openVanilla(String... classNames) {
        for (String cn : classNames) {
            try {
                Class<?> k = Class.forName(cn);
                for (Constructor<?> ctor : k.getConstructors()) {
                    Class<?>[] pt = ctor.getParameterTypes();
                    if (pt.length == 1 && pt[0] == Screen.class) { ScreenUtil.open((Screen) ctor.newInstance(this)); return; }
                    if (pt.length == 2 && pt[0] == Screen.class && pt[1].getSimpleName().equals("Options")) {
                        ScreenUtil.open((Screen) ctor.newInstance(this, minecraft.options)); return;
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void vanillaTitle() {
        bypass = true;
        try {
            Class<?> k = Class.forName("net.minecraft.client.gui.screens.TitleScreen");
            ScreenUtil.open((Screen) k.getConstructor().newInstance());
        } catch (Throwable t) { bypass = false; }
    }

    private void act(int i) {
        switch (i) {
            case 0 -> openVanilla("net.minecraft.client.gui.screens.worldselection.SelectWorldScreen");
            case 1 -> openVanilla("net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen");
            case 2 -> openVanilla("net.minecraft.client.gui.screens.options.OptionsScreen", "net.minecraft.client.gui.screens.OptionsScreen");
            default -> minecraft.stop();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hits.clear();
        mx = mouseX; my = mouseY;
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        float t = (now - openNs) / 1e9f;
        ClientConfig c = ClientConfig.get();
        int ac = c.accent;

        // background: dark gold gradient with drifting glows and floating dust
        g.fillGradient(0, 0, width, height, 0xFF07060A, HudRenderer.tint(ac, 0.20f, 0xFF));
        for (int k = 0; k < 3; k++) {
            float gx = width * (0.2f + 0.3f * k) + (float) Math.sin(t * 0.35 + k * 2.1) * 40f;
            float gy = height * (0.30f + 0.18f * k) + (float) Math.cos(t * 0.30 + k) * 28f;
            Gfx.circle(g, gx, gy, 60 + k * 22, Gfx.a(ac, 0.06f));
            Gfx.circle(g, gx, gy, 34 + k * 12, Gfx.a(ac, 0.05f));
        }
        for (int i = 0; i < 50; i++) {
            float px = ((i * 97) % 1000) / 1000f * width + (float) Math.sin(t * 0.3 + i) * 8f;
            float py = height - ((t * (7 + (i % 5) * 4) + i * 37) % (height + 10));
            int sz = i % 4 == 0 ? 2 : 1;
            g.fill((int) px, (int) py, (int) px + sz, (int) py + sz, Gfx.a(0xFFFFFFFF, 0.10f + 0.12f * (i % 3)));
        }

        // title
        float te = Math.min(1f, t / 0.5f);
        te = 1f - (1f - te) * (1f - te);
        int ty = (int) (height * 0.14f) + (int) ((1f - te) * -10);
        Gfx.bigText(g, font, "SPARED", width / 2f, ty, 4.5f, Gfx.a(0xFFFFFFFF, te), true);
        Gfx.bigText(g, font, "C L I E N T", width / 2f, ty + 44, 1.6f, Gfx.a(ac, te), true);
        Gfx.roundV(g, width / 2 - 40, ty + 62, 80, 2, 1, Gfx.a(ac, 0f), Gfx.a(ac, te));
        String name = minecraft.getUser().getName();
        Gfx.bigText(g, font, "Welcome back, " + name, width / 2f, ty + 72, 1f, Gfx.a(0xFFBBBBBB, te), false);

        // buttons
        int bw = 190, bh = 26, bx = (width - bw) / 2, by0 = (int) (height * 0.46f);
        for (int i = 0; i < LABELS.length; i++) {
            float e = Math.max(0f, Math.min(1f, (t - 0.2f - 0.08f * i) / 0.35f));
            e = 1f - (1f - e) * (1f - e) * (1f - e);
            int by = by0 + i * 33 + (int) ((1f - e) * 14);
            boolean in = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
            hover[i] += ((in ? 1f : 0f) - hover[i]) * Math.min(1f, dt * 16f);
            float hv = hover[i];
            int top = Gfx.lerp(HudRenderer.tint(ac, 0.22f, 0xE0), Gfx.lerp(ac, 0xFFFFFFFF, 0.1f), hv * 0.9f);
            int bot = Gfx.lerp(HudRenderer.tint(ac, 0.12f, 0xE0), ac, hv * 0.9f);
            Gfx.shadow(g, bx, by, bw, bh, 9, ac, (int) (0x28 * e), 0, 2);
            Gfx.border(g, bx, by, bw, bh, 9, Gfx.a(ac, 0.5f * e), Gfx.a(top, e), Gfx.a(bot, e));
            int tw = Txt.w(font, LABELS[i]);
            Txt.draw(g, font, LABELS[i], bx + (bw - tw) / 2, by + (bh - 8) / 2, Gfx.lerp(0xFFFFFFFF, 0xFF15110A, hv), false);
            final int idx = i;
            hits.add(new Hit(bx, by, bw, bh, () -> act(idx)));
        }

        // footer
        int fy = height - 24;
        boolean vin = mx >= 10 && mx < 100 && my >= fy && my < fy + 16;
        hover[4] += ((vin ? 1f : 0f) - hover[4]) * Math.min(1f, dt * 16f);
        Gfx.border(g, 10, fy, 90, 16, 8, Gfx.a(ac, 0.35f), Gfx.lerp(HudRenderer.tint(ac, 0.18f, 0xD0), HudRenderer.tint(ac, 0.4f, 0xE0), hover[4]), HudRenderer.tint(ac, 0.12f, 0xD0));
        Txt.draw(g, font, "Vanilla menu", 10 + (90 - Txt.w(font, "Vanilla menu")) / 2, fy + 4, 0xFFE8E0C8, false);
        hits.add(new Hit(10, fy, 90, 16, this::vanillaTitle));
        String ver = "Spared Client  -  Minecraft 26.2";
        Txt.draw(g, font, ver, (width - Txt.w(font, ver)) / 2, height - 14, 0x88FFFFFF, false);
        String hint = "Right Shift in game = menu";
        Txt.draw(g, font, hint, width - Txt.w(font, hint) - 10, height - 14, 0x66FFFFFF, false);

        CursorRenderer.draw(g, mouseX, mouseY, c.cursor, c.cursorColor, c.cursorSize);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(e.x(), e.y())) { h.fn().run(); return true; }
        }
        return super.mouseClicked(e, doubled);
    }
}
