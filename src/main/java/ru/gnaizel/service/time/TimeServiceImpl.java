package ru.gnaizel.service.time;

import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TimeServiceImpl implements TimeService {

    @Override
    public String getCurrentTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        return  now.format(formatter);
    }
}
