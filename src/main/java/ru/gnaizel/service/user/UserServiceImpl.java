package ru.gnaizel.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.user.UserCreateDto;
import ru.gnaizel.exception.IpNotRecognized;
import ru.gnaizel.mapper.user.UserDtoMapper;
import ru.gnaizel.repository.user.UserRepository;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final UserRepository userRepository;
    private final UserDtoMapper userDtoMapper;

    @Override
    public void addVisit(String ip) {
        String url = "http://ipwho.is/" + ip;
        UserCreateDto user = restTemplate.getForObject(url, UserCreateDto.class);
        if (user == null) {
            throw new IpNotRecognized("ip not recognized");
        }
        userRepository.save(userDtoMapper.UserCreateDtoToUser(user));
    }

    @Override
    public Long getCountOfVisit() {
        return userRepository.count();
    }
}
