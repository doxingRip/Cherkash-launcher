package com.nullpoint.launcher;

import java.util.UUID;

public record LaunchProfile(String name, UUID uuid, boolean authenticated) {
    public static LaunchProfile offline(String name) {
        return new LaunchProfile(name, OfflineUuid.forName(name), false);
    }
}
