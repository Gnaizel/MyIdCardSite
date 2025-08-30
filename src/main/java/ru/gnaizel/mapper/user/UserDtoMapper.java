package ru.gnaizel.mapper.user;

import org.springframework.stereotype.Component;
import ru.gnaizel.dto.user.UserCreateDto;
import ru.gnaizel.model.user.User;

@Component
public class UserDtoMapper {
    public User UserCreateDtoToUser(UserCreateDto userCreateDto) {
        return User.builder()
                .ip(userCreateDto.getIp())
                .country(userCreateDto.getCountry())
                .continent(userCreateDto.getContinent())
                .city(userCreateDto.getCity())
                .build();
    }
}
