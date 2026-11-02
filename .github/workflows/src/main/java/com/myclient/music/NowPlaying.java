package com.myclient.music;

/** What is playing right now: a local file or whatever Windows reports (Spotify, YouTube Music, ...). */
public final class NowPlaying {
    public static String title() { return MusicPlayer.isSystem() ? SystemMedia.rawTitle() : MusicPlayer.rawTitle(); }
    public static String artist() { return MusicPlayer.isSystem() ? SystemMedia.rawArtist() : MusicPlayer.rawArtist(); }
    public static long positionMs() { return MusicPlayer.isSystem() ? SystemMedia.positionMs() : MusicPlayer.positionMs(); }
    public static long durationMs() { return MusicPlayer.isSystem() ? SystemMedia.durationMs() : MusicPlayer.durationMs(); }
    public static boolean has() { String t = title(); return t != null && !t.isEmpty(); }
}
