package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.devtools.command.exception.CommandException;
import io.github.hello09x.devtools.core.utils.Exceptions;
import io.github.hello09x.devtools.core.utils.MetadataUtils;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.constant.MetadataKeys;
import io.github.hello09x.fakeplayer.core.entity.Fakeplayer;
import io.github.hello09x.fakeplayer.core.entity.SpawnOption;
import io.github.hello09x.fakeplayer.core.manager.feature.FakeplayerFeatureManager;
import io.github.hello09x.fakeplayer.core.manager.naming.NameManager;
import io.github.hello09x.fakeplayer.core.repository.FakeplayerOwnershipRepository;
import io.github.hello09x.fakeplayer.core.repository.model.Feature;
import io.github.hello09x.fakeplayer.core.storage.SecretStore;
import io.github.hello09x.fakeplayer.core.util.AddressUtils;
import io.github.hello09x.fakeplayer.core.util.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;

@Singleton
public class FakeplayerManager {

    public final static String REMOVAL_REASON_PREFIX = "[fakeplayer] ";

    private final static Logger log = Main.getInstance().getLogger();

    private final NameManager nameManager;
    private final FakeplayerList playerList;
    private final FakeplayerFeatureManager featureManager;
    private final FakeplayerOwnershipRepository ownershipRepository;
    private final SecretStore secretStore;
    private final FakeplayerConfig config;
    private final ScheduledExecutorService lagMonitor;

