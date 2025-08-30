package ru.gnaizel.model.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class User {
    String ip;
    String country;
    String continent;
    String city;
}
