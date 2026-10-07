package net.onelitefeather.otis.velocity.link;

import net.onelitefeather.otis.client.ProblemDetails;
import net.onelitefeather.otis.client.api.AccountLinksApi;
import net.onelitefeather.otis.client.invoker.ApiException;
import net.onelitefeather.otis.client.model.AccountLinkDTO;
import net.onelitefeather.otis.client.model.LinkCodeDTO;
import net.onelitefeather.otis.client.model.LinkCodeRequestDTO;
import net.onelitefeather.otis.client.model.ProfileLinkRequestDTO;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * {@link LinkGateway} backed by the generated {@link AccountLinksApi}. API problems become
 * {@link GatewayResult.Problem}s; connection failures, server errors and responses that are not problem
 * documents (for example from an older Otis without the link endpoints) become
 * {@link GatewayResult.Unavailable}.
 */
public final class OtisLinkGateway implements LinkGateway {

    private final AccountLinksApi api;

    public OtisLinkGateway(@NotNull AccountLinksApi api) {
        this.api = api;
    }

    @Override
    public @NotNull GatewayResult<IssuedCode> requestCode(@NotNull UUID player, @NotNull LinkProvider provider) {
        return call(() -> {
            LinkCodeDTO dto = api.createLinkCode(player, new LinkCodeRequestDTO().provider(provider.id()));
            return new IssuedCode(dto.getCode(), dto.getExpiresAt().toInstant());
        });
    }

    @Override
    public @NotNull GatewayResult<List<LinkEntry>> listLinks(@NotNull UUID player) {
        return call(() -> api.listPlayerLinks(player).stream().map(OtisLinkGateway::toEntry).toList());
    }

    @Override
    public @NotNull GatewayResult<Void> unlink(@NotNull UUID player, @NotNull LinkProvider provider) {
        return call(() -> {
            api.deletePlayerLink(player, provider.id());
            return null;
        });
    }

    @Override
    public @NotNull GatewayResult<LinkEntry> setProfileLink(@NotNull UUID player, @NotNull LinkProvider provider,
                                                            @NotNull String value) {
        return call(() -> toEntry(api.putPlayerLink(player, provider.id(),
                new ProfileLinkRequestDTO().value(value))));
    }

    private static LinkEntry toEntry(AccountLinkDTO dto) {
        return new LinkEntry(dto.getProvider(), label(dto), Boolean.TRUE.equals(dto.getVerified()));
    }

    private static String label(AccountLinkDTO dto) {
        for (String candidate : new String[]{dto.getDisplayName(), dto.getValue(), dto.getExternalId()}) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private <T> GatewayResult<T> call(ApiCall<T> call) {
        try {
            return new GatewayResult.Success<>(call.execute());
        } catch (ApiException e) {
            return failure(e);
        }
    }

    private static <T> GatewayResult<T> failure(ApiException e) {
        int status = e.getCode();
        if (status >= 400 && status < 500) {
            String type = ProblemDetails.from(e).map(problem -> problem.getType()).orElse(null);
            if (type != null && !type.isBlank()) {
                return new GatewayResult.Problem<>(type.substring(type.lastIndexOf('/') + 1));
            }
        }
        if (status > 0) {
            return new GatewayResult.Unavailable<>("HTTP " + status);
        }
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return new GatewayResult.Unavailable<>(cause.getClass().getSimpleName());
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        T execute() throws ApiException;
    }
}
