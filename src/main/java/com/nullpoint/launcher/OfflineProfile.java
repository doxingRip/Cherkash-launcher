package com.nullpoint.launcher;

public record OfflineProfile(String name) {
    public OfflineProfile {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Minecraft nickname must contain 3-16 letters, digits or underscores");
        }
    }
}
