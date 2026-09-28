package com.nullpoint.launcher;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

final class LauncherService {
    private static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    static void launch(GameVersion version, int ramMb, Path instancesRoot, OfflineProfile profile) throws Exception {
        Path instance = instancesRoot.resolve(version.id());
        Path gameDir = instance.resolve(".minecraft");
        Files.createDirectories(gameDir);
        JsonObject meta = resolveVersion(version.id());
        Path versionDir = gameDir.resolve("versions").resolve(version.id());
        Path client = versionDir.resolve(version.id() + ".jar");
        Files.createDirectories(versionDir);
        downloadVerified(meta.getAsJsonObject("downloads").getAsJsonObject("client"), client);

        Path libraries = gameDir.resolve("libraries");
        List<Path> classpath = new ArrayList<>();
        Path natives = versionDir.resolve("natives");
        Files.createDirectories(natives);
        Set<String> seen = new HashSet<>();
        JsonArray libs = meta.getAsJsonArray("libraries");
        if (libs != null) for (JsonElement e : libs) {
            JsonObject lib = e.getAsJsonObject();
            if (!allowed(lib.getAsJsonArray("rules"))) continue;
            JsonObject downloads = lib.getAsJsonObject("downloads");
            if (downloads == null) continue;
            JsonObject artifact = downloads.getAsJsonObject("artifact");
            if (artifact != null) {
                Path p = libraries.resolve(artifact.get("path").getAsString());
                downloadVerified(artifact, p);
                if (seen.add(p.toString())) classpath.add(p);
            }
            String nativeKey = nativeClassifierKey();
            JsonObject classifiers = downloads.getAsJsonObject("classifiers");
            if (classifiers != null && classifiers.has(nativeKey)) {
                JsonObject nativeDownload = classifiers.getAsJsonObject(nativeKey);
                Path p = libraries.resolve(nativeDownload.get("path").getAsString());
                downloadVerified(nativeDownload, p);
                extractNatives(p, natives);
            }
        }
        classpath.add(client);

        AssetManager.prepare(meta, gameDir);
        String java = RuntimeManager.java(version.runtime.major);
        String cp = String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList());
        UUID uuid = OfflineUuid.forName(profile.name());
        List<String> cmd = new ArrayList<>(List.of(java,
                "-Xms512M", "-Xmx" + Math.max(1024, ramMb) + "M",
                "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=50",
                "-XX:+DisableExplicitGC", "-Dfile.encoding=UTF-8",
                "-Djava.library.path=" + natives.toAbsolutePath(), "-Dminecraft.launcher.brand=Cherkash",
                "-cp", cp, "net.minecraft.client.main.Main",
                "--username", profile.name(), "--version", version.id(),
                "--gameDir", gameDir.toAbsolutePath().toString(),
                "--assetsDir", gameDir.resolve("assets").toAbsolutePath().toString(),
                "--uuid", uuid.toString(), "--accessToken", "0", "--userType", "legacy", "--versionType", "Cherkash"));
        if (meta.has("assetIndex")) { cmd.add("--assetIndex"); cmd.add(meta.getAsJsonObject("assetIndex").get("id").getAsString()); }
        new ProcessBuilder(cmd).directory(gameDir.toFile()).inheritIO().start();
    }

    private static boolean allowed(JsonArray rules) {
        if (rules == null) return true;
        boolean enabled = false;
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            boolean match = true;
            if (r.has("os")) {
                JsonObject os = r.getAsJsonObject("os");
                if (os.has("name")) match &= os.get("name").getAsString().equals(osName());
                if (os.has("arch")) match &= System.getProperty("os.arch").contains(os.get("arch").getAsString());
            }
            if (match && r.has("action")) enabled = "allow".equals(r.get("action").getAsString());
        }
        return enabled;
    }
    private static String osName() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "windows" : System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac") ? "osx" : "linux"; }
    private static String nativeClassifierKey() { return osName().equals("windows") ? "natives-windows" : osName().equals("osx") ? "natives-osx" : "natives-linux"; }

    private static void extractNatives(Path jar, Path out) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var e = entries.nextElement();
                if (e.isDirectory() || !e.getName().startsWith("META-INF/") && e.getName().contains("/")) {
                    String name = Path.of(e.getName()).getFileName().toString();
                    if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")) {
                        Files.copy(zip.getInputStream(e), out.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static JsonObject resolveVersion(String id) throws Exception {
        JsonObject manifest = getJson(MANIFEST);
        for (JsonElement e : manifest.getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            if (id.equals(v.get("id").getAsString())) return getJson(URI.create(v.get("url").getAsString()));
        }
        throw new IOException("Minecraft version not found: " + id);
    }
    private static JsonObject getJson(URI uri) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " while downloading " + uri);
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }
    private static void downloadVerified(JsonObject d, Path target) throws Exception {
        if (Files.exists(target) && d.has("sha1") && sha1(target).equalsIgnoreCase(d.get("sha1").getAsString())) return;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<Path> r = HTTP.send(HttpRequest.newBuilder(URI.create(d.get("url").getAsString())).GET().build(), HttpResponse.BodyHandlers.ofFile(tmp));
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " while downloading " + d.get("url"));
        if (d.has("sha1") && !sha1(tmp).equalsIgnoreCase(d.get("sha1").getAsString())) { Files.deleteIfExists(tmp); throw new IOException("SHA-1 verification failed: " + target); }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    private static String sha1(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(p)) { byte[] b = new byte[1024 * 1024]; int n; while ((n = in.read(b)) > 0) md.update(b, 0, n); }
        StringBuilder s = new StringBuilder(); for (byte b : md.digest()) s.append(String.format("%02x", b)); return s.toString();
    }
}
