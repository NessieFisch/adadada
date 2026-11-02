package com.myclient.sky;

public enum CursorStyle {
    DOT("Dot"), RING("Ring"), CROSS("Cross"), ARROW("Arrow");
    public final String label;
    CursorStyle(String label) { this.label = label; }
}
