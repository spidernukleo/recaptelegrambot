package it.nukleo.recaptelegrambot.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@ConfigurationProperties(prefix="crypto")
public class CryptoProperties {

    @NotBlank
    private String secretKey;
}
