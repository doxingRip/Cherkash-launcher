package com.nullpoint.launcher;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

final class RuntimeManager {
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private RuntimeManager() {}

    static String java(int major) throws Exception {
        String env = System.getenv("CHERKASH_JAVA_" + major);
        if (env != null && Files.isRegularFile(Path.of(env))) return env;
        Path root = Path.of(System.getProperty("user.home"), ".cherkash", "runtimes", "java" + major);
        Path executable = root.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(executable)) return executable.toString();
        if (Runtime.version().feature() == major) return Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        throw new IOException("Java " + major + " is required. Set CHERKASH_JAVA_" + major + " or install the bundled runtime.");
    }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }
}
