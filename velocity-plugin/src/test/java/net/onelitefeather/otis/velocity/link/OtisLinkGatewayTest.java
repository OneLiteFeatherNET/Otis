package net.onelitefeather.otis.velocity.link;

import net.onelitefeather.otis.client.api.AccountLinksApi;
import net.onelitefeather.otis.client.invoker.ApiException;
import net.onelitefeather.otis.client.model.AccountLinkDTO;
import net.onelitefeather.otis.client.model.LinkCodeDTO;
import net.onelitefeather.otis.client.model.LinkCodeRequestDTO;
import net.onelitefeather.otis.client.model.ProfileLinkRequestDTO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OtisLinkGatewayTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String PROBLEMS = "https://otis.onelitefeather.net/problems/";

    private static ApiException problem(int status, String slug) {
        HttpHeaders headers = HttpHeaders.of(Map.of("Content-Type", List.of("application/problem+json")), (_, _) -> true);
        String body = "{\"type\":\"" + PROBLEMS + slug + "\",\"title\":\"t\",\"status\":" + status + "}";
        return new ApiException(status, "failed", headers, body);
    }

    private static OtisLinkGateway gatewayThrowing(ApiException exception) {
        return new OtisLinkGateway(new AccountLinksApi() {
            @Override
            public LinkCodeDTO createLinkCode(UUID playerUuid, LinkCodeRequestDTO request) throws ApiException {
                throw exception;
            }

            @Override
            public List<AccountLinkDTO> listPlayerLinks(UUID playerUuid) throws ApiException {
                throw exception;
            }

            @Override
            public void deletePlayerLink(UUID playerUuid, String provider) throws ApiException {
                throw exception;
            }

            @Override
            public AccountLinkDTO putPlayerLink(UUID playerUuid, String provider, ProfileLinkRequestDTO request)
                    throws ApiException {
                throw exception;
            }
        });
    }

    @Test
    void requestCodeMapsTheIssuedCode() {
        List<String> requests = new ArrayList<>();
        OffsetDateTime expires = OffsetDateTime.of(2026, 1, 1, 12, 10, 0, 0, ZoneOffset.UTC);
        OtisLinkGateway gateway = new OtisLinkGateway(new AccountLinksApi() {
            @Override
            public LinkCodeDTO createLinkCode(UUID playerUuid, LinkCodeRequestDTO request) {
                requests.add(playerUuid + " " + request.getProvider());
                return new LinkCodeDTO().code("K7Q4-MZ2A").provider("discord").expiresAt(expires);
            }
        });

        GatewayResult<IssuedCode> result = gateway.requestCode(PLAYER, LinkProvider.DISCORD);

        assertEquals(new GatewayResult.Success<>(new IssuedCode("K7Q4-MZ2A", expires.toInstant())), result);
        assertEquals(List.of(PLAYER + " discord"), requests);
    }

    @Test
    void listLinksMapsVerifiedAndUnverifiedLinks() {
        OtisLinkGateway gateway = new OtisLinkGateway(new AccountLinksApi() {
            @Override
            public List<AccountLinkDTO> listPlayerLinks(UUID playerUuid) {
                return List.of(
                        new AccountLinkDTO().provider("discord").externalId("123").displayName("Steve#0001").verified(true),
                        new AccountLinkDTO().provider("youtube").value("https://www.youtube.com/@olf").verified(false),
                        new AccountLinkDTO().provider("twitch").externalId("456").verified(true));
            }
        });

        GatewayResult<List<LinkEntry>> result = gateway.listLinks(PLAYER);

        assertEquals(new GatewayResult.Success<>(List.of(
                new LinkEntry("discord", "Steve#0001", true),
                new LinkEntry("youtube", "https://www.youtube.com/@olf", false),
                new LinkEntry("twitch", "456", true))), result);
    }

    @Test
    void unlinkCallsTheDeleteEndpoint() {
        List<String> deleted = new ArrayList<>();
        OtisLinkGateway gateway = new OtisLinkGateway(new AccountLinksApi() {
            @Override
            public void deletePlayerLink(UUID playerUuid, String provider) {
                deleted.add(playerUuid + " " + provider);
            }
        });

        GatewayResult<Void> result = gateway.unlink(PLAYER, LinkProvider.DISCORD);

        assertEquals(new GatewayResult.Success<Void>(null), result);
        assertEquals(List.of(PLAYER + " discord"), deleted);
    }

    @Test
    void setProfileLinkSendsTheValueAndMapsTheLink() {
        List<String> sent = new ArrayList<>();
        OtisLinkGateway gateway = new OtisLinkGateway(new AccountLinksApi() {
            @Override
            public AccountLinkDTO putPlayerLink(UUID playerUuid, String provider, ProfileLinkRequestDTO request) {
                sent.add(provider + " " + request.getValue());
                return new AccountLinkDTO().provider(provider).value(request.getValue()).verified(false);
            }
        });

        GatewayResult<LinkEntry> result = gateway.setProfileLink(PLAYER, LinkProvider.GITHUB, "olf");

        assertEquals(new GatewayResult.Success<>(new LinkEntry("github", "olf", false)), result);
        assertEquals(List.of("github olf"), sent);
    }

    @Test
    void problemResponsesMapToTheProblemSlug() {
        GatewayResult<IssuedCode> result = gatewayThrowing(problem(429, "link-code-rate-limited"))
                .requestCode(PLAYER, LinkProvider.DISCORD);

        assertEquals(new GatewayResult.Problem<IssuedCode>("link-code-rate-limited"), result);
    }

    @Test
    void conflictOnProfileLinkMapsToTheProblemSlug() {
        GatewayResult<LinkEntry> result = gatewayThrowing(problem(409, "provider-already-linked"))
                .setProfileLink(PLAYER, LinkProvider.DISCORD, "steve");

        assertEquals(new GatewayResult.Problem<LinkEntry>("provider-already-linked"), result);
    }

    @Test
    void serverErrorsAreUnavailable() {
        GatewayResult<List<LinkEntry>> result = gatewayThrowing(problem(503, "service-unavailable")).listLinks(PLAYER);

        assertEquals(new GatewayResult.Unavailable<List<LinkEntry>>("HTTP 503"), result);
    }

    @Test
    void connectionErrorsAreUnavailableAndNameOnlyTheExceptionType() {
        GatewayResult<Void> result = gatewayThrowing(new ApiException(new IOException("connect to otis.internal:8080 refused")))
                .unlink(PLAYER, LinkProvider.TWITCH);

        assertEquals(new GatewayResult.Unavailable<Void>("IOException"), result);
    }

    @Test
    void clientErrorsWithoutProblemBodyAreUnavailable() {
        GatewayResult<List<LinkEntry>> result = gatewayThrowing(new ApiException(404, "not found"))
                .listLinks(PLAYER);

        assertInstanceOf(GatewayResult.Unavailable.class, result, "an older backend without link endpoints");
    }
}
