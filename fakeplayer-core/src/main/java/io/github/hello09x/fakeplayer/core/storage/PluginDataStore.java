package io.github.hello09x.fakeplayer.core.storage;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.repository.model.Feature;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Logger;

@Singleton
public class PluginDataStore {

    private final static Logger log = Main.getInstance().getLogger();

    private final File file;

    private YamlConfiguration data = new YamlConfiguration();

    private final Map<String, UUID> profileUuids = new HashMap<>();

    private final Set<UUID> usedProfileUuids = new HashSet<>();

    private final Map<UUID, String> ownerNames = new HashMap<>();

    private final Map<UUID, Map<String, OwnedFakeplayer>> ownedFakeplayers = new HashMap<>();

    @Inject
    public PluginDataStore() {
        var folder = Main.getInstance().getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Could not create data folder " + folder.getAbsolutePath());
        }

        this.file = new File(folder, "data.yml");
        this.load();
    }

    private synchronized void load() {
        if (!file.exists()) {
            return;
        }

        try {
            data.load(file);
            this.validateSections();
        } catch (Exception e) {
            // 绝不能带着空数据继续运行: 下一次 save 会覆盖损坏但可能仍可恢复的原文件
            var backup = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
            try {
                Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                throw new IllegalStateException("Could not read or back up data.yml, refusing to overwrite it", e);
            }
            log.severe("Could not load data.yml (%s); the broken file was moved to %s and a new file will be created"
                               .formatted(e.getMessage(), backup.getName()));
            data = new YamlConfiguration();
            return;
        }

        var profiles = data.getConfigurationSection("profiles");
        if (profiles != null) {
            for (var key : profiles.getKeys(false)) {
                var raw = profiles.getString(key + ".uuid");
                if (raw == null) {
                    continue;
                }
                try {
                    var uuid = UUID.fromString(raw);
                    profileUuids.put(key.toLowerCase(Locale.ROOT), uuid);
                    usedProfileUuids.add(uuid);
                } catch (IllegalArgumentException e) {
                    log.warning("Ignoring invalid profile uuid: " + raw);
                }
            }
        }

        var owners = data.getConfigurationSection("ownership");
        if (owners != null) {
            for (var key : owners.getKeys(false)) {
                UUID ownerId;
                try {
                    ownerId = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                ownerNames.put(ownerId, owners.getString(key + ".name", key));
                var byName = new HashMap<String, OwnedFakeplayer>();
                var players = owners.getConfigurationSection(key + ".players");
                if (players != null) {
                    for (var id : players.getKeys(false)) {
                        var name = players.getString(id + ".name", id);
                        var raw = players.getString(id + ".uuid");
                        if (raw == null) {
                            continue;
                        }
                        try {
                            byName.put(id.toLowerCase(Locale.ROOT), new OwnedFakeplayer(
                                    name,
                                    UUID.fromString(raw),
                                    players.getBoolean(id + ".active", false)
                            ));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                ownedFakeplayers.put(ownerId, byName);
            }
        }
    }

    private void validateSections() {
        for (var key : List.of("profiles", "ownership", "features", "skins")) {
            if (data.contains(key) && data.getConfigurationSection(key) == null) {
                throw new IllegalArgumentException("Invalid section in data.yml: " + key);
            }
        }
    }

    private synchronized void save() {
        var tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            data.save(tmp);
            Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save data.yml", e);
        }
    }

    public synchronized @Nullable UUID getProfileUuid(@NotNull String name) {
        return profileUuids.get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized boolean hasProfileUuid(@NotNull UUID uuid) {
        return usedProfileUuids.contains(uuid);
    }

    public synchronized void saveProfile(@NotNull String name, @NotNull UUID uuid) {
        var key = name.toLowerCase(Locale.ROOT);
        data.set("profiles." + key + ".name", name);
        data.set("profiles." + key + ".uuid", uuid.toString());
        profileUuids.put(key, uuid);
        usedProfileUuids.add(uuid);
        this.save();
    }

    public synchronized @Nullable String getFeature(@NotNull UUID playerId, @NotNull Feature feature) {
        return data.getString("features." + playerId + "." + feature.name());
    }

    public synchronized @NotNull Map<Feature, String> getFeatures(@NotNull UUID playerId) {
        var section = data.getConfigurationSection("features." + playerId);
        if (section == null) {
            return Collections.emptyMap();
        }

        var result = new EnumMap<Feature, String>(Feature.class);
        for (var key : section.getKeys(false)) {
            try {
                result.put(Feature.valueOf(key), section.getString(key));
            } catch (IllegalArgumentException ignored) {
                // Ignore settings of features that no longer exist
            }
        }
        return result;
    }

    public synchronized void setFeature(@NotNull UUID playerId, @NotNull Feature feature, @NotNull String value) {
        data.set("features." + playerId + "." + feature.name(), value);
        this.save();
    }

    public synchronized @Nullable UUID getSkinTarget(@NotNull UUID creatorId, @NotNull UUID fakeplayerId) {
        var raw = data.getString("skins." + creatorId + "." + fakeplayerId);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public synchronized void setSkinTarget(@NotNull UUID creatorId, @NotNull UUID fakeplayerId, @NotNull UUID targetId) {
        data.set("skins." + creatorId + "." + fakeplayerId, targetId.toString());
        this.save();
    }

    public synchronized @Nullable OwnedFakeplayer getOwned(@NotNull UUID ownerId, @NotNull String name) {
        var players = ownedFakeplayers.get(ownerId);
        return players == null ? null : players.get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized @Nullable OwnedFakeplayer findOwned(@NotNull String name) {
        var key = name.toLowerCase(Locale.ROOT);
        for (var players : ownedFakeplayers.values()) {
            var owned = players.get(key);
            if (owned != null) {
                return owned;
            }
        }
        return null;
    }

    public synchronized @Nullable UUID findOwnerId(@NotNull String name) {
        var key = name.toLowerCase(Locale.ROOT);
        for (var entry : ownedFakeplayers.entrySet()) {
            if (entry.getValue().containsKey(key)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized boolean hasActive(@NotNull String name) {
        var key = name.toLowerCase(Locale.ROOT);
        for (var players : ownedFakeplayers.values()) {
            var owned = players.get(key);
            if (owned != null && owned.active()) {
                return true;
            }
        }
        return false;
    }

    public synchronized @NotNull List<OwnedFakeplayer> getOwnedFakeplayers(@NotNull UUID ownerId) {
        var players = ownedFakeplayers.get(ownerId);
        return players == null ? Collections.emptyList() : List.copyOf(players.values());
    }

    public synchronized int countActive(@NotNull UUID ownerId) {
        var players = ownedFakeplayers.get(ownerId);
        if (players == null) {
            return 0;
        }
        return (int) players.values().stream().filter(OwnedFakeplayer::active).count();
    }

    public synchronized void reserve(
            @NotNull UUID ownerId,
            @NotNull String ownerName,
            @NotNull String name,
            @NotNull UUID uuid
    ) {
        var key = name.toLowerCase(Locale.ROOT);
        ownerNames.put(ownerId, ownerName);
        ownedFakeplayers.computeIfAbsent(ownerId, ignored -> new HashMap<>())
                        .put(key, new OwnedFakeplayer(name, uuid, true));
        this.saveOwnership();
    }

    public synchronized void release(@NotNull UUID ownerId, @NotNull String name) {
        var players = ownedFakeplayers.get(ownerId);
        if (players == null) {
            return;
        }
        var key = name.toLowerCase(Locale.ROOT);
        var owned = players.get(key);
        if (owned == null || !owned.active()) {
            return;
        }
        players.put(key, new OwnedFakeplayer(owned.name(), owned.uuid(), false));
        this.saveOwnership();
    }

    public synchronized void releaseAll() {
        var changed = false;
        for (var entry : ownedFakeplayers.entrySet()) {
            for (var playerEntry : entry.getValue().entrySet()) {
                var owned = playerEntry.getValue();
                if (owned.active()) {
                    playerEntry.setValue(new OwnedFakeplayer(owned.name(), owned.uuid(), false));
                    changed = true;
                }
            }
        }
        if (changed) {
            this.saveOwnership();
        }
    }

    private void saveOwnership() {
        data.set("ownership", null);
        for (var entry : ownedFakeplayers.entrySet()) {
            var ownerId = entry.getKey();
            var path = "ownership." + ownerId;
            data.set(path + ".name", ownerNames.getOrDefault(ownerId, ownerId.toString()));
            for (var owned : entry.getValue().values()) {
                var playerPath = path + ".players." + owned.name().toLowerCase(Locale.ROOT);
                data.set(playerPath + ".name", owned.name());
                data.set(playerPath + ".uuid", owned.uuid().toString());
                data.set(playerPath + ".active", owned.active());
            }
        }
        this.save();
    }

    public record OwnedFakeplayer(
            @NotNull String name,
            @NotNull UUID uuid,
            boolean active
    ) {

    }

}
