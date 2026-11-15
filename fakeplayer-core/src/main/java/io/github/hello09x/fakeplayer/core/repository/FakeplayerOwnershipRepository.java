package io.github.hello09x.fakeplayer.core.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.storage.PluginDataStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
public class FakeplayerOwnershipRepository {

    private final PluginDataStore store;

    @Inject
    public FakeplayerOwnershipRepository(PluginDataStore store) {
        this.store = store;
    }

    public void resetActive() {
        this.store.releaseAll();
    }

    public void reserve(@NotNull UUID ownerId, @NotNull String ownerName, @NotNull String name, @NotNull UUID uuid) {
        this.store.reserve(ownerId, ownerName, name, uuid);
    }

    public void release(@NotNull UUID ownerId, @NotNull String name) {
        this.store.release(ownerId, name);
    }

    public boolean isReservedByOther(@NotNull UUID ownerId, @NotNull String name) {
        var owner = this.store.findOwnerId(name);
        return owner != null && !owner.equals(ownerId);
    }

    public boolean hasActive(@NotNull String name) {
        return this.store.hasActive(name);
    }

    public @Nullable UUID findOwnerId(@NotNull String name) {
        return this.store.findOwnerId(name);
    }

    public int countActive(@NotNull UUID ownerId) {
        return this.store.countActive(ownerId);
    }

}
