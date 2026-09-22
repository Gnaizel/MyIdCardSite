package ru.gnaizel.repository.games;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.gnaizel.model.games.TrackedGameDay;

import java.time.LocalDate;

@Repository
public interface TrackedGameDayRepository extends JpaRepository<TrackedGameDay, TrackedGameDay.Key> {
    @Query("select coalesce(sum(d.secondsPlayed), 0) from TrackedGameDay d"
            + " where d.name = :name and d.day >= :from")
    long secondsSince(@Param("name") String name, @Param("from") LocalDate from);
}
