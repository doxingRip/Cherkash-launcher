package com.nullpoint.launcher;

public record GameVersion(String id, JavaRuntime runtime) {
    @Override public String toString() { return id; }
}

enum JavaRuntime {
    JAVA_8(8), JAVA_21(21), JAVA_25(25);
    final int major;
    JavaRuntime(int major) { this.major = major; }
}
