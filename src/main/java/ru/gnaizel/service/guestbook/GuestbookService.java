package ru.gnaizel.service.guestbook;

import ru.gnaizel.dto.guestbook.MessageAdminDto;
import ru.gnaizel.dto.guestbook.MessageCreateDto;
import ru.gnaizel.dto.guestbook.MessageDto;

import java.util.List;

public interface GuestbookService {
    MessageDto add(MessageCreateDto dto, String ip);

    List<MessageAdminDto> getAll(String adminKey);

    void hide(Long id, String adminKey);

    void restore(Long id, String adminKey);
}
