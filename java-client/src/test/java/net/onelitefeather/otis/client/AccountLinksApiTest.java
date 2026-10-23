package net.onelitefeather.otis.client;

import net.onelitefeather.otis.client.api.AccountLinksApi;
import net.onelitefeather.otis.client.invoker.ApiClient;
import net.onelitefeather.otis.client.invoker.ApiException;
import net.onelitefeather.otis.client.model.AccountLinkDTO;
import net.onelitefeather.otis.client.model.LinkLookupDTO;
import net.onelitefeather.otis.client.model.ProblemDetail;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated account links API and models, without any network. */
class AccountLinksApiTest {

    private static final UUID PLAYER = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

    private final ApiClient client = OtisClients.newApiClient();

    @Test
    void deserializesAVerifiedAccountLink() throws Exception {
        String sample = """
                {"provider":"discord","externalId":"123456789012345678","displayName":"meinerlp",
                 "verified":true,"linkedAt":"2026-01-01T12:00:00Z"}
                """;

        AccountLinkDTO link = client.getObjectMapper().readValue(sample, AccountLinkDTO.class);

        assertEquals("discord", link.getProvider(), "provider");
        assertEquals("123456789012345678", link.getExternalId(), "externalId");
        assertEquals("meinerlp", link.getDisplayName(), "displayName");
        assertEquals(Boolean.TRUE, link.getVerified(), "verified");
        assertEquals(OffsetDateTime.parse("2026-01-01T12:00:00Z").toInstant(), link.getLinkedAt().toInstant(), "linkedAt");
    }

    @Test
    void deserializesAnUnverifiedAccountLinkWithoutExternalId() throws Exception {
        String sample = """
                {"provider":"youtube","value":"https://www.youtube.com/@onelitefeather","verified":false,
                 "linkedAt":"2026-01-01T12:00:00Z"}
                """;

        AccountLinkDTO link = client.getObjectMapper().readValue(sample, AccountLinkDTO.class);

        assertEquals("https://www.youtube.com/@onelitefeather", link.getValue(), "value");
        assertEquals(null, link.getExternalId(), "an unverified link has no externalId");
        assertFalse(link.getVerified(), "verified");
    }

    @Test
    void deserializesALookupWithPlayerAndLink() throws Exception {
        String sample = """
                {"playerUuid":"123e4567-e89b-12d3-a456-426614174001",
                 "link":{"provider":"discord","externalId":"1","verified":true,"linkedAt":"2026-01-01T12:00:00Z"}}
                """;

        LinkLookupDTO lookup = client.getObjectMapper().readValue(sample, LinkLookupDTO.class);

        assertEquals(PLAYER, lookup.getPlayerUuid(), "player");
        assertEquals("1", lookup.getLink().getExternalId(), "link");
    }

    @Test
    void readsALinkCodeInvalidProblem() {
        var body = """
                {"type":"https://otis.onelitefeather.net/problems/link-code-invalid","title":"Link code invalid",
                 "status":410,"detail":"The link code is invalid or has expired."}
                """;
        HttpHeaders headers = HttpHeaders.of(Map.of("Content-Type", List.of("application/problem+json")), (n, v) -> true);

        Optional<ProblemDetail> problem = ProblemDetails.from(new ApiException(410, "gone", headers, body));

        assertTrue(problem.isPresent(), "a problem document must be recognised");
        assertEquals("https://otis.onelitefeather.net/problems/link-code-invalid", problem.get().getType(), "type");
        assertEquals(410, problem.get().getStatus(), "status");
    }

    @Test
    void lookupRequestPathHasProviderAndExternalId() {
        AtomicReference<HttpRequest> request = new AtomicReference<>();
        AccountLinksApi api = new AccountLinksApi(OtisClients.newApiClient().setHost("otis.invalid").setPort(8080)
                .setScheme("http").setRequestInterceptor(builder -> {
                    request.set(builder.build());
                    throw new IllegalStateException("request captured");
                }));

        assertThrows(IllegalStateException.class, () -> api.lookupLink("discord", "42"), "the interceptor aborts the call");

        assertEquals("/v1/links/discord/42", request.get().uri().getRawPath(), "path");
        assertEquals("GET", request.get().method(), "method");
    }
}
