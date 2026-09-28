package com.nullpoint.launcher;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class OfflineUuid {
    private OfflineUuid() {}

    static UUID forName(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
