package it.nukleo.recaptelegrambot.llm.service;


import it.nukleo.recaptelegrambot.config.LocalLlmProperties;
import it.nukleo.recaptelegrambot.llm.web.LlmClient;
import it.nukleo.recaptelegrambot.telegram.persistence.entity.TelegramMessageEntity;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class LlmService {

    private final LlmClient recapLlmClient;
    private final LlmClient transcriptionLlmClient;
    private final ResourceLoader resourceLoader;
    private final LocalLlmProperties properties;
    private String recapBasePrompt;
    private String recapKeywordPrompt;

    public LlmService(
            @Qualifier("geminiLlmClient") LlmClient recapLlmClient,
            @Qualifier("localLlmClient") LlmClient transcriptionLlmClient,
            ResourceLoader resourceLoader,
            LocalLlmProperties properties
    ) {
        this.recapLlmClient = recapLlmClient;
        this.transcriptionLlmClient = transcriptionLlmClient;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        recapBasePrompt = loadPrompt("classpath:prompts/recapBasePrompt.txt");
        recapKeywordPrompt = loadPrompt("classpath:prompts/recapKeywordPrompt.txt");

    }

    public CompletableFuture<String> generateTranscription(Path audioFile) throws Exception {
        Path wavFile = convertToWav(audioFile);
        return transcriptionLlmClient.transcribeAudio(wavFile);
    }

    public CompletableFuture<String> generateRecap(List<TelegramMessageEntity> messages, String keyword) {
        String prompt = buildPrompt(messages, keyword);
        return recapLlmClient.generateTextFromPrompt(prompt);
    }

    private String buildPrompt(List<TelegramMessageEntity> messages, String keyword) {
        String messagesText = messages.stream()
                .map(this::formatMessageForPrompt)
                .collect(Collectors.joining("\n"));

        String keywordPrompt = (keyword == null) ? "" : recapKeywordPrompt.replace("${KEYWORD}", keyword.trim());

        String chatId = messages.isEmpty() ? "null" : messages.getFirst().getChatId().toString().substring(4);

        return recapBasePrompt
                .replace("${KEYWORD}", keywordPrompt)
                .replace("${CHAT_ID}", chatId)
                .replace("${MESSAGES}", messagesText);
    }




    private Path convertToWav(Path audioFile) throws Exception {
        String baseName = audioFile.getFileName().toString().replaceFirst("[.][^.]+$", "");
        Path wavFile = audioFile.getParent().resolve(baseName + ".wav");

        ProcessBuilder pb = new ProcessBuilder(
                properties.getFfmpegPath(),
                "-y",
                "-i", audioFile.toAbsolutePath().toString(),
                "-ar", "16000",
                "-ac", "1",
                "-c:a", "pcm_s16le",
                wavFile.toAbsolutePath().toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        Files.deleteIfExists(audioFile);

        if (exitCode != 0) throw new IllegalStateException("ffmpeg failed with exit code " + exitCode);

        return wavFile;

    }


    private String formatMessageForPrompt(TelegramMessageEntity message) {
        return "[MID_%d][%s] %s: %s".formatted(
                message.getMessageId(),
                message.getSentAt(),
                message.getUserFirstName(),
                message.getText()
        );
    }

    private String loadPrompt(String path) {
        try {
            return StreamUtils.copyToString(
                    resourceLoader.getResource(path).getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException("Errore durante la lettura del prompt file: " + path, e);
        }
    }
}