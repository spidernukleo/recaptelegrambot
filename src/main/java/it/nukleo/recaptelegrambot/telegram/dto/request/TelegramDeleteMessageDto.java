package it.nukleo.recaptelegrambot.telegram.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramDeleteMessageDto {

    @JsonProperty("chat_id")
    private Long chatId;

    @JsonProperty("message_id")
    private Long messageId;
}
