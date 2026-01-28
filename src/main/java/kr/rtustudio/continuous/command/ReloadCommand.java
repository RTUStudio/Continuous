package kr.rtustudio.continuous.command;

import kr.rtustudio.continuous.Continuous;
import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ReloadCommand implements SimpleCommand {

    private static final List<Component> HELP_MESSAGE = List.of(
            Component.text("Continuous - Queue & Reconnect Plugin", NamedTextColor.YELLOW),
            Component.text("by IPECTER", NamedTextColor.GRAY),
            Component.empty()
    );

    private static final Component AVAILABLE_SUBCOMMANDS_MESSAGE = Component.text("Available subcommands:", NamedTextColor.WHITE);
    private static final Component NO_AVAILABLE_SUBCOMMANDS_MESSAGE = Component.text("There is no available subcommands for you.", NamedTextColor.WHITE);

    private final Continuous plugin;

    public ReloadCommand(Continuous plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> suggest(SimpleCommand.Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            return Arrays.stream(Subcommand.values()).filter(command -> command.hasPermission(source)).map(Subcommand::getCommand).collect(Collectors.toList());
        } else if (args.length == 1) {
            String argument = args[0];
            return Arrays.stream(Subcommand.values()).filter(command -> command.hasPermission(source)).map(Subcommand::getCommand)
                    .filter(str -> str.regionMatches(true, 0, argument, 0, argument.length())).collect(Collectors.toList());
        } else {
            return ImmutableList.of();
        }
    }

    @Override
    public void execute(SimpleCommand.Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        int argsAmount = args.length;
        if (argsAmount > 0) {
            try {
                Subcommand subcommand = Subcommand.valueOf(args[0].toUpperCase(Locale.ROOT));
                if (!subcommand.hasPermission(source)) {
                    this.showHelp(source);
                    return;
                }

                subcommand.executor.execute(this, source, args);
            } catch (IllegalArgumentException e) {
                this.showHelp(source);
            }
        } else {
            this.showHelp(source);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().getPermissionValue("continuous.command.help") != Tristate.FALSE;
    }

    private void showHelp(CommandSource source) {
        HELP_MESSAGE.forEach(source::sendMessage);

        List<Subcommand> availableSubcommands = Arrays.stream(Subcommand.values()).filter(command -> command.hasPermission(source)).toList();

        if (!availableSubcommands.isEmpty()) {
            source.sendMessage(AVAILABLE_SUBCOMMANDS_MESSAGE);
            availableSubcommands.forEach(command -> source.sendMessage(command.getMessageLine()));
        } else {
            source.sendMessage(NO_AVAILABLE_SUBCOMMANDS_MESSAGE);
        }
    }

    private enum Subcommand {
        RELOAD("Reload config.", (ReloadCommand parent, CommandSource source, String[] args) -> {
            parent.plugin.reload();
            source.sendMessage(Component.text("Configuration reloaded!", NamedTextColor.GREEN));
        });

        @Getter
        private final String command;
        private final String description;
        private final SubcommandExecutor executor;

        Subcommand(String description, SubcommandExecutor executor) {
            this.command = this.name().toLowerCase(Locale.ROOT);
            this.description = description;
            this.executor = executor;
        }

        public boolean hasPermission(CommandSource source) {
            return source.hasPermission("continuous.command." + this.command);
        }

        public Component getMessageLine() {
            return Component.textOfChildren(Component.text("  /continuous " + this.command, NamedTextColor.GREEN),
                    Component.text(" - ", NamedTextColor.DARK_GRAY), Component.text(this.description, NamedTextColor.YELLOW)
            );
        }
    }

    private interface SubcommandExecutor {
        void execute(ReloadCommand parent, CommandSource source, String[] args);
    }
}
