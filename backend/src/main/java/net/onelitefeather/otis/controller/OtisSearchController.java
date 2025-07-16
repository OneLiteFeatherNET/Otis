package net.onelitefeather.otis.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import net.onelitefeather.otis.dto.OtisPlayerDTO;

import java.util.Optional;
import java.util.UUID;

@Controller("/search")
public class OtisSearchController {

    private final OtisPlayerRepository otisPlayerRepository;

    /**
     * Constructs a new reference to the OtisSearchController.
     *
     * @param playerRepository the repository to access player data.
     */
    @Inject
    public OtisSearchController(OtisPlayerRepository playerRepository) {
        this.otisPlayerRepository = playerRepository;
    }

    @Operation(
            summary = "Search for a player by their ID",
            description = "Returns the player information if found, otherwise returns 404 Not Found.",
            tags = {"Player", "Search"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "Player found and returned successfully.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = OtisPlayerDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Player not found.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = String.class)
            )
    )
    @Valid
    @Get("/byId/{id}")
    public HttpResponse<OtisPlayerDTO> searchById(@Valid UUID id) {
        Optional<OtisPlayer> entity = otisPlayerRepository.findById(id);

        if (entity.isEmpty()) {
            return HttpResponse.notFound();
        }

        return HttpResponse.ok(entity.get().toDto());
    }

    @Operation(
            summary = "Search for a player by their name",
            description = "Returns the player information if found, otherwise returns 404 Not Found.",
            tags = {"Player", "Search"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "Player found and returned successfully.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = OtisPlayerDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Player not found.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = String.class)
            )
    )
    @Valid
    @Get("/byName/{name}")
    public HttpResponse<OtisPlayerDTO> searchByName(
            @Valid
            @Size(min = 3, max = 16)
            @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain alphanumeric characters and underscores.")
            String name
    ) {
        Optional<OtisPlayer> entity = otisPlayerRepository.findByName(name);

        if (entity.isEmpty()) {
            return HttpResponse.notFound();
        }

        return HttpResponse.ok(entity.get().toDto());
    }
}
