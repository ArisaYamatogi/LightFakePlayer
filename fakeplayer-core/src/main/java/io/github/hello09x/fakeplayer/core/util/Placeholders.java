package io.github.hello09x.fakeplayer.core.util;

import io.github.hello09x.fakeplayer.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * 可选的 PlaceholderAPI 支持，避免硬依赖
 */
public final class Placeholders {

    private static boolean warned;

    private static Method setPlaceholders;

    private Placeholders() {
    }

    public static @NotNull String parse(@NotNull CommandSender sender, @NotNull String text) {
        if (!(sender instanceof Player player)) {
            return text;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }

        try {
            var method = setPlaceholders;
            if (method == null) {
                var clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                method = clazz.getMethod("setPlaceholders", Player.class, String.class);
                setPlaceholders = method;
            }
            return String.valueOf(method.invoke(null, player, text));
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                Main.getInstance().getLogger().warning("PlaceholderAPI is present but could not be used: " + e.getMessage());
            }
            return text;
        }
    }

}