    @Inject
    public FakeplayerManager(
            NameManager nameManager,
            FakeplayerList playerList,
            FakeplayerFeatureManager featureManager,
            FakeplayerOwnershipRepository ownershipRepository,
            SecretStore secretStore,
            FakeplayerConfig config
    ) {
        this.nameManager = nameManager;
        this.playerList = playerList;
        this.featureManager = featureManager;
        this.ownershipRepository = ownershipRepository;
        this.secretStore = secretStore;
        this.config = config;

        this.lagMonitor = Executors.newSingleThreadScheduledExecutor();
        this.lagMonitor.scheduleWithFixedDelay(() -> {
                                                   if (Bukkit.getServer().getTPS()[1] < config.getKaleTps()) {
                                                       Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                                                           if (this.removeAll("low tps") > 0) {
                                                               Bukkit.broadcast(translatable("fakeplayer.manager.remove-all-on-low-tps", GRAY, ITALIC));
                                                           }
                                                       });
                                                   }
                                               }, 0, 60, TimeUnit.SECONDS
        );
    }

    /**
     * 创建一个假人
     *
     * @param creator  创建者
     * @param id       自定义 ID
     * @param spawnAt  生成地点
     * @param lifespan 存活时间
     */
    public @NotNull CompletableFuture<Player> spawnAsync(
            @NotNull CommandSender creator,
            @NotNull String id,
            @NotNull Location spawnAt,
            long lifespan
    ) {
        this.checkLimit(creator);

        var validatedId = this.nameManager.validateFormat(id);
        return this.nameManager.checkLuckPerms(validatedId).thenCompose(ignored -> {
            var result = new CompletableFuture<Player>();
            Runnable spawn = () -> {
                try {
                    this.spawnOnMainThread(creator, validatedId, spawnAt, lifespan)
                        .whenComplete((player, throwable) -> {
                            if (throwable != null) {
                                result.completeExceptionally(throwable);
                            } else {
                                result.complete(player);
                            }
                        });
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            };
            if (Bukkit.isPrimaryThread()) {
                spawn.run();
            } else {
                Bukkit.getScheduler().runTask(Main.getInstance(), spawn);
            }
            return result;
        });
    }

    private @NotNull CompletableFuture<Player> spawnOnMainThread(
            @NotNull CommandSender creator,
            @NotNull String id,
            @NotNull Location spawnAt,
            long lifespan
    ) {
        // LuckPerms 查询是异步的, 期间限制可能已被其他请求占满
        this.checkLimit(creator);

        var sn = nameManager.getSpecifiedName(creator, id);
        var ownerId = NameManager.getCreatorId(creator);
        log.info("UUID of fake player %s is %s".formatted(sn.name(), sn.uuid()));

        this.ownershipRepository.reserve(ownerId, creator.getName(), sn.name(), sn.uuid());

        final Fakeplayer fp;
        try {
            fp = new Fakeplayer(
                    creator,
                    AddressUtils.getAddress(creator),
                    sn,
                    lifespan
            );
            this.playerList.add(fp);
        } catch (RuntimeException e) {
            this.ownershipRepository.release(ownerId, sn.name());
            throw e;
        }

        var target = fp.getPlayer();
        this.dispatchCommandsEarly(fp, this.config.getPreSpawnCommands());
        return CompletableFuture
                .supplyAsync(() -> {
                    var configs = featureManager.getFeatures(creator);
                    return new SpawnOption(
                            spawnAt,
                            configs.get(Feature.invulnerable).asBoolean(),
                            configs.get(Feature.collidable).asBoolean(),
                            configs.get(Feature.look_at_entity).asBoolean(),
                            configs.get(Feature.skin).asBoolean()
                    );
                })
                .thenComposeAsync(fp::spawnAsync)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        if (Bukkit.isPrimaryThread()) {
                            this.discardFailedSpawn(fp);
                        } else {
                            Bukkit.getScheduler().runTask(Main.getInstance(), () -> this.discardFailedSpawn(fp));
                        }
                        throw new CompletionException(throwable);
                    }
                    return target;
                });
    }

    private void discardFailedSpawn(@NotNull Fakeplayer fakeplayer) {
        if (fakeplayer.isOnline()) {
            fakeplayer.getPlayer().kick(text(REMOVAL_REASON_PREFIX + "spawn failed"));
        }
        this.playerList.remove(fakeplayer);
        this.ownershipRepository.release(fakeplayer.getCreatorId(), fakeplayer.getName());
    }

    /**
     * 获取一个假人
     *
     * @param creator 创建者
     * @param name    假人名称
     * @return 假人
     */
    public @Nullable Player get(@NotNull CommandSender creator, @NotNull String name) {
        return Optional
                .ofNullable(this.playerList.getByName(name))
                .filter(p -> p.isCreatedBy(creator))
                .map(Fakeplayer::getPlayer)
                .orElse(null);
    }

    /**
     * 根据名称获取假人
     *
     * @param name 名称
     * @return 假人
     */
    public @Nullable Player get(@NotNull String name) {
        return Optional
                .ofNullable(this.playerList.getByName(name))
                .map(Fakeplayer::getPlayer)
                .orElse(null);
    }

    /**
     * 获取一个假人的创建者, 如果这个玩家不是假人, 则为 {@code null}
     */
    public @Nullable String getCreatorName(@NotNull Player target) {
        return Optional
                .ofNullable(this.playerList.getByUUID(target.getUniqueId()))
                .map(Fakeplayer::getCreator)
                .map(CommandSender::getName)
                .orElse(null);
    }

    /**
     * 获取假人的创建者
     */
    public @Nullable CommandSender getCreator(@NotNull Player target) {
        return Optional.ofNullable(this.playerList.getByUUID(target.getUniqueId()))
                       .map(Fakeplayer::getCreator)
                       .map(creator -> {
                           if (creator instanceof Player p) {
                               return Bukkit.getPlayer(p.getUniqueId());
                           }
                           return creator;
                       })
                       .orElse(null);
    }

    /**
     * 获取假人的创建者的 UUID
     */
    public @Nullable UUID getCreatorId(@NotNull Player target) {
        return Optional.ofNullable(this.playerList.getByUUID(target.getUniqueId()))
                       .map(Fakeplayer::getCreatorId)
                       .orElse(null);
    }

    /**
     * 根据名称删除假人
     */
    public boolean remove(@NotNull String name, @Nullable String reason) {
        return this.remove(name, reason == null ? null : text(reason));
    }

    public boolean remove(@NotNull String name, @Nullable Component reason) {
        var target = this.get(name);
        if (target == null) {
            return false;
        }

        target.kick(textOfChildren(
                text("[fakeplayer] "),
                reason == null ? text("removed") : reason
        ));
        return true;
    }

    /**
     * 移除所有假人
     */
    public int removeAll(@Nullable String reason) {
        var targets = this.playerList.getAll();
        for (var target : targets) {
            if (target.isOnline()) {
                target.getPlayer().kick(text(REMOVAL_REASON_PREFIX + (reason == null ? "removed" : reason)));
            } else {
                this.playerList.remove(target);
                this.ownershipRepository.release(target.getCreatorId(), target.getName());
            }
        }
        return targets.size();
    }

    public @NotNull List<Player> getAll() {
        return this.getAll((Predicate<Player>) null);
    }

    public @NotNull List<Player> getAll(@Nullable Predicate<Player> predicate) {
        var stream = this.playerList
                .getAll()
                .stream()
                .map(Fakeplayer::getPlayer)
                .filter(Player::isOnline);
        if (predicate != null) {
            stream = stream.filter(predicate);
        }
        return stream.toList();
    }

    public @NotNull List<Fakeplayer> getAllFakeplayers() {
        return this.playerList.getAll();
    }

    /**
     * 清理假人
     */
    public void cleanup(@NotNull Player target) {
        var fakeplayer = this.playerList.removeByUUID(target.getUniqueId());
        if (fakeplayer == null) {
            return;
        }
        this.ownershipRepository.release(fakeplayer.getCreatorId(), fakeplayer.getName());
    }

    public @NotNull List<Player> getAll(@NotNull CommandSender creator) {
        return this.getAll(creator, null);
    }

    public @NotNull List<Player> getAll(@NotNull CommandSender creator, @Nullable Predicate<Player> predicate) {
        var ownerId = NameManager.getCreatorId(creator);
        var stream = this.playerList
                .getByCreator(ownerId)
                .stream()
                .map(Fakeplayer::getPlayer)
                .filter(Player::isOnline);
        if (predicate != null) {
            stream = stream.filter(predicate);
        }
        return stream.toList();
    }

    public int getSize() {
        return this.playerList.getSize();
    }

    /**
     * 判断一名玩家是否是假人
     */
    public boolean isFake(@NotNull Player target) {
        return this.playerList.getByUUID(target.getUniqueId()) != null;
    }

    public boolean isNotFake(@NotNull Player target) {
        return this.playerList.getByUUID(target.getUniqueId()) == null;
    }

    /**
     * 获取 IP 地址创建着多少个假人
     */
    public long countByAddress(@NotNull String address) {
        return this.playerList
                .stream()
                .filter(p -> p.getCreatorIp().equals(address))
                .count();
    }

    /**
     * 获取这个玩家创建了多少个假人
     */
    public int countByCreator(@NotNull CommandSender creator) {
        return this.playerList.countByCreator(NameManager.getCreatorId(creator));
    }

    public void setSelection(@NotNull Player creator, @Nullable Player target) {
        if (target == null) {
            creator.removeMetadata(MetadataKeys.SELECTION, Main.getInstance());
            return;
        }

        if (!this.isFake(target)) {
            return;
        }

        creator.setMetadata(MetadataKeys.SELECTION, new FixedMetadataValue(Main.getInstance(), target.getUniqueId()));
    }

    public @Nullable Player getSelection(@NotNull CommandSender creator) {
        if (!(creator instanceof Player p)) {
            return null;
        }
        if (!p.hasMetadata(MetadataKeys.SELECTION)) {
            return null;
        }

        var uuid = MetadataUtils
                .find(Main.getInstance(), p, MetadataKeys.SELECTION, UUID.class)
                .map(MetadataValue::value)
                .map(UUID.class::cast)
                .orElse(null);

        if (uuid == null) {
            return null;
        }

        var target = Optional.ofNullable(this.playerList.getByUUID(uuid)).map(Fakeplayer::getPlayer).orElse(null);
        if (target == null) {
            this.setSelection(p, null);
        }
        return target;
    }

    /**
     * 以假人身份执行命令
     */
    public void issueCommands(@NotNull Player target, @NotNull List<String> commands) {
        if (commands.isEmpty() || this.isNotFake(target)) {
            return;
        }

        var fakeplayer = this.playerList.getByUUID(target.getUniqueId());
        if (fakeplayer == null) {
            return;
        }

        var placeholders = this.placeholders(
                fakeplayer.getName(),
                fakeplayer.getUUID(),
                fakeplayer.getCreator().getName(),
                fakeplayer.getCreatorId()
        );

        for (var command : Commands.formatCommands(commands, placeholders)) {
            if (!target.performCommand(command)) {
                log.warning(target.getName() + " failed to execute command: " + Commands.mask(command, secretStore.getAuthmePassword()));
            } else {
                log.info(target.getName() + " issued command: " + Commands.mask(command, secretStore.getAuthmePassword()));
            }
        }
    }

    public void dispatchCommandsEarly(@NotNull Fakeplayer fp, @NotNull List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }

        var placeholders = this.placeholders(
                fp.getName(),
                fp.getUUID(),
                fp.getCreator().getName(),
                fp.getCreatorId()
        );
        for (var command : Commands.formatCommands(commands, placeholders)) {
            if (!Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command)) {
                log.warning("Failed to execute command for %s: %s".formatted(
                        fp.getName(),
                        Commands.mask(command, secretStore.getAuthmePassword())
                ));
            } else {
                log.info("Dispatched command: " + Commands.mask(command, secretStore.getAuthmePassword()));
            }
        }
    }

    /**
     * 以控制台身份对玩家执行命令
     */
    public void dispatchCommands(@NotNull Player player, @NotNull List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }

        var fakeplayer = this.playerList.getByUUID(player.getUniqueId());
        if (fakeplayer == null) {
            return;
        }

        this.dispatchCommands(
                player,
                commands,
                fakeplayer.getCreator().getName(),
                fakeplayer.getCreatorId()
        );
    }

    /**
     * 以控制台身份对玩家执行命令
     * <p>用于假人已经从列表移除之后仍需执行的延迟命令</p>
     */
    public void dispatchCommands(
            @NotNull Player player,
            @NotNull List<String> commands,
            @NotNull String creatorName,
            @NotNull UUID creatorId
    ) {
        if (commands.isEmpty()) {
            return;
        }

        var placeholders = this.placeholders(
                player.getName(),
                player.getUniqueId(),
                creatorName,
                creatorId
        );
        for (var command : Commands.formatCommands(commands, placeholders)) {
            if (!Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command)) {
                log.warning("Failed to execute command for %s: %s".formatted(
                        player.getName(),
                        Commands.mask(command, secretStore.getAuthmePassword())
                ));
            } else {
                log.info("Dispatched command: " + Commands.mask(command, secretStore.getAuthmePassword()));
            }
        }
    }

    private @NotNull Map<String, String> placeholders(
            @NotNull String fakeplayerName,
            @NotNull UUID fakeplayerId,
            @NotNull String creatorName,
            @NotNull UUID creatorId
    ) {
        var placeholders = new HashMap<String, String>(8);
        placeholders.put("%p", fakeplayerName);
        placeholders.put("%u", fakeplayerId.toString());
        placeholders.put("%c", creatorName);
        placeholders.put("%id%", fakeplayerName);
        placeholders.put("%owner%", creatorName);
        placeholders.put("%owner_uuid%", creatorId.toString());
        placeholders.put("%password%", secretStore.getAuthmePassword());
        return placeholders;
    }

    /**
     * 检测限制, 不满足条件则抛出异常
     */
    private void checkLimit(@NotNull CommandSender creator) throws CommandException {
        if (creator.isOp()) {
            return;
        }

        if (this.playerList.getSize() >= this.config.getServerLimit()) {
            throw new CommandException(translatable("fakeplayer.command.spawn.error.server-limit"));
        }

        if (this.countByCreator(creator) >= this.config.getPlayerLimit()) {
            throw new CommandException(translatable("fakeplayer.command.spawn.error.player-limit"));
        }

        if (this.config.isDetectIp() && this.countByAddress(AddressUtils.getAddress(creator)) >= this.config.getPlayerLimit()) {
            throw new CommandException(translatable("fakeplayer.command.spawn.error.ip-limit"));
        }
    }

    public void onDisable() {
        Exceptions.suppress(Main.getInstance(), () -> this.removeAll("Plugin disabled"));
        Exceptions.suppress(Main.getInstance(), this.ownershipRepository::resetActive);
        Exceptions.suppress(Main.getInstance(), this.lagMonitor::shutdownNow);
    }

}
