package net.onelitefeather.otis.client;

import net.kyori.adventure.key.Key;
import net.onelitefeather.otis.client.api.PlayerSettingsApi;
import net.onelitefeather.otis.client.invoker.ApiClient;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Looks at the requests the generated settings API builds, without any network: the request interceptor
 * captures the request and aborts the call.
 */
class SettingsApiRequestTest {

    private static final UUID PLAYER = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

    private static final class Captured extends RuntimeException {
        Captured() {
            super("request captured", null, false, false);
        }
    }

    private final AtomicReference<HttpRequest> request = new AtomicReference<>();
    private final PlayerSettingsApi api = new PlayerSettingsApi(
            OtisClients.newApiClient().setHost("otis.invalid").setPort(8080).setScheme("http")
                    .setRequestInterceptor(builder -> {
                        request.set(builder.build());
                        throw new Captured();
                    }));

    @Test
    void keyPathParameterIsTheEncodedNamespaceValueString() {
        assertThrows(Captured.class, () -> api.getPlayerSetting(PLAYER, Key.key("lobby", "player_hider")),
                "the interceptor aborts the call");

        assertEquals("/v1/players/" + PLAYER + "/settings/lobby%3Aplayer_hider", request.get().uri().getRawPath(),
                "key must be sent as namespace:value, not as Key.toString() debug output");
        assertEquals("GET", request.get().method(), "method");
    }

    @Test
    void keyValueWithSlashIsEncodedIntoOneSegment() {
        assertThrows(Captured.class, () -> api.deletePlayerSetting(PLAYER, Key.key("lobby", "shop/layout")),
                "the interceptor aborts the call");

        assertEquals("/v1/players/" + PLAYER + "/settings/lobby%3Ashop%2Flayout", request.get().uri().getRawPath(),
                "slash of the key value");
        assertEquals("DELETE", request.get().method(), "method");
    }

    @Test
    void repeatedNamespaceQueryParameters() {
        assertThrows(Captured.class, () -> api.listPlayerSettings(PLAYER, List.of("olf", "lobby")),
                "the interceptor aborts the call");

        assertEquals("namespace=olf&namespace=lobby", request.get().uri().getRawQuery(), "multi-valued query");
    }

    @Test
    void putSendsTheValueAsJsonBody() {
        assertThrows(Captured.class, () -> api.putPlayerSetting(PLAYER, Key.key("lobby", "player_hider"),
                Map.of("enabled", true)), "the interceptor aborts the call");

        HttpRequest put = request.get();
        assertEquals("PUT", put.method(), "method");
        assertEquals("application/json", put.headers().firstValue("Content-Type").orElse(null), "content type");
        assertEquals("{\"enabled\":true}", bodyOf(put), "body is the plain JSON value");
    }

    @Test
    void putSendsScalarValueAsJsonString() {
        assertThrows(Captured.class, () -> api.putPlayerSetting(PLAYER, Key.key("olf", "language"), "de_de"),
                "the interceptor aborts the call");

        assertEquals("\"de_de\"", bodyOf(request.get()), "a string value is a JSON string");
    }

    private static String bodyOf(HttpRequest httpRequest) {
        var publisher = httpRequest.bodyPublisher().orElseThrow();
        var out = new java.io.ByteArrayOutputStream();
        publisher.subscribe(new Flow.Subscriber<java.nio.ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(java.nio.ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        return out.toString(StandardCharsets.UTF_8);
    }
}
