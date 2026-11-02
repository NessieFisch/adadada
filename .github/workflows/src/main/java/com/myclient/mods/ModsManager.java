package com.myclient.mods;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Lists the mods in your mods folder and lets you switch them off or on.
 * A running mod cannot be unloaded and Windows locks its jar, so "off" renames the jar to .jar.disabled right after
 * the game closes (a tiny hidden script waits for Minecraft to exit). "On" for a disabled jar renames it back at once.
 */
public final class ModsManager {
    public record Entry(String id, String name, String version, String desc, Path jar, boolean locked, boolean enabled, boolean wasDisabledFile) {}

    private static final java.util.Set<String> PROTECTED = java.util.Set.of("myclient", "fabric-api", "fabricloader", "minecraft", "java");
    private static final Map<String, Path> pending = new HashMap<>();
    private static boolean hooked;

    public static Path modsDir() { return FabricLoader.getInstance().getGameDir().resolve("mods"); }

    public static boolean hasPending() { return !pending.isEmpty(); }

    public static List<Entry> list() {
        ensureHook();
        List<Entry> out = new ArrayList<>();
        Path dir = modsDir().toAbsolutePath().normalize();
        for (ModContainer mc : FabricLoader.getInstance().getAllMods()) {
            ModMetadata md = mc.getMetadata();
            String id = md.getId();
            if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader")) continue;
            ModOrigin o = mc.getOrigin();
            if (o.getKind() != ModOrigin.Kind.PATH) continue;                    // libraries packed inside other mods
            Path jar = null;
            for (Path p : o.getPaths()) {
                Path ap = p.toAbsolutePath().normalize();
                if (Files.isRegularFile(ap) && ap.getFileName().toString().endsWith(".jar") && ap.getParent() != null && ap.getParent().equals(dir)) jar = ap;
            }
            if (jar == null) continue;
            boolean locked = PROTECTED.contains(id);
            out.add(new Entry(id, md.getName(), md.getVersion().getFriendlyString(), md.getDescription(), jar, locked, !pending.containsKey(id), false));
        }
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".jar.disabled")).forEach(p -> {
                String n = p.getFileName().toString();
                out.add(new Entry("file:" + n, n.substring(0, n.length() - ".jar.disabled".length()), "disabled", "Switched off. Turn it on, then restart the game.", p, false, false, true));
            });
        } catch (Exception ignored) {}
        out.sort(Comparator.comparing((Entry e) -> e.name().toLowerCase()));
        return out;
    }

    /** returns a short message for the menu */
    public static String setEnabled(Entry e, boolean on) {
        if (e.locked()) return "This one is needed.";
        try {
            if (e.wasDisabledFile()) {
                if (!on) return "";
                String n = e.jar().getFileName().toString();
                Files.move(e.jar(), e.jar().resolveSibling(n.substring(0, n.length() - ".disabled".length())));
                return e.name() + " will load after you restart.";
            }
            if (on) { pending.remove(e.id()); return e.name() + " stays on."; }
            pending.put(e.id(), e.jar());
            return e.name() + " turns off when you close the game.";
        } catch (Exception ex) {
            return "Could not change " + e.name() + ".";
        }
    }

    private static void ensureHook() {
        if (hooked) return;
        hooked = true;
        Runtime.getRuntime().addShutdownHook(new Thread(ModsManager::applyOnExit));
    }

    private static void applyOnExit() {
        if (pending.isEmpty()) return;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("try { Wait-Process -Id ").append(ProcessHandle.current().pid()).append(" -Timeout 90 -ErrorAction SilentlyContinue } catch {}\r\n");
            sb.append("Start-Sleep -Seconds 2\r\n");
            for (Path p : pending.values()) {
                String full = p.toString().replace("'", "''");
                String name = p.getFileName().toString().replace("'", "''");
                sb.append("if (Test-Path -LiteralPath '").append(full).append("') { Rename-Item -LiteralPath '").append(full)
                  .append("' -NewName '").append(name).append(".disabled' -Force }\r\n");
            }
            Path script = FabricLoader.getInstance().getConfigDir().resolve("myclient-apply.ps1");
            Files.writeString(script, sb.toString(), StandardCharsets.UTF_8);
            new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString()).start();
        } catch (Throwable ignored) {}
    }
}
