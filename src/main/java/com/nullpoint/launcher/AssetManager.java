package com.nullpoint.launcher;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;

final class AssetManager {
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private AssetManager() {}

    static void prepare(JsonObject meta, Path gameDir) throws Exception {
        if (!meta.has("assetIndex")) return;
        JsonObject index = meta.getAsJsonObject("assetIndex");
        Path indexes = gameDir.resolve("assets/indexes");
        Path objects = gameDir.resolve("assets/objects");
        Files.createDirectories(indexes); Files.createDirectories(objects);
        Path indexFile = indexes.resolve(index.get("id").getAsString() + ".json");
        download(index.get("url").getAsString(), indexFile, index.has("sha1") ? index.get("sha1").getAsString() : null);
        JsonObject objectsJson = JsonParser.parseString(Files.readString(indexFile)).getAsJsonObject().getAsJsonObject("objects");
        if (objectsJson == null) return;
        for (var e : objectsJson.entrySet()) {
            JsonObject object = e.getValue().getAsJsonObject();
            String hash = object.get("hash").getAsString();
            Path target = objects.resolve(hash.substring(0,2)).resolve(hash);
            download("https://resources.download.minecraft.net/" + hash.substring(0,2) + "/" + hash, target, hash);
        }
    }

    private static void download(String url, Path target, String sha1) throws Exception {
        if (Files.exists(target) && (sha1 == null || sha1(target).equalsIgnoreCase(sha1))) return;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<Path> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(tmp));
        if (r.statusCode() / 100 != 2) throw new IllegalStateException("Asset download failed: HTTP " + r.statusCode());
        if (sha1 != null && !sha1(tmp).equalsIgnoreCase(sha1)) { Files.deleteIfExists(tmp); throw new IllegalStateException("Asset SHA-1 mismatch: " + target); }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    private static String sha1(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (var in = Files.newInputStream(p)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) md.update(b, 0, n); }
        StringBuilder s = new StringBuilder(); for (byte b : md.digest()) s.append(String.format("%02x", b)); return s.toString();
    }
}
