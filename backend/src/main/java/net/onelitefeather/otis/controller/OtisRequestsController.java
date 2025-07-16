package net.onelitefeather.otis.controller;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import net.onelitefeather.otis.dto.OtisPlayerDTO;

import java.util.List;
import java.util.UUID;

@Controller("/otis")
public class OtisRequestsController {

    private final OtisPlayerRepository repository;

    @Inject
    public OtisRequestsController(OtisPlayerRepository repository) {
        this.repository = repository;
    }

    @Operation(
            summary = "Add a new Otis player",
            description = "This endpoint allows you to add a new Otis player to the database.",
            tags = {"Player"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "Player added successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = OtisPlayerDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "500",
            description = "The player could not be added to the database",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = String.class)
            )
    )
    @Validated
    @Post
    public HttpResponse<OtisPlayerDTO> add(@Valid OtisPlayerDTO playerDTO) {
        OtisPlayer otisPlayer = OtisPlayer.toEntity(playerDTO);
        OtisPlayer saved = repository.save(otisPlayer);
        return HttpResponse.ok(saved.toDto());
    }

    @Operation(
            summary = "Get Otis player by ID",
            description = "This endpoint retrieves an Otis player by their UUID.",
            tags = {"Player"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "Player was successfully found.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = OtisPlayerDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Player not found",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = String.class)
            )
    )
    @Validated
    @Get("/byId/{owner}")
    public HttpResponse<OtisPlayerDTO> getById(@Valid UUID owner) {
        OtisPlayer entity = this.repository.findById(owner).orElse(null);
        if (entity == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(entity.toDto());
    }

    @Operation(
            summary = "Get Otis player by name",
            description = "This endpoint retrieves an Otis player by their name.",
            tags = {"Player"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "Player was successfully found.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = OtisPlayerDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Player not found",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = String.class)
            )
    )
    @Validated
    @Get("/byName/{owner}")
    public HttpResponse<OtisPlayerDTO> getByString(@Valid String owner) {
        OtisPlayer entity = this.repository.findById(UUID.fromString(owner)).orElse(null);
        if (entity == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(entity.toDto());
    }

    @Operation(
            summary = "Update an existing Otis player",
            description = "This endpoint allows you to update an existing Otis player in the database.",
            tags = {"Player"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "Player updated successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = OtisPlayerDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Bad request, player UUID does not match the owner",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = String.class)
            )
    )
    @Validated
    @Post("/update/{owner}")
    public HttpResponse<OtisPlayerDTO> update(@Valid UUID owner, @Valid OtisPlayerDTO playerDTO) {
        if (!playerDTO.uuid().equals(owner)) {
            return HttpResponse.badRequest();
        }
        OtisPlayer otisPlayer = OtisPlayer.toEntity(playerDTO);
        OtisPlayer saved = repository.save(otisPlayer);
        return HttpResponse.ok(saved.toDto());
    }

    @Operation(
            summary = "Delete an Otis player",
            description = "This endpoint allows you to delete an Otis player from the database.",
            tags = {"Player"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "Player deleted successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = OtisPlayerDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Player not found",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = String.class)
            )
    )
    @Validated
    @Post("/delete/{owner}")
    public HttpResponse<OtisPlayerDTO> delete(@Valid UUID owner) {
        OtisPlayer entity = this.repository.findById(owner).orElse(null);
        if (entity == null) {
            return HttpResponse.notFound();
        }
        this.repository.delete(entity);
        return HttpResponse.ok(entity.toDto());
    }

    /**
     * Get all players from the database.
     *
     * @param pageable the pageable instance
     * @return a list of all players
     */
    @Operation(
            summary = "Get all Otis players",
            description = "This endpoint retrieves all Otis players from the database.",
            tags = {"Player"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "Players retrieved successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = OtisPlayerDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "No players found",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = String.class)
            )
    )
    @Get(uris = {"/getAll", "/all"})
    public HttpResponse<Iterable<OtisPlayerDTO>> getAll(Pageable pageable) {
        Page<OtisPlayer> entities = this.repository.findAll(pageable);
        if (entities.isEmpty()) {
            return HttpResponse.ok(List.of());
        }
        return HttpResponse.ok(entities.map(OtisPlayer::toDto));
    }
}
