package it.nukleo.recaptelegrambot.telegram.service;


import it.nukleo.recaptelegrambot.llm.service.LlmService;
import it.nukleo.recaptelegrambot.telegram.dto.response.TelegramVoiceDto;
import it.nukleo.recaptelegrambot.telegram.web.TelegramApiClient;
import it.nukleo.recaptelegrambot.telegram.dto.response.TelegramMessageDto;
import it.nukleo.recaptelegrambot.telegram.dto.response.TelegramUpdateDto;
import it.nukleo.recaptelegrambot.telegram.persistence.entity.TelegramMessageEntity;
import it.nukleo.recaptelegrambot.telegram.persistence.repository.TelegramMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TelegramService {

    private final TelegramMessageRepository telegramMessageRepository;
    private final TelegramApiClient telegramApiClient;
    private final LlmService llmService;
    private final StringRedisTemplate redisTemplate;
    private final TaskScheduler taskScheduler;
    private final Path appDir = new ApplicationHome(getClass()).getDir().toPath();


    public void handleUpdate(TelegramUpdateDto update) throws Exception {
        if(update.getMessage() != null) {
            handleMessage(update.getMessage());
        }
    }

    private void handleMessage(TelegramMessageDto message) throws Exception {
        String chatType = message.getChat().getType();
        String text = message.getText();

        if (!"group".equals(chatType) && !"supergroup".equals(chatType)) return;
        if (Boolean.TRUE.equals(message.getFrom().getIsBot())) return;

        if (message.getVoice() != null) {
            handleTranscription(message);
            return;
        }

        if (text == null || text.isBlank()) {
            return;
        }
        text = text.trim();

        /// COMANDI DA QUA
        if (text.toLowerCase().startsWith("/recap")){
            handleRecap(message);

            taskScheduler.schedule(
                    () -> telegramApiClient.deleteMessage(message.getChat().getId(), message.getMessageId()),
                    Instant.now().plusSeconds(5)
            );

            return;
        }

        /// SE NESSUN COMANDO, SALVA MESSAGGIO
        saveMessage(message);
    }

    private void handleTranscription(TelegramMessageDto message) throws Exception {
        Long chatId = message.getChat().getId();
        Long messageId = message.getMessageId();

        TelegramMessageDto sentMex = telegramApiClient.replyToMessage(chatId, "✍ 🤔👂 ...", messageId);

        String filePath = telegramApiClient.getFilePath(message.getVoice().getFileId());
        byte[] audioBytes = telegramApiClient.downloadFile(filePath);
        String extension = Path.of(filePath).getFileName().toString().replaceFirst("^[^.]+", "");
        Path savedFile = appDir.resolve("voice-" + messageId + chatId + extension);
        Files.write(savedFile, audioBytes);

        llmService.generateTranscription(savedFile)
                .thenAccept(result -> {
                    telegramApiClient.editMessage(chatId, sentMex.getMessageId(), result);
                    message.setText(result);
                    saveMessage(message);
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });


    }

    private void handleRecap(TelegramMessageDto message) {
        Long chatId = message.getChat().getId();
        Long messageId = message.getMessageId();
        Long senderId = message.getFrom().getId();

        String key = "recap:cooldown:" + chatId + ":" + senderId;
        if(!redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10))){
            telegramApiClient.sendReaction(chatId, messageId, "👎");
            return;
        }  //ANTIFLOOD RECAP

        String[] parts = message.getText().trim().split("\\s+");

        if (parts.length > 2) { /// MASSIMO UN ARG AMMESSO
            telegramApiClient.sendReaction(chatId, messageId, "👎");
            return;
        }

        telegramApiClient.sendReaction(chatId, messageId, "👀");

        /// SE SENZA ARG, default 24h no keyword
        if (parts.length == 1) {
            this.sendLast24hRecap(chatId, senderId);
            return;
        }

        String arg = parts[1].trim();

        /// SE ARG E' UN NUMERO, RECAP DEGLI ULTIMI n MESSAGGI
        if (arg.matches("\\d+")) {
            int limit =  Integer.parseInt(arg);
            if(limit<1 || limit>10000 ) {
                telegramApiClient.sendReaction(chatId, messageId, "👎");
                return;
            }

            this.sendLastMessagesRecap(chatId, senderId, limit);
            return;
        }

        /// SE MATCHA NELLA FORMA DI TEMPO 1m 1h 1d DOVE m=minuto h=ora d=giorno
        Matcher timeMatcher = Pattern.compile("^(\\d+)([mhd])$").matcher(arg);
        if (timeMatcher.matches()) {
            this.sendTimeRangeRecap(chatId, senderId, Long.parseLong(timeMatcher.group(1)), timeMatcher.group(2));
            return;
        }

        /// A MENO CHE NON SIA UNA FORMA DI TEMPO ERRATA COME 1s (recap secondi non ammesso), USA L'ARG COME KEYWORD
        if (!arg.matches("^\\d+[a-zA-Z]+$")) {
            this.sendKeywordRecap(chatId, senderId, arg);
            return;
        }

        telegramApiClient.sendReaction(chatId, messageId, "👎");

    }

    private void sendLast24hRecap(Long chatId, Long senderId){
        String cacheKey = "recap:24h:" + chatId;

        String cachedRecap = redisTemplate.opsForValue().get(cacheKey);
        if (cachedRecap != null) {
            telegramApiClient.sendMessage(senderId, "<b>Recap delle ultime 24 ore</b>\n" + cachedRecap);
            return;
        }

        Duration duration = Duration.ofHours(24);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minus(duration);

        List<TelegramMessageEntity> messages = telegramMessageRepository.findMessagesByDuration(chatId, from, to);

        llmService.generateRecap(messages, null)
                .thenAccept(result -> {
                            telegramApiClient.sendMessage(senderId, "<b>Recap delle ultime 24 ore</b>\n" + result);
                            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(2));
                }).exceptionally(ex -> {
                    telegramApiClient.sendMessage(senderId, "Errore durante la generazione del recap.");
                    System.out.println(ex.getMessage());
                    return null;
                });

    }

    private void sendLastMessagesRecap(Long chatId, Long senderId, int limit) {
        String cacheKey = "recap:"+limit+"-mex:" + chatId;

        String cachedRecap = redisTemplate.opsForValue().get(cacheKey);
        if (cachedRecap != null) {
            telegramApiClient.sendMessage(senderId, "<b>Recap degli ultimi " + limit + " messaggi</b>\n" + cachedRecap);
            return;
        }

        List<TelegramMessageEntity> messages = telegramMessageRepository.findMessagesByLimit(chatId, PageRequest.of(0, limit));


        llmService.generateRecap(messages, null)
                .thenAccept(result -> {
                            telegramApiClient.sendMessage(senderId, "<b>Recap degli ultimi " + limit + " messaggi</b>\n" + result);
                            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(2));
                }).exceptionally(ex -> {
                    telegramApiClient.sendMessage(senderId, "Errore durante la generazione del recap.");
                    System.out.println(ex.getMessage());
                    return null;
                });
    }

    private void sendKeywordRecap(Long chatId, Long senderId, String keyword) {
        String cacheKey = "recap:"+keyword+"-keyword:" + chatId;

        String cachedRecap = redisTemplate.opsForValue().get(cacheKey);
        if (cachedRecap != null) {
            telegramApiClient.sendMessage(senderId, "<b>Recap delle ultime 24 ore per: " + keyword + "</b>\n" + cachedRecap);
            return;
        }

        Duration duration = Duration.ofHours(24);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minus(duration);

        List<TelegramMessageEntity> messages = telegramMessageRepository.findMessagesByDuration(chatId, from, to);


        llmService.generateRecap(messages, keyword)
                .thenAccept(result ->{
                        telegramApiClient.sendMessage(senderId, "<b>Recap delle ultime 24 ore per: " + keyword + "</b>\n" + result);
                    redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(2));
                }).exceptionally(ex -> {
                    telegramApiClient.sendMessage(senderId, "Errore durante la generazione del recap.");
                    System.out.println(ex.getMessage());
                    return null;
                });
    }

    private void sendTimeRangeRecap(Long chatId, Long senderId, long amount, String unit) {
        String cacheKey = "recap:"+amount+unit+":" + chatId;

        String cachedRecap = redisTemplate.opsForValue().get(cacheKey);
        if (cachedRecap != null) {
            telegramApiClient.sendMessage(senderId, "<b>Recap degli ultimi " + amount + unit + "</b>\n" + cachedRecap);
            return;
        }

        Duration duration = switch (unit) {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Regex fallito unità non supportata: " + unit);
        };

        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minus(duration);

        List<TelegramMessageEntity> messages = telegramMessageRepository.findMessagesByDuration(chatId, from, to);

        llmService.generateRecap(messages, null)
                .thenAccept(result -> {
                            telegramApiClient.sendMessage(senderId, "<b>Recap degli ultimi " + amount + unit + "</b>\n" + result);
                            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(2));
                        }
                )
                .exceptionally(ex -> {
                    telegramApiClient.sendMessage(senderId, "Errore durante la generazione del recap.");
                    System.out.println(ex.getMessage());
                    return null;
                });
    }


    private void saveMessage(TelegramMessageDto dto) {
        TelegramMessageEntity savedMessage = new TelegramMessageEntity();
        savedMessage.setChatId(dto.getChat().getId());
        savedMessage.setText(dto.getText());
        savedMessage.setSentAt(
                Instant.ofEpochSecond(dto.getDate())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
        );
        savedMessage.setUserFirstName(dto.getFrom().getFirstName());
        savedMessage.setMessageId(dto.getMessageId());
        telegramMessageRepository.save(savedMessage);
    }

}
