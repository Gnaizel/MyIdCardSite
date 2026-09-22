package ru.gnaizel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/* Планировщик нужен счётчику часов для игр не из Steam: время копится,
   пока я играю, а не пока кто-то смотрит страницу. */
@EnableScheduling
@SpringBootApplication
public class MyCardApp {
    public static void main(String[] args) {
        SpringApplication.run(MyCardApp.class, args);
    }
}