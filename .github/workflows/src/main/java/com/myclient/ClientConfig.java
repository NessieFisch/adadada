package com.myclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.myclient.sky.CursorStyle;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("myclient.json");
    private static ClientConfig instance;

    public int cfgVersion = 0;

    // Skies
    public boolean skyOn = true;
    public int skyId = 16;
    public float skyIntensity = 0.9f;
    public float skyAtmosphere = 0.6f;
    public int skyTime = 0;               // 0 auto, 1 day, 2 dusk, 3 night, 4 midnight
    public boolean skyAnimate = true;

    // Cursor / crosshair
    public CursorStyle cursor = CursorStyle.RING;
    public int cursorColor = 0xFFFFFFFF;
    public float cursorSize = 1.0f;
    public boolean customCrosshair = true;

    // HUD
    public int accent = 0xFF8B5CF6;
    public boolean showTopBar = true, showNowPlaying = true, showSpeed = true, showMusic = false, showCoords = true, showMemory = true;
    public boolean showKeys = true, showCps = true, showArmor = true, showPotions = true, showPing = false;
    public boolean showDirection = false, showStats = false, showHeld = false, showItems = false, showSession = false, showStatus = false;
    public java.util.Map<String, float[]> hudPos = new java.util.HashMap<>();
    public java.util.Map<String, Float> hudScale = new java.util.HashMap<>();
    public boolean showArmorIcons = false, showSaturation = false, showReach = false, noHurtCam = false;
    public boolean smoothUi = true, smoothFont = true, starterScreen = true;
    public boolean showCombo = false, showTarget = false, lowHealthWarn = false, noDeathAnim = false;
    public boolean toggleSprint = false, fullbright = false;
    public float zoomFov = 30f;

    // Music
    public boolean systemMusic = true;
    public float musicVolume = 0.5f;

    public float scaleOf(String id) {
        Float f = hudScale == null ? null : hudScale.get(id);
        return f == null ? 1f : Math.max(0.5f, Math.min(2f, f));
    }

    public void setScale(String id, float v) {
        if (hudScale == null) hudScale = new java.util.HashMap<>();
        hudScale.put(id, Math.max(0.5f, Math.min(2f, Math.round(v * 10f) / 10f)));
    }

    public static void reset() { instance = new ClientConfig(); instance.cfgVersion = 5; instance.save(); }

    public static ClientConfig get() {
        if (instance == null) {
            try {
                if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), ClientConfig.class);
            } catch (Exception ignored) {}
            if (instance == null) { instance = new ClientConfig(); instance.cfgVersion = 5; }
            if (instance.cfgVersion < 3) {            // config from an old version: take the new defaults
                instance.accent = 0xFF8B5CF6;
                instance.cursorColor = 0xFFFFFFFF;
                instance.showMusic = false;
                instance.showPing = false;
                instance.systemMusic = true;
                if (instance.hudPos != null) instance.hudPos.clear();
            }
            if (instance.cfgVersion < 4) instance.showNowPlaying = true;
            if (instance.cfgVersion < 5) { instance.cfgVersion = 5; instance.skyOn = true; instance.skyId = 16; instance.skyIntensity = 1.0f; }
            if (instance.hudPos == null) instance.hudPos = new java.util.HashMap<>();
            if (instance.hudScale == null) instance.hudScale = new java.util.HashMap<>();
        }
        return instance;
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception e) { e.printStackTrace(); }
    }
}
