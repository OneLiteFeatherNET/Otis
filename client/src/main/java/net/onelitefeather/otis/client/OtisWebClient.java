package net.onelitefeather.otis.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.onelitefeather.otis.client.data.OtisPlayer;
import net.onelitefeather.otis.client.util.GenericBodyHandler;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.fasterxml.jackson.databind.type.TypeFactory.defaultInstance;

public final class OtisWebClient implements OtisClient {

    private static final GenericBodyHandler<List<OtisPlayer>> LIST_HANDLER = new GenericBodyHandler<>(defaultInstance().constructArrayType(defaultInstance().constructType(OtisPlayer.class)));
    private static final GenericBodyHandler<OtisPlayer> SINGLE_TEMPLATE_HANDLER = new GenericBodyHandler<>(defaultInstance().constructType(OtisPlayer.class));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String url;
    private final HttpClient client;

    public OtisWebClient(@NotNull String url, @NotNull Duration timeout) {
        this.url = url;
        this.client = HttpClient
                .newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public @NotNull CompletableFuture<OtisPlayer> addAsync(@NotNull OtisPlayer player) {
        String objectString = mapObjectToString(player);
        HttpRequest addRequest = HttpRequest
                .newBuilder()
                .uri(URI.create(buildUrl("/otis")))
                .POST(HttpRequest.BodyPublishers.ofString(objectString))
                .build();
        CompletableFuture<HttpResponse<OtisPlayer>> addResponse =
                this.client.sendAsync(addRequest, SINGLE_TEMPLATE_HANDLER);
        return addResponse.thenApply(HttpResponse::body);
    }

    @Override
    public @NotNull Optional<OtisPlayer> get(@NotNull UUID owner) {
        HttpRequest getRequest = HttpRequest
                .newBuilder()
                .uri(URI.create(buildUrl("/otis/" + owner)))
                .GET()
                .build();
        CompletableFuture<HttpResponse<OtisPlayer>> addResponse =
                this.client.sendAsync(getRequest, SINGLE_TEMPLATE_HANDLER);
        return Optional.ofNullable(addResponse.join().body());
    }

    @Override
    public @NotNull OtisPlayer update(@NotNull UUID owner, @NotNull OtisPlayer player) {
        String objectString = mapObjectToString(player);
        HttpRequest updateRequest = HttpRequest
                .newBuilder()
                .uri(URI.create(buildUrl("/otis/update/" + owner)))
                .POST(HttpRequest.BodyPublishers.ofString(objectString))
                .build();
        CompletableFuture<HttpResponse<OtisPlayer>> updateResponse =
                this.client.sendAsync(updateRequest, SINGLE_TEMPLATE_HANDLER);
        return updateResponse.join().body();
    }

    @Override
    public @NotNull CompletableFuture<OtisPlayer> deleteAsync(@NotNull UUID owner) {
        HttpRequest deleteRequest = HttpRequest
                .newBuilder()
                .uri(URI.create(buildUrl("/otis/delete/" + owner)))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        CompletableFuture<HttpResponse<OtisPlayer>> deleteResponse =
                this.client.sendAsync(deleteRequest, SINGLE_TEMPLATE_HANDLER);
        return deleteResponse.thenApply(HttpResponse::body);
    }

    @Override
    public @NotNull CompletableFuture<Optional<OtisPlayer>> searchAsyncByName(@NotNull String name) {
        HttpRequest getRequest = HttpRequest
                .newBuilder()
                .uri(URI.create(buildUrl("/search/byName/" + name)))
                .GET()
                .build();
        CompletableFuture<HttpResponse<OtisPlayer>> searchResponse =
                this.client.sendAsync(getRequest, SINGLE_TEMPLATE_HANDLER);
        return searchResponse.thenApply(response -> Optional.ofNullable(response.body()));
    }

    @Override
    public @NotNull CompletableFuture<Optional<OtisPlayer>> searchAsyncById(@NotNull UUID owner) {
        HttpRequest getRequest = HttpRequest
                .newBuilder()
                .uri(URI.create(buildUrl("/search/byId/" + owner)))
                .GET()
                .build();
        CompletableFuture<HttpResponse<OtisPlayer>> searchResponse =
                this.client.sendAsync(getRequest, SINGLE_TEMPLATE_HANDLER);
        return searchResponse.thenApply(response -> Optional.ofNullable(response.body()));
    }

    @Override
    public @NotNull List<OtisPlayer> getAll(int page, int number) {
        HttpRequest getRequest = HttpRequest
                .newBuilder()
                .uri(URI.create(buildUrl("/otis/all?page=" + page + "&number=" + number)))
                .GET()
                .build();
        CompletableFuture<HttpResponse<List<OtisPlayer>>> addResponse =
                this.client.sendAsync(getRequest, LIST_HANDLER);
        return addResponse.join().body();
    }

    /**
     * Creates a string representation of the object.
     *
     * @param object the object to map
     * @return the string representation
     */
    @NotNull String mapObjectToString(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to map object to string", exception);
        }
    }

    /**
     * Build the URL for the request.
     *
     * @param path the path to the resource
     * @return the URL
     */
    @NotNull String buildUrl(@NotNull String path) {
        return this.url + path;
    }
}
