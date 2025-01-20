package net.theevilreaper.otis.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.theevilreaper.otis.database.entity.OtisPlayer;
import net.theevilreaper.otis.database.repository.OtisPlayerRepository;
import net.theevilreaper.otis.dto.OtisPlayerDTO;

import java.util.Optional;
import java.util.UUID;

@Controller("/search")
public class OtisSearchController {

    private final OtisPlayerRepository otisPlayerRepository;

    @Inject
    public OtisSearchController(OtisPlayerRepository otisPlayerRepository) {
        this.otisPlayerRepository = otisPlayerRepository;
    }

    @Valid
    @Get("/byId/{id}")
    public HttpResponse<OtisPlayerDTO> searchById(@Valid UUID id) {
        Optional<OtisPlayer> entity = otisPlayerRepository.findById(id);

        if (entity.isEmpty()) {
            return HttpResponse.notFound();
        }

        return HttpResponse.ok(entity.get().toDto());
    }

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
