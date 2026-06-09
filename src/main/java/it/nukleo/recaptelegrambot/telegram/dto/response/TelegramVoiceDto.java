package it.nukleo.recaptelegrambot.telegram.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class TelegramVoiceDto {

    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("mime_type")
    private String mimeType;
}
