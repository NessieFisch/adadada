package com.myclient.hud;

import com.myclient.ClientConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Position and size of every HUD element: remembers where it was drawn and applies what the player set. */
public final class HudLayout {
    public record Rect(int x, int y, int w, int h) {}

    /** true while the layout editor is open, so empty elements show sample content. */
    public static boolean editing;

    public static final Map<String, Rect> rects = new LinkedHashMap<>();

    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put("topbar", "Top bar");     NAMES.put("speed", "Speed");          NAMES.put("music", "Music card");
        NAMES.put("coords", "Coordinates"); NAMES.put("ram", "RAM");              NAMES.put("keys", "Keystrokes");
        NAMES.put("cps", "CPS");            NAMES.put("ping", "Ping");            NAMES.put("armor", "Armor status");
        NAMES.put("potions", "Potions");    NAMES.put("direction", "Direction");  NAMES.put("stats", "Health / Food");
        NAMES.put("held", "Held item");     NAMES.put("items", "Item counter");   NAMES.put("session", "Session");
        NAMES.put("status", "Sprint status"); NAMES.put("armoricons", "Armor icons"); NAMES.put("reach", "Reach");
        NAMES.put("combo", "Combo");        NAMES.put("target", "Target HUD");
    }

    private static GuiGraphicsExtractor G;
    private static boolean transformed;

    public static String name(String id) { return NAMES.getOrDefault(id, id); }

    public static void begin(GuiGraphicsExtractor g) { rects.clear(); G = g; transformed = false; }

    /** Ends the size transform of the element drawn last. */
    public static void flush() {
        if (transformed && G != null) Gfx.popTransform(G);
        transformed = false;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /**
     * Returns the final {x, y} of an element: the dragged position if set, else the default. If the element has a
     * size other than 1.0, everything drawn after this call is scaled around {x, y} until the next place() / flush().
     */
    public static int[] place(String id, int w, int h, int defX, int defY, int W, int H) {
        flush();
        ClientConfig cfg = ClientConfig.get();
        float s = cfg.scaleOf(id);
        float[] p = cfg.hudPos.get(id);
        int x = defX, y = defY;
        if (p != null && p.length >= 2) { x = Math.round(p[0] * W); y = Math.round(p[1] * H); }
        int sw = Math.round(w * s), sh = Math.round(h * s);
        x = clamp(x, 0, Math.max(0, W - sw));
        y = clamp(y, 0, Math.max(0, H - sh));
        boolean ok = Math.abs(s - 1f) > 0.001f && G != null && Gfx.pushTransform(G, x, y, s);
        if (!ok) {
            sw = w; sh = h;
            x = clamp(x, 0, Math.max(0, W - w));
            y = clamp(y, 0, Math.max(0, H - h));
        }
        transformed = ok;
        rects.put(id, new Rect(x, y, sw, sh));
        return new int[] { x, y };
    }
}
