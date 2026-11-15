package io.github.hello09x.fakeplayer.core.repository;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.repository.model.FakePlayerSkin;
import io.github.hello09x.fakeplayer.core.storage.PluginDataStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
public class FakeplayerSkinRepository {

    private final PluginDataStore store;

    @Inject
    public FakeplayerSkinRepository(PluginDataStore store) {
        this.store = store;
    }

    @CanIgnoreReturnValue
    public int insertOrUpdate(@NotNull FakePlayerSkin skin) {
        this.store.setSkinTarget(skin.creatorId(), skin.playerId(), skin.targetId());
        return 1;
    }

    public @Nullable FakePlayerSkin selectByCreatorIdAndPlayerId(@NotNull UUID creatorId, @NotNull UUID playerId) {
        var targetId = this.store.getSkinTarget(creatorId, playerId);
        if (targetId == null) {
            return null;
        }
        return new FakePlayerSkin(playerId, creatorId, targetId);
    }

}
