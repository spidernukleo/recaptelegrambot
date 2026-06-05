package it.nukleo.recaptelegrambot.telegram.service;

import it.nukleo.recaptelegrambot.telegram.persistence.repository.TelegramMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TelegramMessageCleanupJob {

    private final TelegramMessageRepository telegramMessageRepository;

    @Scheduled(cron = "0 00 4 * * *")
    public void cleanupOldMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(14);
        int deleted = telegramMessageRepository.deleteMessagesOlderThan(cutoff);
    }
}
