package net.onelitefeather.otis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import net.onelitefeather.otis.dto.PlayerSettingDTO;
import net.onelitefeather.otis.problem.ProblemDetailSchema;
import net.onelitefeather.otis.service.PlayerSettingService;
import net.onelitefeather.otis.service.PutResult;
import net.onelitefeather.otis.settings.SettingJson;

import java.util.List;
import java.util.UUID;

/**
 * Versioned HTTP API for per-player settings. Only maps HTTP to the service; the rules live there.
 */
@Controller("/v1/players/{playerUuid}/settings")
public class PlayerSettingController {

    private static final String PLAYER_UUID_DESCRIPTION = "Mojang uuid of the player; the player must be stored in Otis.";
    private static final String KEY_DESCRIPTION =
            "Setting key in Adventure key syntax, namespace:value. Use the namespace olf for generic settings "
                    + "shared by all services and the namespace of your game for game-specific ones. "
                    + "The namespace minecraft is reserved.";

    private final PlayerSettingService service;
    private final ObjectMapper mapper;

    @Inject
    public PlayerSettingController(PlayerSettingService service, @Named(SettingJson.MAPPER_NAME) ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(
            summary = "List the settings of a player",
            operationId = "listPlayerSettings",
            description = "Returns all settings of the player, or only those of the given namespaces.",
            tags = {"Player settings"}
    )
    @ApiResponse(responseCode = "200", description = "The settings, possibly an empty list",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = PlayerSettingDTO.class))))
    @ApiResponse(responseCode = "404", description = "Player not found (problem type player-not-found)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "400", description = "The request is invalid, e.g. the player uuid is malformed",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Get
    public HttpResponse<List<PlayerSettingDTO>> list(
            @Parameter(description = PLAYER_UUID_DESCRIPTION) @PathVariable UUID playerUuid,
            @Parameter(description = "Only return settings of this namespace, e.g. lobby; repeat the parameter for several namespaces.",
                    explode = Explode.TRUE)
            @QueryValue("namespace") @Nullable List<String> namespace) {
        return HttpResponse.ok(service.list(playerUuid, namespace == null ? List.of() : namespace));
    }

    @Operation(
            summary = "Get one setting of a player",
            operationId = "getPlayerSetting",
            description = "Returns the setting stored under the key.",
            tags = {"Player settings"}
    )
    @ApiResponse(responseCode = "200", description = "The setting",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerSettingDTO.class)))
    @ApiResponse(responseCode = "404",
            description = "Player or setting not found (problem types player-not-found, setting-not-found)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "400",
            description = "Invalid key (problem types missing-namespace, reserved-namespace, invalid-setting-key)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Get("/{key:.+}")
    public HttpResponse<PlayerSettingDTO> get(
            @Parameter(description = PLAYER_UUID_DESCRIPTION) @PathVariable UUID playerUuid,
            @Parameter(description = KEY_DESCRIPTION, schema = @Schema(type = "string", format = "adventure-key", example = "lobby:player_hider"))
            @PathVariable String key) {
        return HttpResponse.ok(service.get(playerUuid, key));
    }

    @Operation(
            summary = "Store a setting of a player",
            operationId = "putPlayerSetting",
            description = "Creates the setting or replaces its value (last write wins). Putting a value that is "
                    + "semantically equal to the stored one changes nothing and keeps version and updatedAt.",
            tags = {"Player settings"}
    )
    @RequestBody(description = "The setting value: any JSON value (object, array, string, number, boolean or null), at most 64 KiB serialized.",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", nullable = true, description = "Any JSON value, not only an object.")))
    @ApiResponse(responseCode = "201", description = "The setting was created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerSettingDTO.class)))
    @ApiResponse(responseCode = "200", description = "The setting existed; it was replaced or, for an equal value, left unchanged",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerSettingDTO.class)))
    @ApiResponse(responseCode = "404", description = "Player not found (problem type player-not-found)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "400",
            description = "Invalid key or body (problem types missing-namespace, reserved-namespace, invalid-setting-key, invalid-setting-value)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "413", description = "The value exceeds 64 KiB (problem type setting-value-too-large)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Put("/{key:.+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public HttpResponse<PlayerSettingDTO> put(
            @Parameter(description = PLAYER_UUID_DESCRIPTION) @PathVariable UUID playerUuid,
            @Parameter(description = KEY_DESCRIPTION, schema = @Schema(type = "string", format = "adventure-key", example = "lobby:player_hider"))
            @PathVariable String key,
            @Body @Nullable String body) {
        PutResult result = service.put(playerUuid, key, SettingJson.parse(mapper, body));
        return switch (result) {
            case PutResult.Created created -> HttpResponse.created(created.setting());
            case PutResult.Updated updated -> HttpResponse.ok(updated.setting());
            case PutResult.Unchanged unchanged -> HttpResponse.ok(unchanged.setting());
        };
    }

    @Operation(
            summary = "Delete a setting of a player",
            operationId = "deletePlayerSetting",
            description = "Removes the setting. Deleting a setting that does not exist succeeds as well.",
            tags = {"Player settings"}
    )
    @ApiResponse(responseCode = "204", description = "The setting does not exist (any more)")
    @ApiResponse(responseCode = "404", description = "Player not found (problem type player-not-found)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "400",
            description = "Invalid key (problem types missing-namespace, reserved-namespace, invalid-setting-key)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Delete("/{key:.+}")
    public HttpResponse<Void> delete(
            @Parameter(description = PLAYER_UUID_DESCRIPTION) @PathVariable UUID playerUuid,
            @Parameter(description = KEY_DESCRIPTION, schema = @Schema(type = "string", format = "adventure-key", example = "lobby:player_hider"))
            @PathVariable String key) {
        service.delete(playerUuid, key);
        return HttpResponse.noContent();
    }
}
