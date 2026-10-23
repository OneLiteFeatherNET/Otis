package net.onelitefeather.otis.api;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario "Concurrent redeem of one external account": two players redeem their codes for the same external
 * account at the same time on virtual threads. Exactly one link is created, the other request answers 409 and
 * its code is still redeemable. No sleeps: the requests are released by a latch and joined.
 */
@MicronautTest(environments = "test", transactional = false)
class ConcurrentRedeemTest {

    private static final int ROUNDS = 10;
    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");

    @Inject
    EmbeddedServer server;

    @Inject
    OtisPlayerRepository players;

    private String url(String path) {
        return "http://localhost:" + server.getPort() + path;
    }

    private HttpResponse<String> post(HttpClient client, String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private UUID storePlayer() {
        UUID mojangUuid = UUID.randomUUID();
        players.save(new OtisPlayer(null, mojangUuid, "R" + mojangUuid.toString().substring(0, 8).replace('-', '_'),
                1L, 2L, Map.of(), Locale.US));
        return mojangUuid;
    }

    private String issue(HttpClient client, UUID player) throws Exception {
        HttpResponse<String> response = post(client, "/v1/players/" + player + "/link-codes", "{\"provider\":\"discord\"}");
        assertEquals(201, response.statusCode(), "issuing a code");
        Matcher matcher = CODE.matcher(response.body());
        assertTrue(matcher.find(), "the response carries a code");
        return matcher.group(1);
    }

    private static String redeemBody(String code, String externalId) {
        return "{\"code\":\"" + code + "\",\"provider\":\"discord\",\"externalId\":\"" + externalId + "\"}";
    }

    @Test
    void simultaneousRedeemsOfOneExternalAccountCreateOneLinkAndKeepTheLosingCode() throws Exception {
        try (var virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
             HttpClient client = HttpClient.newBuilder().executor(virtualThreads).build()) {
            for (int round = 0; round < ROUNDS; round++) {
                String account = "race-" + round + "-" + UUID.randomUUID();
                UUID playerA = storePlayer();
                UUID playerB = storePlayer();
                String codeA = issue(client, playerA);
                String codeB = issue(client, playerB);

                CountDownLatch go = new CountDownLatch(1);
                List<CompletableFuture<Integer>> redeems = List.of(
                        CompletableFuture.supplyAsync(() -> redeemWhenReleased(client, go, codeA, account), virtualThreads),
                        CompletableFuture.supplyAsync(() -> redeemWhenReleased(client, go, codeB, account), virtualThreads));
                go.countDown();
                List<Integer> statuses = redeems.stream().map(CompletableFuture::join).sorted().toList();

                assertEquals(List.of(201, 409), statuses, "round " + round + ": one winner and one conflict");
                String loserCode = redeems.get(0).join() == 201 ? codeB : codeA;
                HttpResponse<String> retry = post(client, "/v1/link-codes/redeem", redeemBody(loserCode, account + "-other"));
                assertEquals(201, retry.statusCode(), "round " + round + ": the losing code must still be redeemable");
            }
        }
    }

    private int redeemWhenReleased(HttpClient client, CountDownLatch go, String code, String account) {
        try {
            go.await();
            return post(client, "/v1/link-codes/redeem", redeemBody(code, account)).statusCode();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
