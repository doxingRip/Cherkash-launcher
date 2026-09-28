package com.nullpoint.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class OfflineProfileStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private OfflineProfileStore() {}

    static List<OfflineProfile> load(Path file) throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        ProfileFile data = GSON.fromJson(Files.readString(file), ProfileFile.class);
        List<OfflineProfile> result = new ArrayList<>();
        if (data != null && data.profiles != null) {
            for (String name : data.profiles) {
                try { result.add(new OfflineProfile(name)); } catch (IllegalArgumentException ignored) { }
            }
        }
        return result;
    }

    static void save(Path file, List<OfflineProfile> profiles) throws IOException {
        Files.createDirectories(file.getParent());
        ProfileFile data = new ProfileFile();
        data.profiles = profiles.stream().map(OfflineProfile::name).distinct().toList();
        Files.writeString(file, GSON.toJson(data));
    }

    private static final class ProfileFile {
        List<String> profiles = new ArrayList<>();
    }
}
