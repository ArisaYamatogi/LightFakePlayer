package io.github.hello09x.fakeplayer.core.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.jorel.commandapi.CommandPermission;
import io.github.hello09x.devtools.command.HelpCommand;
import io.github.hello09x.devtools.core.utils.ComponentUtils;
import io.github.hello09x.fakeplayer.api.spi.ActionSetting;
import io.github.hello09x.fakeplayer.api.spi.ActionType;
import io.github.hello09x.fakeplayer.core.command.impl.*;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.constant.Direction;
import io.github.hello09x.fakeplayer.core.repository.model.Feature;
import org.bukkit.entity.Entity;

import java.util.List;

import static io.github.hello09x.devtools.command.Commands.*;
import static io.github.hello09x.fakeplayer.core.command.CommandSupports.*;
import static net.kyori.adventure.text.Component.translatable;


@Singleton
public class CommandRegistry {

    @Inject
    private ActionCommand actionCommand;
    @Inject
    private ConfigCommand configCommand;
    @Inject
    private DistanceCommand distanceCommand;
    @Inject
    private ExpmeCommand expmeCommand;
    @Inject
    private HoldCommand holdCommand;
    @Inject
    private KillCommand killCommand;
    @Inject
    private KillallCommand killallCommand;
    @Inject
    private ListCommand listCommand;
    @Inject
    private MoveCommand moveCommand;
    @Inject
    private ReloadCommand reloadCommand;
    @Inject
    private RespawnCommand respawnCommand;
    @Inject
    private RotationCommand rotationCommand;
    @Inject
    private SelectCommand selectCommand;
    @Inject
    private SetCommand setCommand;
    @Inject
    private SkinCommand skinCommand;
    @Inject
    private SpawnCommand spawnCommand;
    @Inject
    private StatusCommand statusCommand;
    @Inject
    private SwapCommand swapCommand;
    @Inject
    private TeleportCommand teleportCommand;
    @Inject
    private DebugCommand debugCommand;
    @Inject
    private StopCommand stopCommand;

    @Inject
    private FakeplayerConfig config;

