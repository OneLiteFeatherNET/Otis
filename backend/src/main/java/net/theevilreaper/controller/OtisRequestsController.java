package net.theevilreaper.controller;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import net.theevilreaper.database.entity.OtisPlayer;
import net.theevilreaper.database.repository.OtisPlayerRepository;
import net.theevilreaper.dto.OtisPlayerDTO;

import java.util.List;
import java.util.UUID;

@Controller("/otis")
public class OtisRequestsController {

    private final OtisPlayerRepository repository;

    @Inject
    public OtisRequestsController(OtisPlayerRepository repository) {
        this.repository = repository;
    }

    @Validated
    @Post()
    public HttpResponse<OtisPlayerDTO> add(@Valid OtisPlayerDTO playerDTO) {
        OtisPlayer otisPlayer = OtisPlayer.toEntity(playerDTO);
        OtisPlayer saved = repository.save(otisPlayer);
        return HttpResponse.ok(saved.toDto());
    }

    @Validated
    @Get("/byId/{owner}")
    public HttpResponse<OtisPlayerDTO> getById(@Valid UUID owner) {
        OtisPlayer entity = this.repository.findById(owner).orElse(null);
        if (entity == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(entity.toDto());
    }

    @Validated
    @Get("/byName/{owner}")
    public HttpResponse<OtisPlayerDTO> getByString(@Valid String owner) {
        OtisPlayer entity = this.repository.findById(UUID.fromString(owner)).orElse(null);
        if (entity == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(entity.toDto());
    }

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
    @Get("/all")
    public HttpResponse<Iterable<OtisPlayerDTO>> getAll(Pageable pageable) {
        Page<OtisPlayer> entities = this.repository.findAll(pageable);
        if (entities.isEmpty()) {
            return HttpResponse.ok(List.of());
        }
        return HttpResponse.ok(entities.map(OtisPlayer::toDto));
    }
}
