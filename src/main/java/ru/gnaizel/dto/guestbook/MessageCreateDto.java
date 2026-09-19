package ru.gnaizel.dto.guestbook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageCreateDto {
    private String nick;
    private String body;

    /* Ловушка: поле спрятано от людей стилями, но бот его видит в разметке и
       заполняет. Заполнено — молча выбрасываем, не сообщая боту об отказе. */
    private String website;
}
