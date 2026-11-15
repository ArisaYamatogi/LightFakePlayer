package io.github.hello09x.fakeplayer.core.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.storage.PluginDataStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
public class FakeplayerProfileRepository {

    private final PluginDataStore store;

    @Inject
    public FakeplayerProfileRepository(PluginDataStore store) {
        this.store = store;
    }

    public void insert(@NotNull String name, @NotNull UUID uuid) {
        this.store.saveProfile(name, uuid);
    }

    public boolean existsByUUID(@NotNull UUID uuid) {
        return this.store.hasProfileUuid(uuid);
    }

    public @Nullable UUID selectUUIDByName(@NotNull String name) {
        return this.store.getProfileUuid(name);
    }

}
