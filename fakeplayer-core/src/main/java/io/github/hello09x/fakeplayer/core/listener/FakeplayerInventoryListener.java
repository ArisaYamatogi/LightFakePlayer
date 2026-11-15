package io.github.hello09x.fakeplayer.core.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 假人只保留主手/副手，禁止捡起物品或打开/操作背包
 */
@Singleton
public class FakeplayerInventoryListener implements Listener {

    private final FakeplayerManager manager;

    @Inject
    public FakeplayerInventoryListener(FakeplayerManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && manager.isFake(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onOpenInventory(@NotNull InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && manager.isFake(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onClickInventory(@NotNull InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isFake(player)) {
            event.setCancelled(true);
        }
    }

}
