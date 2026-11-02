package com.myclient.music;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/** Plays .mp3 / .wav files from .minecraft/myclient_music on a background thread. */
public final class MusicPlayer {
    private static final Object LOCK = new Object();
    private static final List<Path> tracks = new CopyOnWriteArrayList<>();
    private static Thread thread;
    private static SourceDataLine line;

    private static volatile int index = -1;
    private static volatile int requested = -1;
    private static volatile boolean stop, paused, active;
    private static volatile long posMs, durMs;
    private static volatile float volume = 0.5f;
    private static volatile String title = "Nothing playing", artist = "Menu > Music tab";

    public static Path folder;
    private static volatile boolean systemSource;

    public static boolean isSystem() { return systemSource; }

    public static void setSystem(boolean on) {
        systemSource = on;
        if (on) { paused = true; SystemMedia.start(folder); }
        else SystemMedia.stop();
    }

    public static void init(float vol, boolean sys) {
        volume = vol;
        try {
            folder = FabricLoader.getInstance().getGameDir().resolve("myclient_music");
            Files.createDirectories(folder);
        } catch (Exception e) { e.printStackTrace(); }
        scan();
        if (sys) setSystem(true);
    }

    private static void scan() {
        tracks.clear();
        if (folder == null) return;
        try (Stream<Path> s = Files.list(folder)) {
            List<Path> found = new ArrayList<>();
            s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".mp3") || n.endsWith(".wav");
            }).sorted().forEach(found::add);
            tracks.addAll(found);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ---------- public control API ----------
    public static void rescan() {
        stop = true;
        scan();
        index = -1; active = false; paused = false;
        title = "Nothing playing"; artist = "Menu > Music tab"; posMs = 0; durMs = 0;
    }

    public static void play(int i) {
        if (tracks.isEmpty()) return;
        i = Math.floorMod(i, tracks.size());
        paused = false;
        synchronized (LOCK) {
            requested = i;
            if (thread == null) {
                thread = new Thread(MusicPlayer::worker, "myclient-music");
                thread.setDaemon(true);
                thread.start();
            }
            LOCK.notifyAll();
        }
    }

    public static void toggle() { if (systemSource) { SystemMedia.key(179); return; } if (!active && requested < 0) play(Math.max(index, 0)); else paused = !paused; }
    public static void next() { if (systemSource) { SystemMedia.key(176); return; } play((index < 0 ? 0 : index) + 1); }
    public static void prev() { if (systemSource) { SystemMedia.key(177); return; } play((index < 0 ? 0 : index) - 1); }
    public static void setVolume(float v) { volume = v; }

    /** Real playback position: asks the audio line, so pausing and buffering stay in sync. */
    public static long positionMs() {
        SourceDataLine l = line;
        return l != null ? l.getMicrosecondPosition() / 1000L : posMs;
    }

    public static String rawTitle() { return active ? title : ""; }
    public static String rawArtist() { return active ? artist : ""; }
    public static long durationMs() { return durMs; }

    public static boolean isPlaying() { return systemSource ? SystemMedia.isPlaying() : active && !paused; }
    public static int currentIndex() { return index; }
    public static String title() { return systemSource ? SystemMedia.title() : title; }
    public static String artist() { return systemSource ? SystemMedia.artist() : artist; }
    public static float progress() { if (systemSource) return SystemMedia.progress(); return durMs > 0 ? Math.min(1f, posMs / (float) durMs) : 0f; }

    public static List<String> names() {
        List<String> l = new ArrayList<>();
        for (Path p : tracks) l.add(p.getFileName().toString());
        return l;
    }

    // ---------- worker ----------
    private static void worker() {
        int failures = 0;
        while (true) {
            int idx;
            synchronized (LOCK) {
                while (requested < 0) {
                    try { LOCK.wait(); } catch (InterruptedException e) { return; }
                }
                idx = requested;
                requested = -1;
                stop = false;
            }
            if (idx >= tracks.size()) continue;
            Path p = tracks.get(idx);
            index = idx;
            setNames(p);
            active = true;
            boolean ok = true;
            try {
                if (p.getFileName().toString().toLowerCase().endsWith(".wav")) playWav(p); else playMp3(p);
            } catch (Throwable t) {
                ok = false;
                System.err.println("[myclient] could not play " + p.getFileName() + ": " + t);
            }
            closeLine();
            active = false;
            if (stop) { stop = false; continue; }          // rescan: wait for the next command
            if (requested >= 0) continue;                  // user picked another track
            failures = ok ? 0 : failures + 1;
            if (failures >= Math.max(1, tracks.size())) { index = -1; title = "Nothing playing"; continue; }
            synchronized (LOCK) { if (requested < 0) requested = (idx + 1) % tracks.size(); }
        }
    }

    private static void setNames(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        int sep = n.indexOf(" - ");
        if (sep > 0) { artist = n.substring(0, sep).trim(); title = n.substring(sep + 3).trim(); }
        else { title = n; artist = "Unknown artist"; }
        posMs = 0; durMs = 0;
    }

    private static boolean interrupted() { return stop || requested >= 0; }

    private static void open(AudioFormat f) throws LineUnavailableException {
        line = AudioSystem.getSourceDataLine(f);
        line.open(f);
        line.start();
    }

    private static void closeLine() {
        SourceDataLine l = line;
        line = null;
        if (l == null) return;
        try { if (interrupted()) l.flush(); else l.drain(); } catch (Throwable ignored) {}
        try { l.close(); } catch (Throwable ignored) {}
    }

    private static void setGain() {
        SourceDataLine l = line;
        if (l != null && l.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl c = (FloatControl) l.getControl(FloatControl.Type.MASTER_GAIN);
            float db = (float) (20.0 * Math.log10(Math.max(0.0001, volume)));
            c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), db)));
        }
    }

    private static void write(byte[] b, int len) throws InterruptedException {
        boolean wasPaused = false;
        while (paused && !interrupted()) {
            if (!wasPaused) { line.stop(); wasPaused = true; }
            Thread.sleep(40);
        }
        if (wasPaused) line.start();
        setGain();
        line.write(b, 0, len);
    }

    private static void playMp3(Path p) throws Exception {
        long total = 0;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
            Bitstream bs = new Bitstream(in);
            Header h;
            while ((h = bs.readFrame()) != null) { total += (long) h.ms_per_frame(); bs.closeFrame(); }
            bs.close();
        }
        durMs = total;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
            Bitstream bs = new Bitstream(in);
            Decoder dec = new Decoder();
            boolean opened = false;
            Header h;
            while (!interrupted() && (h = bs.readFrame()) != null) {
                SampleBuffer out = (SampleBuffer) dec.decodeFrame(h, bs);
                if (!opened) {
                    open(new AudioFormat(dec.getOutputFrequency(), 16, dec.getOutputChannels(), true, false));
                    opened = true;
                }
                short[] s = out.getBuffer();
                int n = out.getBufferLength();
                byte[] b = new byte[n * 2];
                for (int i = 0; i < n; i++) { b[2 * i] = (byte) s[i]; b[2 * i + 1] = (byte) (s[i] >> 8); }
                write(b, b.length);
                posMs += (long) h.ms_per_frame();
                bs.closeFrame();
            }
            bs.close();
        }
    }

    private static void playWav(Path p) throws Exception {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(p.toFile())) {
            AudioFormat f = raw.getFormat();
            AudioInputStream ais = raw;
            if (f.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                AudioFormat t = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
                        f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
                ais = AudioSystem.getAudioInputStream(t, raw);
                f = t;
            }
            long frames = raw.getFrameLength();
            durMs = frames > 0 ? (long) (frames * 1000.0 / raw.getFormat().getFrameRate()) : 0;
            open(f);
            byte[] buf = new byte[8192];
            long bytes = 0;
            int n;
            while (!interrupted() && (n = ais.read(buf)) > 0) {
                write(buf, n);
                bytes += n;
                posMs = (long) (bytes / (double) f.getFrameSize() / f.getSampleRate() * 1000.0);
            }
        }
    }
}
