package ru.gnaizel.service.time;

import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Service
public class DynamicValueServiceImpl implements DynamicValueService {

    @Override
    public String getCurrentTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        return  now.format(formatter);
    }

    @Override
    public String getAlive() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime birth = LocalDateTime.of(2007, 9, 10, 13, 0);

        int years = Period.between(birth.toLocalDate(), now.toLocalDate()).getYears();
        LocalDateTime afterYears = birth.plusYears(years);

        Duration duration = Duration.between(afterYears, now);

        long days = duration.toDays();
        duration = duration.minusDays(days);

        long hours = duration.toHours();
        duration = duration.minusHours(hours);

        long minutes = duration.toMinutes();
        duration = duration.minusMinutes(minutes);

        long seconds = duration.getSeconds();

        return years + "y " + days + "d " + hours + "h " + minutes + "m " + seconds + "s";
    }
}
