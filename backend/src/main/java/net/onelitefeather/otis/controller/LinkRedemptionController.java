package net.onelitefeather.otis.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import net.onelitefeather.otis.dto.LinkLookupDTO;
import net.onelitefeather.otis.dto.RedeemRequestDTO;
import net.onelitefeather.otis.problem.ProblemDetailSchema;
import net.onelitefeather.otis.service.AccountLinkService;

/**
 * Service-side account link API: the external service (e.g. the Discord bot) redeems codes and looks up
 * links by external account. Only maps HTTP to the service; the rules live there.
 * <p>
 * Deliberately separate from {@link PlayerLinkController}: the two groups are used by different callers
 * and get different access scopes once service authentication is added.
 */
@Validated
@Controller("/v1")
public class LinkRedemptionController {

    private static final String PROBLEM = "application/problem+json";

    private final AccountLinkService service;

    @Inject
    public LinkRedemptionController(AccountLinkService service) {
        this.service = service;
    }

    @Operation(
            summary = "Redeem a link code",
            operationId = "redeemLinkCode",
            description = "Consumes a one-time code and links the player of the code to the external account. "
                    + "Unknown, expired, used and foreign-provider codes are answered identically.",
            tags = {"Account links"}
    )
    @ApiResponse(responseCode = "201", description = "The verified link and its player",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LinkLookupDTO.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request (problem types unsupported-provider, constraint violations)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "409",
            description = "Conflict; the code stays redeemable (problem types external-account-already-linked, provider-already-linked)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "410", description = "The code is invalid or expired (problem type link-code-invalid)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Post("/link-codes/redeem")
    @Consumes(MediaType.APPLICATION_JSON)
    public HttpResponse<LinkLookupDTO> redeem(@Body @Valid RedeemRequestDTO request) {
        return HttpResponse.created(service.redeem(request));
    }

    @Operation(
            summary = "Find the player of an external account",
            operationId = "lookupLink",
            description = "Returns the player and link of a verified external account. Unverified links are never found.",
            tags = {"Account links"}
    )
    @ApiResponse(responseCode = "200", description = "The player and the verified link",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LinkLookupDTO.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request (problem type unsupported-provider)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "404", description = "No verified link (problem type link-not-found)",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetailSchema.class)))
    @Get("/links/{provider}/{externalId}")
    public HttpResponse<LinkLookupDTO> lookup(
            @Parameter(description = "Provider: discord, twitch, youtube, x, tiktok or github.") @PathVariable String provider,
            @Parameter(description = "Id of the external account at the provider.") @PathVariable String externalId) {
        return HttpResponse.ok(service.lookup(provider, externalId));
    }
}
