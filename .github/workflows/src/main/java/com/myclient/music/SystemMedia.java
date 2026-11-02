package com.myclient.music;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads "now playing" info from the Windows media controls (the same thing the volume flyout shows).
 * Anything that reports there works: YouTube Music in Chrome/Edge, Spotify, etc.
 * The mod never downloads or plays that audio, it only displays it and sends media keys.
 */
public final class SystemMedia {
    private static final String READER_SCRIPT = """
            [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            Function Await($WinRtTask, $ResultType) {
              $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
              $netTask = $asTask.Invoke($null, @($WinRtTask))
              $netTask.Wait(-1) | Out-Null
              $netTask.Result
            }
            [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
            $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            while ($true) {
              try {
                $s = $mgr.GetCurrentSession()
                if ($s -ne $null) {
                  $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                  $t = $s.GetTimelineProperties()
                  $i = $s.GetPlaybackInfo()
                  $pos = [int]($t.Position.TotalSeconds)
                  $end = [int](($t.EndTime - $t.StartTime).TotalSeconds)
                  $pl = 0
                  if ($i.PlaybackStatus -eq 'Playing') { $pl = 1 }
                  Write-Output ("{0}`t{1}`t{2}`t{3}`t{4}" -f $p.Title, $p.Artist, $pos, $end, $pl)
                } else { Write-Output "`t`t0`t0`t0" }
              } catch { Write-Output "`t`t0`t0`t0" }
              [Console]::Out.Flush()
              Start-Sleep -Milliseconds 1000
            }
            """;

    private static final String KEY_SCRIPT = """
            param([int]$vk)
            Add-Type -MemberDefinition '[DllImport("user32.dll")] public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);' -Name K -Namespace W
            [W.K]::keybd_event([byte]$vk, 0, 0, [UIntPtr]::Zero)
            [W.K]::keybd_event([byte]$vk, 0, 2, [UIntPtr]::Zero)
            """;

    private static Process proc;
    private static Path dir;
    private static volatile String title = "", artist = "";
    private static volatile boolean playing;
    private static volatile long pos, end, stamp;

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static synchronized void start(Path folder) {
        if (!supported() || proc != null || folder == null) return;
        dir = folder;
        try {
            Path script = folder.resolve("media.ps1");
            Files.writeString(script, READER_SCRIPT, StandardCharsets.UTF_8);
            Files.writeString(folder.resolve("key.ps1"), KEY_SCRIPT, StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            proc = pb.start();
            Runtime.getRuntime().addShutdownHook(new Thread(SystemMedia::stop));
            Process p = proc;
            Thread t = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] a = line.split("\t", -1);
                        if (a.length < 5) continue;
                        title = a[0]; artist = a[1];
                        long np = parse(a[2]);
                        end = parse(a[3]);
                        playing = a[4].trim().equals("1");
                        if (np != pos) { pos = np; stamp = System.currentTimeMillis(); }
                    }
                } catch (Exception ignored) {}
            }, "myclient-media");
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            System.err.println("[myclient] media reader failed: " + e);
            proc = null;
        }
    }

    public static synchronized void stop() {
        if (proc != null) { proc.destroyForcibly(); proc = null; }
        title = ""; artist = ""; playing = false;
    }

    private static long parse(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    /** 179 = play/pause, 176 = next, 177 = previous (Windows media keys). */
    public static void key(int vk) {
        if (!supported() || dir == null) return;
        Thread t = new Thread(() -> {
            try {
                new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-File", dir.resolve("key.ps1").toString(), "-vk", String.valueOf(vk))
                        .redirectErrorStream(true).start().waitFor();
            } catch (Exception ignored) {}
        }, "myclient-mediakey");
        t.setDaemon(true);
        t.start();
    }

    public static String title() { return title.isEmpty() ? "Nothing playing" : title; }
    public static String artist() { return title.isEmpty() ? "Play something on YT Music" : (artist.isEmpty() ? "Unknown artist" : artist); }
    public static boolean isPlaying() { return playing; }
    public static String rawTitle() { return title; }
    public static String rawArtist() { return artist; }
    public static long durationMs() { return end * 1000L; }
    public static long positionMs() {
        double p = pos + (playing ? (System.currentTimeMillis() - stamp) / 1000.0 : 0);
        if (end > 0) p = Math.min(p, end);
        return (long) (p * 1000);
    }

    public static float progress() {
        if (end <= 0) return 0f;
        double p = pos + (playing ? (System.currentTimeMillis() - stamp) / 1000.0 : 0);
        return (float) Math.max(0, Math.min(1, p / end));
    }
}
