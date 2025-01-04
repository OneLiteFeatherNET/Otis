package net.onelitefeather.otis.client.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.onelitefeather.otis.client.jackson.OtisJacksonModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpResponse;
import java.nio.charset.Charset;

public final class GenericBodyHandler<T> implements HttpResponse.BodyHandler<T> {

    private final JavaType typeReference;
    private final ObjectMapper objectMapper;

    public GenericBodyHandler(JavaType typeReference) {
        this.typeReference = typeReference;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(OtisJacksonModule.INSTANCE);
    }

    @Override
    public HttpResponse.BodySubscriber<T> apply(@NotNull HttpResponse.ResponseInfo responseInfo) {
        if (responseInfo.statusCode() != 200) return HttpResponse.BodySubscribers.replacing(null);
        return HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofString(Charset.defaultCharset()), this::parseJsonData
        );
    }

    /**
     * Parse the JSON data into a list of objects.
     *
     * @param data JSON data
     * @return List of objects
     */
    protected @Nullable T parseJsonData(@NotNull String data) {
        try {
            return this.objectMapper.readValue(data, this.typeReference);
        } catch (Exception exception) {
            return null;
        }
    }
}
