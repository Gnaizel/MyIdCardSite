package ru.gnaizel.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.gnaizel.model.user.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
