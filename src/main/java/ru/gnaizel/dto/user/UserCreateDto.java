package ru.gnaizel.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserCreateDto {
    @NotBlank
    String ip;
    @NotBlank
    String country;
    @NotBlank
    String continent;
    @NotBlank
    String city;
}
