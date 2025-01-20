package net.theevilreaper.otis.database.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.PageableRepository;
import net.theevilreaper.otis.database.entity.OtisPlayer;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtisPlayerRepository extends PageableRepository<OtisPlayer, UUID> {

    @Query("SELECT player FROM OtisPlayer player WHERE player.playerName = :name")
    Optional<OtisPlayer> findByName(@Param("name") String name);
}
