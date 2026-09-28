package com.nullpoint.launcher;

import com.google.gson.*;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;

final class LauncherService {
    private static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    static void launch(GameVersion version, int ramMb, Path instancesRoot) throws Exception {
        Path instance = instancesRoot.resolve(version.id());
        Path gameDir = instance.resolve(".minecraft");
        Files.createDirectories(gameDir);
        Path jsonPath = instance.resolve(version.id() + ".json");
        JsonObject meta = resolveVersion(version.id());
        downloadVerified(meta.get("downloads").getAsJsonObject().getAsJsonObject("client"),
                jsonPath);

        Path client = gameDir.resolve("versions").resolve(version.id()).resolve(version.id() + ".jar");
        Files.createDirectories(client.getParent());
        downloadVerified(meta.getAsJsonObject("downloads").getAsJsonObject("client"), client);

        Path libraries = gameDir.resolve("libraries");
        List<Path> classpath = new ArrayList<>();
        JsonArray libs = meta.getAsJsonArray("libraries");
        if (libs != null) for (JsonElement e : libs) {
            JsonObject lib = e.getAsJsonObject();
            if (!lib.has("downloads") || !lib.getAsJsonObject("downloads").has("artifact")) continue;
            JsonObject artifact = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
            Path p = libraries.resolve(artifact.get("path").getAsString());
            downloadVerified(artifact, p);
            classpath.add(p);
        }
        classpath.add(client);

        String java = locateJava(version.runtime.major);
        String cp = String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList());
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.add("-Xms512M");
        cmd.add("-Xmx" + ramMb + "M");
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:+ParallelRefProcEnabled");
        cmd.add("-XX:MaxGCPauseMillis=50");
        cmd.add("-XX:+DisableExplicitGC");
        cmd.add("-XX:+AlwaysPreTouch");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dminecraft.launcher.brand=Cherkash");
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add("net.minecraft.client.main.Main");
        cmd.add("--version"); cmd.add(version.id());
        cmd.add("--gameDir"); cmd.add(gameDir.toAbsolutePath().toString());
        cmd.add("--assetsDir"); cmd.add(gameDir.resolve("assets").toAbsolutePath().toString());
        cmd.add("--assetIndex");
        if (meta.has("assetIndex")) cmd.add(meta.getAsJsonObject("assetIndex").get("id").getAsString());
        cmd.add("--accessToken"); cmd.add("0");

        new ProcessBuilder(cmd).directory(gameDir.toFile()).inheritIO().start();
    }

    private static JsonObject resolveVersion(String id) throws Exception {
        JsonObject manifest = JsonParser.parseString(HTTP.send(
                HttpRequest.newBuilder(MANIFEST).GET().build(), HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
        for (JsonElement e : manifest.getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            if (id.equals(v.get("id").getAsString())) {
                return JsonParser.parseString(HTTP.send(HttpRequest.newBuilder(URI.create(v.get("url").getAsString())).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
            }
        }
        throw new IOException("Minecraft version not found in Mojang manifest: " + id);
    }

    private static void downloadVerified(JsonObject download, Path target) throws Exception {
        if (Files.exists(target) && download.has("sha1") && sha1(target).equalsIgnoreCase(download.get("sha1").getAsString())) return;
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(download.get("url").getAsString())).GET().build();
        HTTP.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
        if (download.has("sha1") && !sha1(tmp).equalsIgnoreCase(download.get("sha1").getAsString())) {
            Files.deleteIfExists(tmp);
            throw new IOException("SHA-1 verification failed: " + target);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String sha1(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] b = new byte[1024 * 1024]; int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
        }
        StringBuilder s = new StringBuilder();
        for (byte b : md.digest()) s.append(String.format("%02x", b));
        return s.toString();
    }

    private static String locateJava(int major) throws IOException {
        String configured = System.getenv("CHERKASH_JAVA_" + major);
        if (configured != null && Files.isRegularFile(Path.of(configured))) return configured;
        String current = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        if (major == Runtime.version().feature()) return current;
        throw new IOException("Java " + major + " is required. Set CHERKASH_JAVA_" + major + " to its java executable; automatic runtime installation is planned for the runtime manager.");
    }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }
}
