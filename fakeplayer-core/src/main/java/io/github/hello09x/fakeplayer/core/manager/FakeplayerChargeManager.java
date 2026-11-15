package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.util.Commands;
import io.github.hello09x.fakeplayer.core.util.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 按每个假人的存活时长执行配置的扣费命令
 */
@Singleton
public class FakeplayerChargeManager {

    private final static Logger log = Main.getInstance().getLogger();

    private final FakeplayerManager manager;

    private final FakeplayerConfig config;

    private final BukkitTask task;

    @Inject
    public FakeplayerChargeManager(FakeplayerManager manager, FakeplayerConfig config) {
        this.manager = manager;
        this.config = config;
        this.task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::tick, 20L, 20L);
    }

    private void tick() {
        if (!this.config.isChargingEnabled()) {
            return;
        }

        var commands = this.config.getChargingCommands();
        if (commands.isEmpty()) {
            return;
        }

        var interval = (long) (this.config.getChargingIntervalMinutes() * 60_000D);
        if (interval <= 0) {
            return;
        }

        var now = System.currentTimeMillis();
        for (var fakeplayer : this.manager.getAllFakeplayers()) {
            if (!fakeplayer.isOnline()) {
                continue;
            }
            // 仅对玩家创建的假人扣费; 控制台创建的假人不参与
            if (!(fakeplayer.getCreator() instanceof Player)) {
                continue;
            }
            if (now - fakeplayer.getLastChargedAt() < interval) {
                continue;
            }

            fakeplayer.setLastChargedAt(now);
            var creator = fakeplayer.getCreator();
            var placeholders = this.placeholders(fakeplayer.getName(), fakeplayer.getUUID(), creator.getName(), fakeplayer.getCreatorId());
            for (var raw : commands) {
                // PAPI 占位符也以 % 开头, 必须先解析 PAPI, 否则 %p 会破坏 %player_name% 等占位符
                var command = Placeholders.parse(creator, raw);
                command = Commands.formatCommand(command, placeholders);
                if (command.isBlank()) {
                    continue;
                }
                if (!Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    log.warning("Failed to execute charging command for %s: %s".formatted(fakeplayer.getName(), command));
                } else {
                    log.info("Executed charging command for %s: %s".formatted(fakeplayer.getName(), command));
                }
            }
        }
    }

    private @NotNull Map<String, String> placeholders(
            @NotNull String fakeplayerName,
            @NotNull java.util.UUID fakeplayerId,
            @NotNull String creatorName,
            @NotNull java.util.UUID creatorId
    ) {
        var placeholders = new HashMap<String, String>(6);
        placeholders.put("%p", fakeplayerName);
        placeholders.put("%u", fakeplayerId.toString());
        placeholders.put("%c", creatorName);
        placeholders.put("%id%", fakeplayerName);
        placeholders.put("%owner%", creatorName);
        placeholders.put("%owner_uuid%", creatorId.toString());
        return placeholders;
    }

    public void onDisable() {
        this.task.cancel();
    }

}
