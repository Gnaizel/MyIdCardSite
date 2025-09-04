package ru.gnaizel.service.user;

public interface UserService {
    void addVisit(String ip);

    Long getCountOfVisit();
}
