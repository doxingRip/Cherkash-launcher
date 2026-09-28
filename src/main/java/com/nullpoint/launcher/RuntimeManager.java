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
        if (!isWindows() || !System.getProperty("os.arch").contains("64")) throw new IOException("Automatic Java runtime installation currently supports Windows x64. Set CHERKASH_JAVA_" + major + " manually on this platform.");
        installTemurin(major, root);
        if (!Files.isRegularFile(executable)) throw new IOException("Java " + major + " runtime installation completed but java.exe was not found.");
        return executable.toString();
    }

    private static void installTemurin(int major, Path root) throws Exception {
        Files.createDirectories(root.getParent());
        URI api = URI.create("https://api.adoptium.net/v3/assets/latest/" + major + "/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse");
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(api).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("Unable to find Java " + major + " runtime: HTTP " + response.statusCode());
        JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
        if (arr.isEmpty()) throw new IOException("No Windows x64 Temurin JRE found for Java " + major);
        String url = arr.get(0).getAsJsonObject().getAsJsonObject("binary").getAsJsonObject("package").get("link").getAsString();
        Path zip = root.getParent().resolve("java" + major + ".zip.part");
        HttpResponse<Path> dl = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(zip));
        if (dl.statusCode() / 100 != 2) throw new IOException("Java runtime download failed: HTTP " + dl.statusCode());
        Path temp = root.getParent().resolve("java" + major + ".extract");
        deleteTree(temp); Files.createDirectories(temp);
        try (ZipFile z = new ZipFile(zip.toFile())) {
            var entries = z.entries();
            while (entries.hasMoreElements()) {
                var e = entries.nextElement();
                Path out = temp.resolve(e.getName()).normalize();
                if (!out.startsWith(temp)) throw new IOException("Invalid runtime archive");
                if (e.isDirectory()) Files.createDirectories(out); else { Files.createDirectories(out.getParent()); try (InputStream in = z.getInputStream(e)) { Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING); } }
            }
        }
        Path actual = temp;
        try (var s = Files.list(temp)) { var first = s.findFirst().orElse(temp); if (!Files.isRegularFile(first.resolve("bin").resolve("java.exe"))) actual = first; }
        deleteTree(root); Files.move(actual, root, StandardCopyOption.REPLACE_EXISTING);
        deleteTree(temp); Files.deleteIfExists(zip);
    }
    private static void deleteTree(Path p) throws IOException { if (!Files.exists(p)) return; try (var s = Files.walk(p)) { s.sorted(Comparator.reverseOrder()).forEach(x -> { try { Files.deleteIfExists(x); } catch (IOException e) { throw new UncheckedIOException(e); } }); } catch (UncheckedIOException e) { throw e.getCause(); } }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }
}
