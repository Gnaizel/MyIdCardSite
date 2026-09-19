package ru.gnaizel.mapper.guestbook;

import org.springframework.stereotype.Service;
import ru.gnaizel.dto.guestbook.MessageAdminDto;
import ru.gnaizel.dto.guestbook.MessageDto;
import ru.gnaizel.model.guestbook.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GuestbookMapper {
    private static final String ANON = "anon";

    public MessageDto mapToDto(Message message) {
        String nick = message.getNick() == null || message.getNick().isBlank()
                ? ANON : message.getNick();
        return new MessageDto(message.getId(), nick, message.getBody(), ago(message.getCreatedAt()));
    }

    public List<MessageAdminDto> mapToAdminDto(List<Message> messages) {
        return messages.stream()
                .map(message -> new MessageAdminDto(message.getId(),
                        message.getNick(),
                        message.getBody(),
                        message.getIp(),
                        message.getCreatedAt(),
                        message.isHidden()))
                .collect(Collectors.toList());
    }

    /** Та же шкала, что у треков last.fm: m / h / d. */
    private String ago(Instant createdAt) {
        if (createdAt == null) {
            return "";
        }
        long minutes = Duration.between(createdAt, Instant.now()).toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes > 1440) {
            return (minutes / 1440) + "d";
        }
        if (minutes > 60) {
            return (minutes / 60) + "h";
        }
        return minutes + "m";
    }
}
