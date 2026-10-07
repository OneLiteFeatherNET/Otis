package net.onelitefeather.otis.api;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import net.onelitefeather.otis.database.repository.PlayerSettingRepository;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario "Concurrent first write": writers that start together on virtual threads never see a server
 * error and leave exactly one setting behind. No sleeps: the writers are released by a latch and joined.
 */
@MicronautTest(environments = "test", transactional = false)
class ConcurrentFirstWriteTest {

    private static final int ROUNDS = 10;
    private static final int WRITERS = 4;

    @Inject
    EmbeddedServer server;

    @Inject
    OtisPlayerRepository players;

    @Inject
    PlayerSettingRepository settings;

    private int put(HttpClient client, UUID player, String key, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + server.getPort() + "/v1/players/" + player + "/settings/" + key))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void simultaneousFirstWritesCreateExactlyOneSettingAndNeverFail() throws Exception {
        UUID mojangUuid = UUID.randomUUID();
        OtisPlayer player = players.save(new OtisPlayer(
                null, mojangUuid, "Racer", 1L, 2L, Map.of(), Locale.US));
        try (var virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
             HttpClient client = HttpClient.newBuilder().executor(virtualThreads).build()) {
            Executor executor = virtualThreads;
            for (int round = 0; round < ROUNDS; round++) {
                final int current = round;
                String key = "lobby:race_" + current;
                CountDownLatch go = new CountDownLatch(1);
                List<CompletableFuture<Integer>> writers = new ArrayList<>();
                for (int writer = 0; writer < WRITERS; writer++) {
                    String json = "{\"writer\":" + writer + "}";
                    writers.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            go.await();
                            return put(client, mojangUuid, key, json);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    }, executor));
                }
                go.countDown();
                for (CompletableFuture<Integer> writer : writers) {
                    int status = writer.get();
                    assertTrue(status == 200 || status == 201, "writer in round " + round + " got status " + status);
                }

                var stored = settings.findAllByPlayerAndNamespaces(player.getUuid(), List.of("lobby")).stream()
                        .filter(s -> s.getKeyValue().equals("race_" + current)).toList();
                assertEquals(1, stored.size(), "exactly one setting for " + key);
            }
        } finally {
            players.deleteById(player.getUuid());
        }
    }
}
