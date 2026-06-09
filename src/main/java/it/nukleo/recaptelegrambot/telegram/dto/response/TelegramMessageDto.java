package it.nukleo.recaptelegrambot.telegram.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class TelegramMessageDto {
    @JsonProperty("message_id")
    private Long messageId;

    private TelegramUserDto from;

    private TelegramChatDto chat;

    private String text;

    private Long date;

    @JsonProperty("reply_to_message")
    private TelegramMessageDto replyToMessage;

    private TelegramVoiceDto voice;
}
