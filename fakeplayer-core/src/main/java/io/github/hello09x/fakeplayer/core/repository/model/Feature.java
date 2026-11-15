package io.github.hello09x.fakeplayer.core.repository.model;

import io.github.hello09x.fakeplayer.api.spi.ActionSetting;
import io.github.hello09x.fakeplayer.api.spi.ActionType;
import io.github.hello09x.fakeplayer.core.command.Permission;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.translation.Translatable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Getter
@AllArgsConstructor
public enum Feature implements Translatable, Singletons {

    /**
     * 是否具有碰撞箱
     */
    collidable(
            "fakeplayer.config.collidable",
            List.of(Permission.config),
            List.of("true", "false"),
            "true",
            faker -> String.valueOf(faker.isCollidable()),
            (faker, value) -> faker.setCollidable(Boolean.parseBoolean(value))
    ),

    /**
     * 是否无敌
     */
    invulnerable(
            "fakeplayer.config.invulnerable",
            List.of(Permission.config),
            List.of("true", "false"),
            "false",
            faker -> String.valueOf(faker.isInvulnerable()),
            (faker, value) -> faker.setInvulnerable(Boolean.parseBoolean(value))
    ),

    /**
     * 是否自动看向实体
     */
    look_at_entity(
            "fakeplayer.config.look_at_entity",
            List.of(Permission.config, Permission.look),
            List.of("true", "false"),
            "false",
            faker -> String.valueOf(actionManager.get().hasActiveAction(faker, ActionType.LOOK_AT_NEAREST_ENTITY)),
            (faker, value) -> {
                if (Boolean.parseBoolean(value)) {
                    actionManager.get().setAction(faker, ActionType.LOOK_AT_NEAREST_ENTITY, ActionSetting.continuous());
                } else {
                    actionManager.get().setAction(faker, ActionType.LOOK_AT_NEAREST_ENTITY, ActionSetting.stop());
                }
            }
    ),

    /**
     * 是否使用皮肤
     */
    skin(
            "fakeplayer.config.skin",
            List.of(Permission.config),
            List.of("true", "false"),
            "true",
            null,
            null
    ),
    ;

    @NotNull
    final String translationKey;

    @NotNull
    final List<String> permissions;

    @NotNull
    final List<String> options;

    @NotNull
    final String defaultOption;

    @Nullable
    final Function<Player, String> detector;

    @Nullable
    final BiConsumer<Player, String> modifier;


    @Override
    public @NotNull String translationKey() {
        return this.translationKey;
    }

    public boolean hasDetector() {
        return this.detector != null;
    }

    public boolean hasModifier() {
        return this.modifier != null;
    }

    public boolean testPermissions(@NotNull CommandSender sender) {
        for (var permission : this.permissions) {
            if (!sender.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

}
