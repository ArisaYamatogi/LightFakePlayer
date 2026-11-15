package io.github.hello09x.fakeplayer.core.manager.naming;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.naming.exception.IllegalCustomNameException;
import io.github.hello09x.fakeplayer.core.repository.FakeplayerOwnershipRepository;
import io.github.hello09x.fakeplayer.core.repository.FakeplayerProfileRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

@Singleton
public class NameManager {

    private final static Logger log = Main.getInstance().getLogger();

    public final static Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    public final static int MAX_LENGTH = 16;

    private final FakeplayerProfileRepository profileRepository;

    private final FakeplayerOwnershipRepository ownershipRepository;

    @Inject
    public NameManager(
            FakeplayerProfileRepository profileRepository,
            FakeplayerOwnershipRepository ownershipRepository
    ) {
        this.profileRepository = profileRepository;
        this.ownershipRepository = ownershipRepository;
    }

    /**
     * 校验并生成一个指定名称的假人
     *
     * @param creator 创建者
     * @param id      自定义 ID
     * @return 序列名
     */
    public @NotNull SequenceName getSpecifiedName(@NotNull CommandSender creator, @NotNull String id) {
        id = this.validateFormat(id);

        this.checkOnlinePlayer(id);
        this.checkOwnership(creator, id);
        this.checkRealPlayer(id);

        // 假人尚未完成异步上线时 Bukkit 看不到它, 因此还要检查归属记录
        if (this.ownershipRepository.hasActive(id)) {
            throw new IllegalCustomNameException(translatable("fakeplayer.spawn.error.name.online", text(id, GOLD)).color(RED));
        }

        return new SequenceName("custom", 0, this.getUUIDFromName(id), id);
    }

    public @NotNull String validateFormat(@NotNull String id) {
        id = id.trim();

        if (id.length() > MAX_LENGTH) {
            throw new IllegalCustomNameException(translatable(
                    "fakeplayer.spawn.error.name.too-long",
                    text(MAX_LENGTH, RED)
            ).color(RED));
        }

        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalCustomNameException(translatable("fakeplayer.spawn.error.name.invalid", RED));
        }
        return id;
    }

    private void checkOnlinePlayer(@NotNull String id) {
        for (var online : Bukkit.getOnlinePlayers()) {
            if (!online.getName().equalsIgnoreCase(id)) {
                continue;
            }
            throw new IllegalCustomNameException(
                    online.isDead()
                            ? translatable("fakeplayer.spawn.error.name.online-dead", text(id, GOLD), text("/fp respawn", RED)).color(RED)
                            : translatable("fakeplayer.spawn.error.name.online", text(id, GOLD)).color(RED)
            );
        }
    }

    private void checkOwnership(@NotNull CommandSender creator, @NotNull String id) {
        var creatorId = getCreatorId(creator);
        if (this.ownershipRepository.isReservedByOther(creatorId, id)) {
            throw new IllegalCustomNameException(translatable(
                    "fakeplayer.spawn.error.name.reserved",
                    text(id, GOLD)
            ).color(RED));
        }
    }

    private void checkRealPlayer(@NotNull String id) {
        var offline = Bukkit.getOfflinePlayer(id);
        if (!offline.hasPlayedBefore()) {
            return;
        }

        // 本插件为该名称生成过 UUID 时，只有 UUID 相同才视为假人自己的历史数据
        var profileUuid = this.profileRepository.selectUUIDByName(id);
        if (profileUuid != null && profileUuid.equals(offline.getUniqueId())) {
            return;
        }

        throw new IllegalCustomNameException(translatable(
                "fakeplayer.spawn.error.name.used",
                text(id, GOLD),
                text(offline.getUniqueId().toString(), GOLD)
        ).color(RED));
    }

    /**
     * 通过 LuckPerms 检查名称是否属于真实玩家
     * <p>LuckPerms 中可能存在从未连接过本服务器的玩家, 单靠 Bukkit 离线缓存无法发现</p>
     */
    public @NotNull CompletableFuture<Void> checkLuckPerms(@NotNull String id) {
        return this.lookupLuckPermsUuid(id).thenAccept(uuid -> {
            if (uuid == null) {
                return;
            }
            var profileUuid = this.profileRepository.selectUUIDByName(id);
            if (profileUuid != null && profileUuid.equals(uuid)) {
                return;
            }
            throw new CompletionException(new IllegalCustomNameException(translatable(
                    "fakeplayer.spawn.error.name.used",
                    text(id, GOLD),
                    text(uuid.toString(), GOLD)
            ).color(RED)));
        });
    }

    @SuppressWarnings("unchecked")
    private @NotNull CompletableFuture<@Nullable UUID> lookupLuckPermsUuid(@NotNull String id) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            var luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            var userManagerClass = Class.forName("net.luckperms.api.model.user.UserManager");
            var providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            var luckPerms = providerClass.getMethod("get").invoke(null);
            var userManager = luckPermsClass.getMethod("getUserManager").invoke(luckPerms);
            var lookup = userManagerClass.getMethod("lookupUniqueId", String.class);
            // LuckPerms 用户名以小写保存, 而命令参数可能是任意大小写
            return ((CompletableFuture<UUID>) lookup.invoke(userManager, id.toLowerCase(Locale.ROOT))).exceptionally(e -> {
                log.warning("Could not query LuckPerms for player name '%s': %s".formatted(id, e.getMessage()));
                throw new CompletionException(e);
            });
        } catch (Throwable e) {
            log.warning("Could not query LuckPerms for player name '%s': %s".formatted(id, e.getMessage()));
            // 校验失败时不能放行, 否则可能在 LuckPerms 故障期间占用真实玩家名称
            return CompletableFuture.failedFuture(e);
        }
    }

    private @NotNull UUID getUUIDFromName(@NotNull String name) {
        var uuid = this.profileRepository.selectUUIDByName(name);
        if (uuid != null) {
            return uuid;
        }

        for (int i = 0; i < 10; i++) {
            var generated = UUID.randomUUID();
            if (this.profileRepository.existsByUUID(generated) || Bukkit.getOfflinePlayer(generated).hasPlayedBefore()) {
                continue;
            }
            this.profileRepository.insert(name, generated);
            return generated;
        }

        throw new IllegalStateException("Failed to generate uuid for fake player '%s' after 10 attempts".formatted(name));
    }

    public static @NotNull UUID getCreatorId(@NotNull CommandSender creator) {
        return creator instanceof Player player ? player.getUniqueId() : new UUID(0, 0);
    }

}
