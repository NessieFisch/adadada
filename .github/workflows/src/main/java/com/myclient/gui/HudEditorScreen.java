package com.myclient.gui;

import com.myclient.ClientConfig;
import com.myclient.ScreenUtil;
import com.myclient.hud.Gfx;
import com.myclient.hud.Txt;
import com.myclient.hud.HudLayout;
import com.myclient.sky.CursorRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Drag any HUD element to where you want it. Esc / Done = back to the menu. */
public class HudEditorScreen extends Screen {
    private String drag;
    private int offX, offY;

    public HudEditorScreen() { super(Component.literal("HUD Editor")); }

    @Override
    protected void init() {
        HudLayout.editing = true;
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public void removed() {
        HudLayout.editing = false;
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        ClientConfig.get().save();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { ScreenUtil.open(new ClickGuiScreen()); }

    private boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        ClientConfig c = ClientConfig.get();
        g.fill(0, 0, width, height, 0x33000000);
        String hint = "Drag elements to move them.  Esc / Done = back";
        Txt.draw(g, font, hint, (width - Txt.w(font, hint)) / 2, height / 2 - 4, 0xFFFFFFFF, true);

        String hover = topAt(mouseX, mouseY);
        for (var e : new ArrayList<>(HudLayout.rects.entrySet())) {
            HudLayout.Rect r = e.getValue();
            boolean active = e.getKey().equals(drag) || (drag == null && e.getKey().equals(hover));
            int col = active ? c.accent : 0xAAFFFFFF;
            Gfx.border(g, r.x() - 2, r.y() - 2, r.w() + 4, r.h() + 4, 7, col,
                    active ? 0x33FFFFFF : 0x14FFFFFF, active ? 0x33FFFFFF : 0x14FFFFFF);
            if (active) Txt.draw(g, font, HudLayout.name(e.getKey()), r.x(), Math.max(0, r.y() - 10), c.accent, true);
        }

        int by = height - 30, bx = width / 2 - 66;
        drawButton(g, bx, by, 60, 20, "Reset", mouseX, mouseY, false);
        drawButton(g, bx + 72, by, 60, 20, "Done", mouseX, mouseY, true);
        CursorRenderer.draw(g, mouseX, mouseY, c.cursor, c.cursorColor, c.cursorSize);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my, boolean primary) {
        ClientConfig c = ClientConfig.get();
        int top = primary ? Gfx.lerp(c.accent, 0xFFFFFFFF, 0.18f) : (in(mx, my, x, y, w, h) ? 0xF02A2A34 : 0xE0202028);
        int bot = primary ? c.accent : (in(mx, my, x, y, w, h) ? 0xF0222229 : 0xE0181820);
        Gfx.roundV(g, x, y, w, h, 8, top, bot);
        Txt.draw(g, font, label, x + (w - Txt.w(font, label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
    }

    private String topAt(double mx, double my) {
        List<String> ids = new ArrayList<>(HudLayout.rects.keySet());
        for (int i = ids.size() - 1; i >= 0; i--) {
            HudLayout.Rect r = HudLayout.rects.get(ids.get(i));
            if (in(mx, my, r.x(), r.y(), r.w(), r.h())) return ids.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x(), my = e.y();
        int by = height - 30, bx = width / 2 - 66;
        if (in(mx, my, bx, by, 60, 20)) { ClientConfig.get().hudPos.clear(); return true; }
        if (in(mx, my, bx + 72, by, 60, 20)) { onClose(); return true; }
        String id = topAt(mx, my);
        if (id != null) {
            HudLayout.Rect r = HudLayout.rects.get(id);
            drag = id; offX = (int) mx - r.x(); offY = (int) my - r.y();
            return true;
        }
        return super.mouseClicked(e, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (drag != null) {
            HudLayout.Rect r = HudLayout.rects.get(drag);
            if (r != null) {
                int x = Math.max(0, Math.min(width - r.w(), (int) e.x() - offX));
                int y = Math.max(0, Math.min(height - r.h(), (int) e.y() - offY));
                ClientConfig.get().hudPos.put(drag, new float[] { x / (float) width, y / (float) height });
                HudLayout.rects.put(drag, new HudLayout.Rect(x, y, r.w(), r.h()));
            }
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        drag = null;
        return super.mouseReleased(e);
    }
}
