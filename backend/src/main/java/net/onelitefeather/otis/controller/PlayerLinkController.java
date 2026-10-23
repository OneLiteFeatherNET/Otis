package net.onelitefeather.otis.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import net.onelitefeather.otis.dto.AccountLinkDTO;
import net.onelitefeather.otis.dto.LinkCodeDTO;
import net.onelitefeather.otis.dto.LinkCodeRequestDTO;
import net.onelitefeather.otis.dto.ProfileLinkRequestDTO;
import net.onelitefeather.otis.problem.ProblemDetailSchema;
import net.onelitefeather.otis.service.AccountLinkService;

import java.util.List;
import java.util.UUID;

/**
 * Game-side account link API: the proxy or game server issues link codes for a player and manages the
 * player's links. Only maps HTTP to the service; the rules live there.
 * <p>
 * Deliberately separate from {@link LinkRedemptionController}: the two groups are used by different callers
 * and get different access scopes once service authentication is added.
 */
@Validated
@Controller("/v1/players/{playerUuid}")
public class PlayerLinkController {

    private static final String PLAYER_UUID_DESCRIPTION = "Mojang uuid of the player; the player must be stored in Otis.";
    private static final String PROVIDER_DESCRIPTION = "Provider: discord, twitch, youtube, x, tiktok or github.";
    private static final String PROBLEM = "application/problem+json";

    private final AccountLinkService service;

    @Inject
    public PlayerLinkController(AccountLinkService service) {
        this.service = service;
    }

    @Operation(
            summary = "Issue a link code for a player",
            operationId = "createLinkCode",
            description = "Issues a one-time code (valid 10 minutes) the player enters at the external service. "
                    + "A previous open code of the same provider is revoked. At most 5 codes per player and hour.",
            tags = {"Account links"}
    )
    @ApiResponse(responseCode = "201", description = "The code; it is shown only once",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LinkCodeDTO.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request (problem types unsupported-provider, constraint violations)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "404", description = "Player not found (problem type player-not-found)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "429", description = "Too many codes (problem type link-code-rate-limited)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Post("/link-codes")
    @Consumes(MediaType.APPLICATION_JSON)
    public HttpResponse<LinkCodeDTO> createLinkCode(
            @Parameter(description = PLAYER_UUID_DESCRIPTION) @PathVariable UUID playerUuid,
            @Body @Valid LinkCodeRequestDTO request) {
        return HttpResponse.created(service.issueCode(playerUuid, request.provider()));
    }

    @Operation(
            summary = "List the links of a player",
            operationId = "listPlayerLinks",
            description = "Returns the verified and unverified links of the player.",
            tags = {"Account links"}
    )
    @ApiResponse(responseCode = "200", description = "The links, possibly an empty list",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AccountLinkDTO.class))))
    @ApiResponse(responseCode = "404", description = "Player not found (problem type player-not-found)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "400", description = "The request is invalid, e.g. the player uuid is malformed",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Get("/links")
    public HttpResponse<List<AccountLinkDTO>> listLinks(
            @Parameter(description = PLAYER_UUID_DESCRIPTION) @PathVariable UUID playerUuid) {
        return HttpResponse.ok(service.list(playerUuid));
    }

    @Operation(
            summary = "Set an unverified link of a player",
            operationId = "putPlayerLink",
            description = "Stores a public, unverified link (handle or https URL on the provider's domain) and "
                    + "replaces a previous unverified link of the provider. A verified link is never overwritten.",
            tags = {"Account links"}
    )
    @ApiResponse(responseCode = "200", description = "The stored link",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccountLinkDTO.class)))
    @ApiResponse(responseCode = "400",
            description = "Invalid request (problem types unsupported-provider, invalid-link-value)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "404", description = "Player not found (problem type player-not-found)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "409", description = "The player has a verified link for the provider (problem type provider-already-linked)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Put("/links/{provider}")
    @Consumes(MediaType.APPLICATION_JSON)
    public HttpResponse<AccountLinkDTO> putLink(
            @Parameter(description = PLAYER_UUID_DESCRIPTION) @PathVariable UUID playerUuid,
            @Parameter(description = PROVIDER_DESCRIPTION) @PathVariable String provider,
            @Body @Valid ProfileLinkRequestDTO request) {
        return HttpResponse.ok(service.putUnverified(playerUuid, provider, request.value()));
    }

    @Operation(
            summary = "Remove a link of a player",
            operationId = "deletePlayerLink",
            description = "Removes the verified or unverified link of the provider. Removing a link that does not "
                    + "exist succeeds as well.",
            tags = {"Account links"}
    )
    @ApiResponse(responseCode = "204", description = "The player has no link for the provider (any more)")
    @ApiResponse(responseCode = "400", description = "Invalid request (problem type unsupported-provider)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "404", description = "Player not found (problem type player-not-found)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Delete("/links/{provider}")
    public HttpResponse<Void> deleteLink(
            @Parameter(description = PLAYER_UUID_DESCRIPTION) @PathVariable UUID playerUuid,
            @Parameter(description = PROVIDER_DESCRIPTION) @PathVariable String provider) {
        service.delete(playerUuid, provider);
        return HttpResponse.noContent();
    }
}
