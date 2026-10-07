package net.onelitefeather.otis.velocity.link;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Builds the Brigadier trees of {@code /link}, {@code /links}, {@code /unlink} and {@code /social}. The trees
 * only map input to {@link LinkCommandHandler} calls.
 */
public final class LinkCommands {

    public static final String PERMISSION_LINK = "otis.command.link";
    public static final String PERMISSION_LINKS = "otis.command.links";
    public static final String PERMISSION_UNLINK = "otis.command.unlink";
    public static final String PERMISSION_SOCIAL = "otis.command.social";

    private static final String PROVIDER = "provider";
    private static final String VALUE = "value";

    private static final SuggestionProvider<CommandSource> CODE_PROVIDERS = suggesting(LinkProvider.codeLinkableIds());
    private static final SuggestionProvider<CommandSource> ALL_PROVIDERS = suggesting(LinkProvider.allIds());

    private LinkCommands() {
    }

    /**
     * @param handler the logic the commands call
     * @return the four commands, ready to be registered with Velocity's command manager
     */
    public static @NotNull List<BrigadierCommand> create(@NotNull LinkCommandHandler handler) {
        return List.of(
                new BrigadierCommand(link(handler)),
                new BrigadierCommand(links(handler)),
                new BrigadierCommand(unlink(handler)),
                new BrigadierCommand(social(handler)));
    }

    private static LiteralArgumentBuilder<CommandSource> link(LinkCommandHandler handler) {
        return BrigadierCommand.literalArgumentBuilder("link")
                .requires(source -> source.hasPermission(PERMISSION_LINK))
                .then(BrigadierCommand.requiredArgumentBuilder(PROVIDER, StringArgumentType.word())
                        .suggests(CODE_PROVIDERS)
                        .executes(context -> playersOnly(context, player ->
                                handler.requestCode(player, player.getUniqueId(),
                                        StringArgumentType.getString(context, PROVIDER)))));
    }

    private static LiteralArgumentBuilder<CommandSource> links(LinkCommandHandler handler) {
        return BrigadierCommand.literalArgumentBuilder("links")
                .requires(source -> source.hasPermission(PERMISSION_LINKS))
                .executes(context -> playersOnly(context, player ->
                        handler.list(player, player.getUniqueId())));
    }

    private static LiteralArgumentBuilder<CommandSource> unlink(LinkCommandHandler handler) {
        return BrigadierCommand.literalArgumentBuilder("unlink")
                .requires(source -> source.hasPermission(PERMISSION_UNLINK))
                .then(BrigadierCommand.requiredArgumentBuilder(PROVIDER, StringArgumentType.word())
                        .suggests(ALL_PROVIDERS)
                        .executes(context -> playersOnly(context, player ->
                                handler.unlink(player, player.getUniqueId(),
                                        StringArgumentType.getString(context, PROVIDER)))));
    }

    private static LiteralArgumentBuilder<CommandSource> social(LinkCommandHandler handler) {
        RequiredArgumentBuilder<CommandSource, String> value =
                BrigadierCommand.requiredArgumentBuilder(VALUE, StringArgumentType.greedyString())
                        .executes(context -> playersOnly(context, player ->
                                handler.setProfileLink(player, player.getUniqueId(),
                                        StringArgumentType.getString(context, PROVIDER),
                                        StringArgumentType.getString(context, VALUE))));
        return BrigadierCommand.literalArgumentBuilder("social")
                .requires(source -> source.hasPermission(PERMISSION_SOCIAL))
                .then(BrigadierCommand.requiredArgumentBuilder(PROVIDER, StringArgumentType.word())
                        .suggests(ALL_PROVIDERS)
                        .then(value));
    }

    private static int playersOnly(CommandContext<CommandSource> context, java.util.function.Consumer<Player> action) {
        if (context.getSource() instanceof Player player) {
            action.accept(player);
        } else {
            context.getSource().sendMessage(Component.translatable(Messages.ERROR_PLAYERS_ONLY));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static SuggestionProvider<CommandSource> suggesting(List<String> options) {
        return (_, builder) -> {
            String typed = builder.getRemainingLowerCase();
            options.stream().filter(option -> option.startsWith(typed)).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }
}
