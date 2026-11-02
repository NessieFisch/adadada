package com.myclient.hud;

import com.myclient.ClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Text drawn with our smooth TrueType fonts (assets/myclient/font/*.json). Falls back to Minecraft's font. */
public final class Txt {
    public static final String[] LYRIC_FONT_LABELS = { "Poppins", "Poppins Italic", "Serif", "Gloock", "Handwritten", "Poiret", "Heavy", "Minecraft" };
    private static final Map<String, Style> STYLES = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static Method textC;
    private static boolean textTried;

    /** font id for lyric font index; null = Minecraft's own font */
    public static String lyricFont(int idx) { return idx >= 0 && idx < 7 ? "lyr" + idx : null; }

    private static Style styleFor(String name) {
        if (name == null) return null;
        if (name.equals("ui") && !ClientConfig.get().smoothFont) return null;
        Style s = STYLES.get(name);
        if (s != null) return s;
        if (FAILED.contains(name)) return null;
        try {
            Identifier id = Identifier.fromNamespaceAndPath("myclient", name);
            for (Method m : Style.class.getMethods()) {
                if (!m.getName().equals("withFont") || m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                Object arg = null;
                if (p.isInstance(id)) arg = id;
                else {
                    for (Class<?> inner : p.getClasses()) {
                        if (!inner.getSimpleName().equals("Resource")) continue;
                        for (Constructor<?> k : inner.getConstructors()) {
                            if (k.getParameterCount() == 1 && k.getParameterTypes()[0].isInstance(id)) { arg = k.newInstance(id); break; }
                        }
                    }
                }
                if (arg == null) continue;
                s = (Style) m.invoke(Style.EMPTY, arg);
                break;
            }
        } catch (Throwable t) { s = null; }
        if (s == null) FAILED.add(name); else STYLES.put(name, s);
        return s;
    }

    public static int w(Font f, String s) { return wF(f, "ui", s); }

    public static int wF(Font f, String font, String s) {
        Style st = styleFor(font);
        return st == null ? f.width(s) : f.width(Component.literal(s).withStyle(st));
    }

    public static void draw(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color, boolean shadow) {
        drawF(g, f, "ui", s, x, y, color, shadow);
    }

    public static void drawF(GuiGraphicsExtractor g, Font f, String font, String s, int x, int y, int color, boolean shadow) {
        Style st = styleFor(font);
        if (st != null) {
            if (!textTried) {
                textTried = true;
                try { textC = g.getClass().getMethod("text", Font.class, Component.class, int.class, int.class, int.class, boolean.class); }
                catch (Throwable t) { textC = null; }
            }
            if (textC != null) {
                try { textC.invoke(g, f, Component.literal(s).withStyle(st), x, y, color, shadow); return; }
                catch (Throwable t) { textC = null; }
            }
        }
        g.text(f, s, x, y, color, shadow);
    }
}
