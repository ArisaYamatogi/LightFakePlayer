package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.entity.Fakeplayer;
import io.github.hello09x.fakeplayer.core.repository.FakeplayerOwnershipRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.stream.Stream;

@Singleton
public class FakeplayerList {

    private final Map<String, Fakeplayer> playersByName = new HashMap<>();

    private final Map<UUID, Fakeplayer> playersByUUID = new HashMap<>();

    private final Map<UUID, List<Fakeplayer>> playersByOwner = new HashMap<>();

    private final FakeplayerOwnershipRepository ownershipRepository;

    @Inject
    public FakeplayerList(FakeplayerOwnershipRepository ownershipRepository) {
        this.ownershipRepository = ownershipRepository;
    }

    public void add(@NotNull Fakeplayer player) {
        this.playersByName.put(player.getName().toLowerCase(Locale.ROOT), player);
        this.playersByUUID.put(player.getUUID(), player);
        this.playersByOwner.computeIfAbsent(player.getCreatorId(), key -> new LinkedList<>()).add(player);
    }

    public @Nullable Fakeplayer getByName(@NotNull String name) {
        return Optional
                .ofNullable(this.playersByName.get(name.toLowerCase(Locale.ROOT)))
                .map(this::checkOnline)
                .orElse(null);
    }

    public @Nullable Fakeplayer getByUUID(@NotNull UUID uuid) {
        return Optional.ofNullable(this.playersByUUID.get(uuid)).map(this::checkOnline).orElse(null);
    }

    public @NotNull @Unmodifiable List<Fakeplayer> getByCreator(@NotNull UUID creatorId) {
        return Optional
                .ofNullable(this.playersByOwner.get(creatorId))
                .map(Collections::unmodifiableList)
                .orElse(Collections.emptyList());
    }

    public void remove(@NotNull Fakeplayer player) {
        this.playersByName.remove(player.getName().toLowerCase(Locale.ROOT));
        this.playersByUUID.remove(player.getUUID());
        Optional.ofNullable(this.playersByOwner.get(player.getCreatorId()))
                .ifPresent(players -> players.remove(player));
    }

    /**
     * 通过 UUID 移除假人，不检查在线状态
     *
     * @param uuid UUID
     * @return 被移除的假人
     */
    public @Nullable Fakeplayer removeByUUID(@NotNull UUID uuid) {
        var player = this.playersByUUID.get(uuid);
        if (player == null) {
            return null;
        }
        this.remove(player);
        return player;
    }

    public int countByCreator(@NotNull UUID creatorId) {
        return Optional
                .ofNullable(this.playersByOwner.get(creatorId))
                .map(List::size)
                .orElse(0);
    }

    public @NotNull @Unmodifiable List<Fakeplayer> getAll() {
        return List.copyOf(this.playersByUUID.values());
    }

    private @Nullable Fakeplayer checkOnline(@NotNull Fakeplayer player) {
        if (!player.isOnline()) {
            this.remove(player);
            this.ownershipRepository.release(player.getCreatorId(), player.getName());
            return null;
        }
        return player;
    }

    public @NotNull Stream<Fakeplayer> stream() {
        return this.playersByUUID.values().stream();
    }

    public int getSize() {
        return this.playersByUUID.size();
    }

}
