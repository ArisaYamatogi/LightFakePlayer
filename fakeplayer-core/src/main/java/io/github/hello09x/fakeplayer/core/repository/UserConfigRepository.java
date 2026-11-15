package io.github.hello09x.fakeplayer.core.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.repository.model.Feature;
import io.github.hello09x.fakeplayer.core.repository.model.UserConfig;
import io.github.hello09x.fakeplayer.core.storage.PluginDataStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Singleton
public class UserConfigRepository {

    private final PluginDataStore store;

    @Inject
    public UserConfigRepository(PluginDataStore store) {
        this.store = store;
    }

    public @Nullable UserConfig selectByPlayerIdAndKey(@NotNull UUID playerId, @NotNull Feature feature) {
        var value = this.store.getFeature(playerId, feature);
        if (value == null) {
            return null;
        }
        return new UserConfig(null, playerId, feature, value);
    }

    public int saveOrUpdate(@NotNull UserConfig config) {
        this.store.setFeature(config.playerId(), config.key(), config.value());
        return 1;
    }

    public @NotNull List<UserConfig> selectByPlayerId(@NotNull UUID playerId) {
        return this.store.getFeatures(playerId)
                        .entrySet()
                        .stream()
                        .map(entry -> new UserConfig(null, playerId, entry.getKey(), entry.getValue()))
                        .toList();
    }

}
