package ru.gnaizel.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
