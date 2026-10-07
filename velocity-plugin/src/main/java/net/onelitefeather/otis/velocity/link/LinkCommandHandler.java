package net.onelitefeather.otis.velocity.link;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The logic behind {@code /link}, {@code /links}, {@code /unlink} and {@code /social}: it validates the
 * input, calls Otis through the {@link LinkGateway} on the given executor and reports the outcome to the
 * player as translatable components.
 * <p>
 * Nothing here logs player names, uuids, link codes or external ids.
 */
public final class LinkCommandHandler {

    private final LinkGateway gateway;
    private final Executor executor;
    private final Logger logger;
    private final Clock clock;

    /**
     * @param gateway  the access to Otis
     * @param executor runs the blocking gateway calls (virtual threads in production)
     * @param logger   receives one WARN per failed call
     * @param clock    the time source for the code's remaining validity
     */
    public LinkCommandHandler(@NotNull LinkGateway gateway, @NotNull Executor executor, @NotNull Logger logger,
                              @NotNull Clock clock) {
        this.gateway = gateway;
        this.executor = executor;
        this.logger = logger;
        this.clock = clock;
    }

    /**
     * {@code /link <provider>}: requests a link code and shows it as a copyable component.
     */
    public void requestCode(@NotNull Audience audience, @NotNull UUID player, @NotNull String providerInput) {
        LinkProvider provider = resolve(audience, providerInput);
        if (provider == null) {
            return;
        }
        if (!provider.codeLinkable()) {
            audience.sendMessage(Component.translatable(Messages.UNSUPPORTED_FOR_LINK,
                    Argument.string("provider", provider.id())));
            return;
        }
        run(audience, "link " + provider.id(), provider,
                () -> gateway.requestCode(player, provider),
                issued -> audience.sendMessage(codeMessage(provider, issued)));
    }

    /**
     * {@code /links}: lists the player's links.
     */
    public void list(@NotNull Audience audience, @NotNull UUID player) {
        run(audience, "links", null, () -> gateway.listLinks(player), links -> sendLinks(audience, links));
    }

    /**
     * {@code /unlink <provider>}: removes the player's link for a provider.
     */
    public void unlink(@NotNull Audience audience, @NotNull UUID player, @NotNull String providerInput) {
        LinkProvider provider = resolve(audience, providerInput);
        if (provider == null) {
            return;
        }
        run(audience, "unlink " + provider.id(), provider,
                () -> gateway.unlink(player, provider),
                _ -> audience.sendMessage(Component.translatable(Messages.UNLINKED,
                        Argument.string("provider", provider.id()))));
    }

    /**
     * {@code /social <provider> <value>}: sets an unverified public profile link.
     */
    public void setProfileLink(@NotNull Audience audience, @NotNull UUID player, @NotNull String providerInput,
                               @NotNull String value) {
        LinkProvider provider = resolve(audience, providerInput);
        if (provider == null) {
            return;
        }
        run(audience, "social " + provider.id(), provider,
                () -> gateway.setProfileLink(player, provider, value),
                entry -> audience.sendMessage(Component.translatable(Messages.SOCIAL_SET,
                        Argument.string("provider", provider.id()),
                        Argument.string("value", entry.label()))));
    }

    private LinkProvider resolve(Audience audience, String input) {
        return LinkProvider.parse(input).orElseGet(() -> {
            audience.sendMessage(Component.translatable(Messages.ERROR_UNSUPPORTED_PROVIDER));
            return null;
        });
    }

    private <T> void run(Audience audience, String operation, LinkProvider provider,
                         Supplier<GatewayResult<T>> call, Consumer<T> onSuccess) {
        executor.execute(() -> {
            GatewayResult<T> result;
            try {
                result = call.get();
            } catch (RuntimeException e) {
                result = new GatewayResult.Unavailable<>(e.getClass().getSimpleName());
            }
            switch (result) {
                case GatewayResult.Success<T>(var value) -> onSuccess.accept(value);
                case GatewayResult.Problem<T>(var slug) -> audience.sendMessage(problemMessage(operation, slug, provider));
                case GatewayResult.Unavailable<T>(var cause) -> {
                    logger.warn("Otis request '{}' failed: {}", operation, cause);
                    audience.sendMessage(Component.translatable(Messages.ERROR_UNAVAILABLE));
                }
            }
        });
    }

    private Component problemMessage(String operation, String slug, LinkProvider provider) {
        return switch (slug) {
            case "link-code-rate-limited" -> Component.translatable(Messages.ERROR_RATE_LIMITED);
            case "invalid-link-value" -> Component.translatable(Messages.ERROR_INVALID_VALUE, providerArg(provider));
            case "provider-already-linked" ->
                    Component.translatable(Messages.ERROR_ALREADY_VERIFIED, providerArg(provider));
            case "player-not-found" -> Component.translatable(Messages.ERROR_PLAYER_UNKNOWN);
            case "unsupported-provider" -> Component.translatable(Messages.ERROR_UNSUPPORTED_PROVIDER);
            default -> {
                logger.warn("Otis request '{}' was rejected with unexpected problem '{}'", operation, slug);
                yield Component.translatable(Messages.ERROR_UNAVAILABLE);
            }
        };
    }

    private static ComponentLike providerArg(LinkProvider provider) {
        return Argument.string("provider", provider == null ? "" : provider.id());
    }

    private Component codeMessage(LinkProvider provider, IssuedCode issued) {
        Component code = Component.text(issued.code(), NamedTextColor.GOLD, TextDecoration.BOLD)
                .clickEvent(ClickEvent.copyToClipboard(issued.code()))
                .hoverEvent(HoverEvent.showText(Component.translatable(Messages.CODE_COPY_HOVER)));
        return Component.translatable(Messages.CODE_ISSUED,
                Argument.string("provider", provider.id()),
                Argument.component("code", code),
                Argument.numeric("minutes", minutesLeft(issued.expiresAt())));
    }

    private long minutesLeft(Instant expiresAt) {
        long seconds = Duration.between(clock.instant(), expiresAt).toSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }

    private void sendLinks(Audience audience, List<LinkEntry> links) {
        if (links.isEmpty()) {
            audience.sendMessage(Component.translatable(Messages.LIST_EMPTY));
            return;
        }
        audience.sendMessage(Component.translatable(Messages.LIST_HEADER));
        for (LinkEntry link : links) {
            audience.sendMessage(Component.translatable(
                    link.verified() ? Messages.LIST_ENTRY_VERIFIED : Messages.LIST_ENTRY_UNVERIFIED,
                    Argument.string("provider", link.provider()),
                    Argument.string("value", link.label())));
        }
    }
}