    public void register() {
        var root = command("fakeplayer")
                .withAliases("fp")
                .withHelp(
                        ComponentUtils.toString(translatable("fakeplayer.command.fp.short-description"), null),
                        ComponentUtils.toString(translatable("fakeplayer.command.fp.full-description"), null)
                )
                .withUsage(
                        "type fp ? for more information",
                        "hello09x [汤姆]"
                )
                .withPermission(Permission.spawn)
                .withSubcommands(
                        command("select")
                                .withPermission(Permission.select)
                                .withShortDescription("fakeplayer.command.select.description")
                                .withRequirement(CommandSupports::needSelect)
                                .withOptionalArguments(fakeplayer("name"))
                                .executesPlayer(selectCommand::select),
                        command("selection")
                                .withPermission(Permission.select)
                                .withShortDescription("fakeplayer.command.selection.description")
                                .withRequirement(CommandSupports::needSelect)
                                .executesPlayer(selectCommand::selection),

                        command("spawn")
                                .withPermission(Permission.spawn)
                                .withShortDescription("fakeplayer.command.spawn.description")
                                .withArguments(text("id"))
                                .withOptionalArguments(
                                        world("world").withPermission(Permission.spawnLocation),
                                        location("location").withPermission(Permission.spawnLocation))
                                .executes(spawnCommand::spawn),
                        command("kill")
                                .withPermission(Permission.kill)
                                .withShortDescription("fakeplayer.command.kill.description")
                                .withOptionalArguments(fakeplayers("names"))
                                .executes(killCommand::kill),
                        command("list")
                                .withPermission(Permission.list)
                                .withShortDescription("fakeplayer.command.list.description")
                                .withOptionalArguments(
                                        int32("page", 1),
                                        int32("size", 1))
                                .executes(listCommand::list),
                        command("distance")
                                .withPermission(Permission.distance)
                                .withShortDescription("fakeplayer.command.distance.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withOptionalArguments(fakeplayer("name"))
                                .executesPlayer(distanceCommand::distance),
                        command("skin")
                                .withPermission(Permission.skin)
                                .withShortDescription("fakeplayer.command.skin.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withArguments(offlinePlayer("player"))
                                .withOptionalArguments(fakeplayer("name"))
                                .executes(skinCommand::skin),
                        command("hold")
                                .withPermission(Permission.hold)
                                .withShortDescription("fakeplayer.command.hold.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withArguments(int32("slot", 1, 9))
                                .withOptionalArguments(fakeplayer("name"))
                                .executes(holdCommand::hold),
                        command("status")
                                .withPermission(Permission.status)
                                .withShortDescription("fakeplayer.command.status.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withOptionalArguments(fakeplayer("name"))
                                .executes(statusCommand::status),
                        command("respawn")
                                .withRequirement(CommandSupports::hasFakeplayerForRespawn)
                                .withShortDescription("fakeplayer.command.respawn.description")
                                .withPermission(Permission.respawn)
                                .withOptionalArguments(fakeplayer("name", Entity::isDead))
                                .executes(respawnCommand::respawn),
                        command("set")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withShortDescription("fakeplayer.command.set.description")
                                .withPermission(Permission.set)
                                .withArguments(
                                        configKey("feature", Feature::hasModifier),
                                        configValue("feature", "option")
                                )
                                .withOptionalArguments(fakeplayer("name"))
                                .executes(setCommand::set),
                        command("config")
                                .withPermission(Permission.config)
                                .withShortDescription("fakeplayer.command.config.description")
                                .withSubcommands(
                                        command("set")
                                                .withArguments(
                                                        configKey("feature"),
                                                        configValue("feature", "option"))
                                                .executesPlayer(configCommand::setConfig),
                                        command("list")
                                                .executesPlayer(configCommand::listConfig)
                                )
                                .executesPlayer(configCommand::listConfig),

                        command("expme")
                                .withPermission(Permission.expme)
                                .withShortDescription("fakeplayer.command.expme.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withOptionalArguments(fakeplayer("name"))
                                .executesPlayer(expmeCommand::expme),

                        command("tp")
                                .withAliases("tpto")
                                .withPermission(Permission.tp)
                                .withShortDescription("fakeplayer.command.tp.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withOptionalArguments(fakeplayer("name"))
                                .executesPlayer(teleportCommand::tp),
                        command("tphere")
                                .withPermission(Permission.tphere)
                                .withShortDescription("fakeplayer.command.tphere.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withOptionalArguments(fakeplayer("name"))
                                .executesPlayer(teleportCommand::tphere),
                        command("tps")
                                .withPermission(Permission.tps)
                                .withShortDescription("fakeplayer.command.tphere.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withOptionalArguments(fakeplayer("name"))
                                .executesPlayer(teleportCommand::tps),

                        command("attack")
                                .withPermission(Permission.attack)
                                .withShortDescription("fakeplayer.command.attack.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withSubcommands(newActionCommands(ActionType.ATTACK))
                                .executes(actionCommand.action(ActionType.ATTACK, ActionSetting.once())),
                        command("drop")
                                .withPermission(Permission.drop)
                                .withShortDescription("fakeplayer.command.drop.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withSubcommands(newActionCommands(ActionType.DROP_ITEM))
                                .executes(actionCommand.action(ActionType.DROP_ITEM, ActionSetting.once())),
                        command("dropstack")
                                .withPermission(Permission.dropstack)
                                .withShortDescription("fakeplayer.command.dropstack.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withSubcommands(newActionCommands(ActionType.DROP_STACK))
                                .executes(actionCommand.action(ActionType.DROP_STACK, ActionSetting.once())),
                        command("look")
                                .withPermission(Permission.look)
                                .withShortDescription("fakeplayer.command.look.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withSubcommands(
                                        command("north")
                                                .withShortDescription("fakeplayer.command.look.north.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand.look(Direction.NORTH)),
                                        command("south")
                                                .withShortDescription("fakeplayer.command.look.south.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand.look(Direction.SOUTH)),
                                        command("west")
                                                .withShortDescription("fakeplayer.command.look.west.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand.look(Direction.WEST)),
                                        command("east")
                                                .withShortDescription("fakeplayer.command.look.east.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand.look(Direction.EAST)),
                                        command("up")
                                                .withShortDescription("fakeplayer.command.look.up.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand.look(Direction.UP)),
                                        command("down")
                                                .withShortDescription("fakeplayer.command.look.down.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand.look(Direction.DOWN)),
                                        command("at")
                                                .withShortDescription("fakeplayer.command.look.at.description")
                                                .withArguments(location("location"))
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand::lookAt),
                                        command("me")
                                                .withShortDescription("fakeplayer.command.look.me.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executesPlayer(rotationCommand::lookMe),
                                        command("entity")
                                                .withShortDescription("fakeplayer.command.look.entity.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .withSubcommands(newActionCommands(ActionType.LOOK_AT_NEAREST_ENTITY))
                                ),
                        command("turn")
                                .withPermission(Permission.turn)
                                .withShortDescription("fakeplayer.command.turn.description")
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withSubcommands(
                                        command("left")
                                                .withShortDescription("fakeplayer.command.turn.left.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand.turn(-90, 0)),
                                        command("right")
                                                .withShortDescription("fakeplayer.command.turn.right.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand.turn(90, 0)),
                                        command("back")
                                                .withShortDescription("fakeplayer.command.turn.back.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand.turn(180, 0)),
                                        command("to")
                                                .withShortDescription("fakeplayer.command.turn.to.description")
                                                .withArguments(rotation("rotation"))
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(rotationCommand::turnTo)
                                ),
                        command("move")
                                .withShortDescription("fakeplayer.command.move.description")
                                .withPermission(Permission.move)
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withSubcommands(
                                        command("forward")
                                                .withShortDescription("fakeplayer.command.move.forward.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(moveCommand.move(1, 0)),
                                        command("backward")
                                                .withShortDescription("fakeplayer.command.move.backward.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(moveCommand.move(-1, 0)),
                                        command("left")
                                                .withShortDescription("fakeplayer.command.move.left.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(moveCommand.move(0, 1)),
                                        command("right")
                                                .withShortDescription("fakeplayer.command.move.right.description")
                                                .withOptionalArguments(fakeplayer("name"))
                                                .executes(moveCommand.move(0, -1))
                                )
                                .executes(moveCommand.move(1, 0)),

                        command("swap")
                                .withShortDescription("fakeplayer.command.swap.description")
                                .withPermission(Permission.swap)
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withOptionalArguments(fakeplayer("name"))
                                .executes(swapCommand::swap),
                        command("stop")
                                .withShortDescription("fakeplayer.command.stop.description")
                                .withPermission(Permission.stop)
                                .withRequirement(CommandSupports::hasFakeplayer)
                                .withOptionalArguments(fakeplayer("name"))
                                .executes(stopCommand::stop),

                        command("killall")
                                .withShortDescription("fakeplayer.command.killall.description")
                                .withPermission(CommandPermission.OP)
                                .executes(killallCommand::killall),
                        command("reload")
                                .withShortDescription("fakeplayer.command.reload.description")
                                .withPermission(CommandPermission.OP)
                                .executes(reloadCommand::reload),
                        command("reload-translation")
                                .withShortDescription("fakeplayer.command.reload-translation.description")
                                .withPermission(CommandPermission.OP)
                                .executes(reloadCommand::reloadTranslation),

                        // developer debug
                        command("debug")
                                .withPermission(CommandPermission.OP)
                                .withRequirement(sender -> config.isDebug())
                                .withSubcommands(
                                        command("send-plugin-message")
                                                .withArguments(
                                                        player("player"),
                                                        text("channel"),
                                                        text("message")
                                                )
                                                .executes(debugCommand::sendPluginMessage)
                                )

                );
        HelpCommand.generateHelpCommand(root, true);
        root.register();
    }


}
