package net.onelitefeather.otis.velocity.link;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCommandsTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final FakeGateway gateway = new FakeGateway();
    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();

    @BeforeEach
    void setUp() {
        LinkCommandHandler handler = new LinkCommandHandler(gateway, Runnable::run,
                LoggerFactory.getLogger("test-" + UUID.randomUUID()), Clock.fixed(NOW, ZoneOffset.UTC));
        for (BrigadierCommand command : LinkCommands.create(handler)) {
            dispatcher.getRoot().addChild(command.getNode());
        }
    }

    @Test
    void linkDispatchesToTheHandlerWithTheProvider() throws CommandSyntaxException {
        gateway.codeResult = new GatewayResult.Success<>(new IssuedCode("K7Q4-MZ2A", NOW.plusSeconds(600)));
        TestSources.Recording player = TestSources.player(PLAYER, LinkCommands.PERMISSION_LINK);

        dispatcher.execute("link discord", player);

        assertEquals(List.of("requestCode discord"), gateway.calls);
        assertEquals(List.of(Messages.CODE_ISSUED), keys(player));
    }

    @Test
    void linksDispatchesToTheHandler() throws CommandSyntaxException {
        gateway.listResult = new GatewayResult.Success<>(List.of());
        TestSources.Recording player = TestSources.player(PLAYER, LinkCommands.PERMISSION_LINKS);

        dispatcher.execute("links", player);

        assertEquals(List.of("listLinks"), gateway.calls);
    }

    @Test
    void unlinkDispatchesToTheHandlerWithTheProvider() throws CommandSyntaxException {
        gateway.unlinkResult = new GatewayResult.Success<>(null);
        TestSources.Recording player = TestSources.player(PLAYER, LinkCommands.PERMISSION_UNLINK);

        dispatcher.execute("unlink twitch", player);

        assertEquals(List.of("unlink twitch"), gateway.calls);
    }

    @Test
    void socialPassesTheWholeRestOfTheLineAsValue() throws CommandSyntaxException {
        gateway.profileResult = new GatewayResult.Success<>(new LinkEntry("youtube", "x", false));
        TestSources.Recording player = TestSources.player(PLAYER, LinkCommands.PERMISSION_SOCIAL);

        dispatcher.execute("social youtube https://www.youtube.com/@onelitefeather", player);

        assertEquals(List.of("setProfileLink youtube https://www.youtube.com/@onelitefeather"), gateway.calls);
    }

    @ParameterizedTest
    @CsvSource({
            "link discord, otis.command.link",
            "links, otis.command.links",
            "unlink discord, otis.command.unlink",
            "social github olf, otis.command.social"
    })
    void sourcesWithoutThePermissionCannotRunTheCommand(String line, String permission) {
        String otherPermission = permission.equals("otis.command.link") ? "otis.command.links" : "otis.command.link";
        TestSources.Recording player = TestSources.player(PLAYER, otherPermission);

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(line, player));
        assertTrue(gateway.calls.isEmpty(), "no request may be sent: " + gateway.calls);
    }

    @ParameterizedTest
    @CsvSource({
            "link discord, otis.command.link",
            "links, otis.command.links",
            "unlink discord, otis.command.unlink",
            "social github olf, otis.command.social"
    })
    void eachCommandIsGuardedByItsOwnPermissionNode(String line, String permission) throws CommandSyntaxException {
        gateway.codeResult = new GatewayResult.Success<>(new IssuedCode("K7Q4-MZ2A", NOW.plusSeconds(600)));
        gateway.listResult = new GatewayResult.Success<>(List.of());
        gateway.unlinkResult = new GatewayResult.Success<>(null);
        gateway.profileResult = new GatewayResult.Success<>(new LinkEntry("github", "olf", false));

        dispatcher.execute(line, TestSources.player(PLAYER, permission));

        assertEquals(1, gateway.calls.size(), "the command must run with its own node: " + gateway.calls);
    }

    @Test
    void consoleGetsThePlayersOnlyMessage() throws CommandSyntaxException {
        TestSources.Recording console = TestSources.console(LinkCommands.PERMISSION_LINK);

        dispatcher.execute("link discord", console);

        assertEquals(List.of(Messages.ERROR_PLAYERS_ONLY), keys(console));
        assertTrue(gateway.calls.isEmpty());
    }

    @Test
    void linkSuggestsOnlyProvidersThatSupportCodes() throws ExecutionException, InterruptedException {
        TestSources.Recording player = TestSources.player(PLAYER, LinkCommands.PERMISSION_LINK);

        List<String> suggestions = suggest("link ", player);

        assertEquals(Set.of("discord", "twitch", "youtube"), Set.copyOf(suggestions));
    }

    @Test
    void socialAndUnlinkSuggestAllProviders() throws ExecutionException, InterruptedException {
        TestSources.Recording player = TestSources.player(PLAYER, LinkCommands.PERMISSION_SOCIAL,
                LinkCommands.PERMISSION_UNLINK);

        Set<String> expected = Set.of("discord", "twitch", "youtube", "x", "tiktok", "github");
        assertEquals(expected, Set.copyOf(suggest("social ", player)));
        assertEquals(expected, Set.copyOf(suggest("unlink ", player)));
    }

    @Test
    void suggestionsAreFilteredByThePrefix() throws ExecutionException, InterruptedException {
        TestSources.Recording player = TestSources.player(PLAYER, LinkCommands.PERMISSION_SOCIAL);

        assertEquals(Set.of("twitch", "tiktok"), Set.copyOf(suggest("social t", player)));
    }

    private List<String> suggest(String input, CommandSource source) throws ExecutionException, InterruptedException {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).get()
                .getList().stream().map(Suggestion::getText).toList();
    }

    private static List<String> keys(TestSources.Recording source) {
        return source.received().stream()
                .map(component -> component instanceof TranslatableComponent translatable
                        ? translatable.key() : "<not translatable>")
                .toList();
    }
}
