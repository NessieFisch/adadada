package com.myclient.mods;

import com.myclient.ClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Per-tick behaviour mods: toggle sprint, zoom, fullbright. */
public final class ModTicker {
    private static boolean zooming, fbApplied;
    private static int savedFov = 70;

    public static void tick(Minecraft mc, KeyMapping zoomKey) {
        ClientConfig c = ClientConfig.get();

        // ---- zoom (hold key, no mixin: temporarily lowers the FOV option) ----
        boolean z = zoomKey.isDown();
        if (z && !zooming) { savedFov = mc.options.fov().get(); zooming = true; }
        if (z) mc.options.fov().set(Math.max(30, Math.min(110, Math.round(c.zoomFov))));
        if (!z && zooming) { mc.options.fov().set(savedFov); zooming = false; }

        if (mc.player == null) return;
        com.myclient.sky.Skies.tickTime(mc);

        // ---- no death animation: dead players vanish at once ----
        if (c.noDeathAnim && mc.level != null) {
            var dead = new java.util.ArrayList<net.minecraft.world.entity.player.Player>();
            for (var p : mc.level.players()) if (p != mc.player && p.isDeadOrDying()) dead.add(p);
            for (var p : dead) mc.level.removeEntity(p.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }

        // ---- no hurt cam: clears the damage shake right after it is set ----
        if (c.noHurtCam) mc.player.hurtTime = 0;

        // ---- always sprint while moving forward ----
        if (c.toggleSprint && mc.options.keyUp.isDown()) mc.options.keySprint.setDown(true);

        // ---- fullbright (client-side night vision, no particles / icon) ----
        if (c.fullbright) {
            MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 300) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false, false));
            }
            fbApplied = true;
        } else if (fbApplied) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            fbApplied = false;
        }
    }
}
