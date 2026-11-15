package io.github.hello09x.fakeplayer.core.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class Commands {

    public static @NotNull List<String> formatCommands(
            @NotNull List<String> commands,
            @NotNull Map<String, String> placeholders
    ) {
        var result = new ArrayList<String>(commands.size());
        for (var command : commands) {
            var formatted = formatCommand(command, placeholders);
            if (!formatted.isBlank()) {
                result.add(formatted);
            }
        }
        return result;
    }

    public static @NotNull String formatCommand(
            @NotNull String command,
            @NotNull Map<String, String> placeholders
    ) {
        var formatted = command.trim();
        if (formatted.startsWith("/")) {
            formatted = formatted.substring(1);
        }

        // 先替换较长的占位符, 避免 %p 把 %password% 截断
        var ordered = placeholders.entrySet()
                                  .stream()
                                  .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                                  .sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed())
                                  .toList();
        for (var entry : ordered) {
            formatted = formatted.replace(entry.getKey(), entry.getValue());
        }
        return formatted;
    }

    public static @NotNull String mask(@NotNull String command, @NotNull String... secrets) {
        for (var secret : secrets) {
            if (secret != null && !secret.isBlank()) {
                command = command.replace(secret, "******");
            }
        }
        return command;
    }

}
