package io.github.hello09x.fakeplayer.core.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author tanyaofei
 * @since 2024/8/7
 **/
@Singleton
public class FakeplayerLifecycleListener implements Listener {

    private final FakeplayerManager manager;
    private final FakeplayerConfig config;

    @Inject
    public FakeplayerLifecycleListener(FakeplayerManager manager, FakeplayerConfig config) {
        this.manager = manager;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPostSpawn(@NotNull PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (this.manager.isNotFake(player)) {
            // Not a fake player
            return;
        }

        manager.dispatchCommands(player, config.getPostSpawnCommands());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAfterSpawn(@NotNull PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (this.manager.isNotFake(player)) {
            // Not a fake player
            return;
        }

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!player.isOnline()) {
                return;
            }

            manager.dispatchCommands(player, config.getAfterSpawnCommands());

            if (config.isAuthmeEnabled() && Bukkit.getPluginManager().isPluginEnabled("AuthMe")) {
                manager.issueCommands(player, config.getAuthmeRegisterCommands());
                Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                    if (player.isOnline()) {
                        manager.issueCommands(player, config.getAuthmeLoginCommands());
                    }
                }, 20L);
                Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                    if (player.isOnline()) {
                        manager.issueCommands(player, config.getSelfCommands());
                    }
                }, 40L);
            } else {
                manager.issueCommands(player, config.getSelfCommands());
            }
        }, 20);
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPostQuit(@NotNull PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (this.manager.isNotFake(player)) {
            // Not a fake player
            return;
        }

        manager.dispatchCommands(player, config.getPostQuitCommands());

        // cleanup 会在更低优先级移除假人, 因此这里先记录创建者信息供延迟命令使用
        var afterQuitCommands = config.getAfterQuitCommands();
        if (!afterQuitCommands.isEmpty()) {
            var creatorName = manager.getCreatorName(player);
            var creatorId = manager.getCreatorId(player);
            if (creatorName != null && creatorId != null) {
                Bukkit.getScheduler().runTaskLater(
                        Main.getInstance(),
                        () -> manager.dispatchCommands(player, afterQuitCommands, creatorName, creatorId),
                        20
                );
            }
        }
    }


}
