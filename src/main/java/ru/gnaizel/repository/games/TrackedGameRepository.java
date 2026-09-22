package ru.gnaizel.repository.games;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.gnaizel.model.games.TrackedGame;

@Repository
public interface TrackedGameRepository extends JpaRepository<TrackedGame, String> {
}
