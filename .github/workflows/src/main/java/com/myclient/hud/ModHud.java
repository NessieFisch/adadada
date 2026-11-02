package com.myclient.hud;

import com.myclient.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Lunar-style extras. Every element goes through HudLayout so it can be dragged. */
public final class ModHud {
    private static final ArrayDeque<Long> leftClicks = new ArrayDeque<>(), rightClicks = new ArrayDeque<>();
    private static boolean lastL, lastR;
    private static double lastReach;
    private static int combo;
    private static long comboTime, targetSeen;
    private static float lastHp = 20f;
    private static LivingEntity target;
    private static final long SESSION_START = System.currentTimeMillis();
    private static final String[] ROMAN = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
    private static final String[] DIRS = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
    private static final int CENTER = Integer.MIN_VALUE;

    private static int cps(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    private static void key(GuiGraphicsExtractor g, Font tr, int x, int y, int w, int h, String label, boolean down, int accent) {
        int bg = down ? ((accent & 0x00FFFFFF) | 0xDD000000) : HudRenderer.tint(accent, 0.2f, 0xB0);
        HudRenderer.roundRect(g, x, y, w, h, 3, bg);
        if (!label.isEmpty()) Txt.draw(g, tr, label, x + (w - Txt.w(tr, label)) / 2, y + (h - 8) / 2, down ? 0xFF111111 : 0xFFFFFFFF, false);
    }

    /** align: 0 = left at margin, 1 = centered, 2 = right at margin, 3 = absolute x = margin */
    private static void textBox(GuiGraphicsExtractor g, Font tr, String id, String text, int align, int margin, int defY, int W, int H, int accent, int col) {
        int w = Txt.w(tr, text) + 14, h = 15;
        int dx = align == 0 ? margin : align == 1 ? (W - w) / 2 : align == 2 ? W - w - margin : margin;
        int[] p = HudLayout.place(id, w, h, dx, defY, W, H);
        HudRenderer.panel(g, p[0], p[1], w, h, accent);
        Txt.draw(g, tr, text, p[0] + 7, p[1] + 4, col, true);
    }

    private static void textBox(GuiGraphicsExtractor g, Font tr, String id, String text, int align, int margin, int defY, int W, int H, int accent) {
        textBox(g, tr, id, text, align, margin, defY, W, H, accent, 0xFFFFFFFF);
    }

    private static void listBox(GuiGraphicsExtractor g, Font tr, String id, List<String> lines, List<Integer> cols,
                                int align, int margin, int defY, int W, int H, int accent) {
        int w = 40;
        for (String s : lines) w = Math.max(w, Txt.w(tr, s) + 14);
        int h = lines.size() * 11 + 8;
        int dx = align == 0 ? margin : align == 1 ? (W - w) / 2 : W - w - margin;
        int dy = defY == CENTER ? H / 2 - h / 2 : defY;
        int[] p = HudLayout.place(id, w, h, dx, dy, W, H);
        HudRenderer.panel(g, p[0], p[1], w, h, accent);
        for (int i = 0; i < lines.size(); i++) Txt.draw(g, tr, lines.get(i), p[0] + 7, p[1] + 5 + i * 11, cols.get(i), true);
    }

    public static void render(GuiGraphicsExtractor g, Minecraft mc, ClientConfig c) {
        if (mc.player == null) return;
        Font tr = mc.font;
        int W = g.guiWidth(), H = g.guiHeight(), accent = c.accent;
        boolean edit = HudLayout.editing;
        long now = System.currentTimeMillis();

        boolean l = mc.options.keyAttack.isDown(), r = mc.options.keyUse.isDown();
        if (l && !lastL) {
            leftClicks.addLast(now);
            HitResult hr = mc.hitResult;
            if (hr != null && hr.getType() == HitResult.Type.ENTITY) {
                lastReach = hr.getLocation().distanceTo(mc.player.getEyePosition());
                combo++;
                comboTime = now;
            }
        }
        if (r && !lastR) rightClicks.addLast(now);
        lastL = l; lastR = r;
        HitResult th = mc.hitResult;
        if (th instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le != mc.player) { target = le; targetSeen = now; }
        float hp = mc.player.getHealth();
        if (hp < lastHp - 0.01f) combo = 0;
        lastHp = hp;
        if (combo > 0 && now - comboTime > 3500) combo = 0;
        int cl = cps(leftClicks, now), cr = cps(rightClicks, now);

        // ---- low health warning: pulsing red edges ----
        if (c.lowHealthWarn && !mc.player.isCreative() && !mc.player.isSpectator() && hp <= 6f) {
            double pulse = 0.55 + 0.45 * Math.sin(now / 180.0);
            int al = (int) (0x80 * pulse * Math.min(1f, (7f - hp) / 5f));
            int red = (al << 24) | 0xFF2020;
            g.fillGradient(0, 0, W, H / 5, red, 0x00FF2020);
            g.fillGradient(0, H - H / 5, W, H, 0x00FF2020, red);
        }

        // ---- keystrokes ----
        if (c.showKeys) {
            int[] p = HudLayout.place("keys", 70, 84, 8, H - 96, W, H);
            int x = p[0], y = p[1];
            key(g, tr, x + 24, y, 22, 22, "W", mc.options.keyUp.isDown(), accent);
            key(g, tr, x, y + 24, 22, 22, "A", mc.options.keyLeft.isDown(), accent);
            key(g, tr, x + 24, y + 24, 22, 22, "S", mc.options.keyDown.isDown(), accent);
            key(g, tr, x + 48, y + 24, 22, 22, "D", mc.options.keyRight.isDown(), accent);
            key(g, tr, x, y + 48, 34, 22, "", l, accent);
            key(g, tr, x + 36, y + 48, 34, 22, "", r, accent);
            int lc = l ? 0xFF111111 : 0xFFFFFFFF, rc = r ? 0xFF111111 : 0xFFFFFFFF;
            Txt.draw(g, tr, "LMB", x + (34 - Txt.w(tr, "LMB")) / 2, y + 51, lc, false);
            Txt.draw(g, tr, "" + cl, x + (34 - Txt.w(tr, "" + cl)) / 2, y + 61, lc, false);
            Txt.draw(g, tr, "RMB", x + 36 + (34 - Txt.w(tr, "RMB")) / 2, y + 51, rc, false);
            Txt.draw(g, tr, "" + cr, x + 36 + (34 - Txt.w(tr, "" + cr)) / 2, y + 61, rc, false);
            boolean j = mc.options.keyJump.isDown();
            key(g, tr, x, y + 72, 70, 12, "", j, accent);
            g.fill(x + 22, y + 77, x + 48, y + 79, j ? 0xFF111111 : 0xFFFFFFFF);
        }

        if (c.showCps) textBox(g, tr, "cps", "CPS " + cl + " | " + cr, 0, 8, 70, W, H, accent);

        if (c.showCombo && (combo > 0 || edit)) {
            textBox(g, tr, "combo", "Combo " + (combo > 0 ? combo : 3), 1, 0, H / 2 + 30, W, H, accent, 0xFFFFD84A);
        }

        if (c.showTarget) {
            boolean live = target != null && target.isAlive() && now - targetSeen < 4000;
            if (live || edit) {
                String name = live ? target.getName().getString() : "Target";
                while (name.length() > 1 && Txt.w(tr, name) > 120) name = name.substring(0, name.length() - 1);
                float thp = live ? target.getHealth() : 20f, tmax = live ? Math.max(1f, target.getMaxHealth()) : 20f;
                double dist = live ? mc.player.distanceTo(target) : 3.0;
                int w = 140, h = 44;
                int[] p = HudLayout.place("target", w, h, W / 2 + 22, H / 2 + 18, W, H);
                HudRenderer.panelR(g, p[0], p[1], w, h, 8, accent);
                Txt.draw(g, tr, name, p[0] + 9, p[1] + 6, 0xFFFFFFFF, true);
                float pct = Math.max(0f, Math.min(1f, thp / tmax));
                Gfx.roundF(g, p[0] + 9, p[1] + 19, w - 18, 6, 3, 0x66000000);
                Gfx.roundV(g, p[0] + 9, p[1] + 19, Math.max(6, (int) ((w - 18) * pct)), 6, 3,
                        Gfx.lerp(0xFFFF8080, 0xFF99FFAA, pct), Gfx.lerp(0xFFFF4040, 0xFF40E070, pct));
                Txt.draw(g, tr, String.format("%.1f / %.0f HP", thp, tmax), p[0] + 9, p[1] + 29, 0xFFCCCCCC, false);
                String d = String.format("%.1fm", dist);
                Txt.draw(g, tr, d, p[0] + w - 9 - Txt.w(tr, d), p[1] + 29, 0xFFCCCCCC, false);
            }
        }

        if (c.showPing) {
            int ping = 0;
            var conn = mc.getConnection();
            if (conn != null) {
                var info = conn.getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = info.getLatency();
            }
            textBox(g, tr, "ping", "Ping " + ping + " ms", 2, 8, 70, W, H, accent);
        }

        if (c.showReach) textBox(g, tr, "reach", lastReach > 0 ? String.format("Reach %.2f", lastReach) : "Reach -", 0, 8, H - 132, W, H, accent);

        // ---- armor (text) ----
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        String[] names = { "Helm", "Chest", "Legs", "Boots" };
        if (c.showArmor) {
            List<String> lines = new ArrayList<>();
            List<Integer> cols = new ArrayList<>();
            for (int i = 0; i < slots.length; i++) {
                ItemStack s = mc.player.getItemBySlot(slots[i]);
                if (s.isEmpty()) continue;
                int max = s.getMaxDamage();
                if (max > 0) {
                    int pct = (max - s.getDamageValue()) * 100 / max;
                    lines.add(names[i] + " " + pct + "%");
                    cols.add(pct > 50 ? 0xFF55FF55 : pct > 25 ? 0xFFFFFF55 : 0xFFFF5555);
                } else { lines.add(names[i] + " -"); cols.add(0xFFFFFFFF); }
            }
            if (lines.isEmpty() && edit) { lines.add("Helm 100%"); cols.add(0xFF55FF55); lines.add("Chest 100%"); cols.add(0xFF55FF55); }
            if (!lines.isEmpty()) listBox(g, tr, "armor", lines, cols, 2, 8, CENTER, W, H, accent);
        }

        // ---- armor icons with durability bars (next to the hotbar) ----
        if (c.showArmorIcons) {
            int w = 4 * 20 + 2, h = 24;
            int[] p = HudLayout.place("armoricons", w, h, W / 2 + 98, H - 26, W, H);
            for (int i = 0; i < slots.length; i++) {
                int x = p[0] + 1 + i * 20, y = p[1] + 1;
                HudRenderer.roundRect(g, x - 1, y - 1, 18, 18, 3, 0x66000000);
                ItemStack s = mc.player.getItemBySlot(slots[i]);
                if (s.isEmpty()) continue;
                boolean drawn = ItemIcons.draw(g, s, x, y);
                if (!drawn) Txt.draw(g, tr, names[i].substring(0, 1), x + 5, y + 4, 0xFFFFFFFF, true);
                int max = s.getMaxDamage();
                if (max > 0) {
                    int pct = (max - s.getDamageValue()) * 100 / max;
                    int col = pct > 50 ? 0xFF55FF55 : pct > 25 ? 0xFFFFFF55 : 0xFFFF5555;
                    g.fill(x, y + 18, x + 16, y + 20, 0xFF000000);
                    g.fill(x, y + 18, x + Math.max(1, 16 * pct / 100), y + 20, col);
                }
            }
        }

        // ---- potions / status effect timer ----
        if (c.showPotions) {
            List<String> lines = new ArrayList<>();
            List<Integer> cols = new ArrayList<>();
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                String name = e.getEffect().value().getDisplayName().getString();
                int amp = e.getAmplifier() + 1;
                if (amp > 1) name += " " + (amp < ROMAN.length ? ROMAN[amp] : "" + amp);
                String time;
                if (e.isInfiniteDuration()) time = "inf";
                else { int sec = e.getDuration() / 20; time = (sec / 60) + ":" + String.format("%02d", sec % 60); }
                lines.add(name + "  " + time); cols.add(e.getDuration() < 200 && !e.isInfiniteDuration() ? 0xFFFF7777 : 0xFFFFFFFF);
                if (lines.size() >= 6) break;
            }
            if (lines.isEmpty() && edit) { lines.add("Speed II  1:30"); cols.add(0xFFFFFFFF); }
            if (!lines.isEmpty()) listBox(g, tr, "potions", lines, cols, 0, 8, 92, W, H, accent);
        }

        if (c.showDirection) {
            float a = ((mc.player.getYRot() % 360f) + 360f) % 360f;
            textBox(g, tr, "direction", "Facing " + DIRS[Math.round(a / 45f) % 8] + "  " + Math.round(a), 1, 0, 102, W, H, accent);
        }

        if (c.showStats) {
            String s = String.format("HP %.1f  Food %d", mc.player.getHealth(), mc.player.getFoodData().getFoodLevel());
            textBox(g, tr, "stats", s, 2, 8, 92, W, H, accent);
        }

        HudLayout.flush();
        // ---- saturation: gold bars under each hunger icon, like AppleSkin ----
        if (c.showSaturation && !mc.player.isCreative() && !mc.player.isSpectator()) {
            float sat = mc.player.getFoodData().getSaturationLevel();
            int endX = W / 2 + 91, by = H - 39 + 10;
            for (int k = 0; k < 10; k++) {
                int ix = endX - 8 * (k + 1);
                float frac = Math.max(0f, Math.min(1f, (sat - 2 * k) / 2f));
                Gfx.roundF(g, ix + 0.5f, by, 8, 2, 1f, 0x77000000);
                if (frac > 0.04f) Gfx.roundF(g, ix + 0.5f, by, Math.max(2f, 8 * frac), 2, 1f, 0xFFFFC83D);
            }
        }

        if (c.showHeld) {
            ItemStack s = mc.player.getMainHandItem();
            String t = "Hand: -";
            if (!s.isEmpty()) {
                t = "Hand: " + s.getHoverName().getString();
                int max = s.getMaxDamage();
                if (max > 0) t += "  " + ((max - s.getDamageValue()) * 100 / max) + "%";
                else if (s.getCount() > 1) t += " x" + s.getCount();
            }
            textBox(g, tr, "held", t, 2, 8, H - 30, W, H, accent);
        }

        if (c.showItems) {
            int gap = 0, pearl = 0, totem = 0, arrow = 0, pots = 0, crystals = 0;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) continue;
                if (s.is(Items.GOLDEN_APPLE)) gap += s.getCount();
                else if (s.is(Items.ENDER_PEARL)) pearl += s.getCount();
                else if (s.is(Items.TOTEM_OF_UNDYING)) totem += s.getCount();
                else if (s.is(Items.ARROW)) arrow += s.getCount();
                else if (s.is(Items.SPLASH_POTION)) pots += s.getCount();
                else if (s.is(Items.END_CRYSTAL)) crystals += s.getCount();
            }
            List<String> lines = new ArrayList<>();
            List<Integer> cols = new ArrayList<>();
            if (gap > 0) { lines.add("Gapples " + gap); cols.add(0xFFFFDD55); }
            if (pearl > 0) { lines.add("Pearls " + pearl); cols.add(0xFF55DDAA); }
            if (totem > 0) { lines.add("Totems " + totem); cols.add(0xFFFFFFFF); }
            if (arrow > 0) { lines.add("Arrows " + arrow); cols.add(0xFFCCCCCC); }
            if (pots > 0) { lines.add("Pots " + pots); cols.add(0xFFFF7799); }
            if (crystals > 0) { lines.add("Crystals " + crystals); cols.add(0xFFDD88FF); }
            if (lines.isEmpty() && edit) { lines.add("Pearls 16"); cols.add(0xFF55DDAA); }
            if (!lines.isEmpty()) listBox(g, tr, "items", lines, cols, 2, 8, H / 2 + 40, W, H, accent);
        }

        if (c.showSession) {
            long sec = (now - SESSION_START) / 1000;
            String t = sec >= 3600 ? String.format("%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)
                    : String.format("%d:%02d", sec / 60, sec % 60);
            textBox(g, tr, "session", "Session " + t, 1, 0, 120, W, H, accent);
        }

        if (c.showStatus) {
            String s = c.toggleSprint ? "Sprint (Toggled)"
                    : mc.player.isSprinting() ? "Sprinting"
                    : mc.player.isShiftKeyDown() ? "Sneaking" : "Walking";
            textBox(g, tr, "status", s, 0, 8, H - 114, W, H, accent);
        }
    }
}
