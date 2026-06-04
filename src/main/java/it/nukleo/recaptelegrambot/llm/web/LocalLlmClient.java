package it.nukleo.recaptelegrambot.llm.web;

import it.nukleo.recaptelegrambot.config.LocalLlmProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


@Service("localLlmClient")
public class LocalLlmClient implements LlmClient {

    private final LocalLlmProperties properties;

    public LocalLlmClient(LocalLlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public CompletableFuture<String> generateTextFromPrompt(String prompt) {
        return null;
    }

    @Override
    @Async("voiceExecutor")
    public CompletableFuture<String> transcribeAudio(Path audioFile) throws Exception {
        return CompletableFuture.completedFuture(doTranscription(audioFile));
    }


    private String doTranscription(Path audioFile) throws Exception {
        String baseName = audioFile.getFileName().toString().replaceFirst("[.][^.]+$", "");

        ProcessBuilder pb = new ProcessBuilder(
                properties.getCliPath(),
                "-m", properties.getModelPath(),
                "-f", audioFile.toAbsolutePath().toString(),
                "-l", properties.getLanguage(),
                "--output-txt",
                "-of", audioFile.getParent().resolve(baseName).toAbsolutePath().toString()
        );

        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            Files.deleteIfExists(audioFile);
            throw new IllegalStateException("whisper-cli failed with exit code " + exitCode);
        }

        Path txtFile = audioFile.getParent().resolve(baseName + ".txt");

        if (!Files.exists(txtFile)) {
            Files.deleteIfExists(audioFile);
            throw new IllegalStateException("no txt output fount");
        }

        String transcription = Files.readString(txtFile).trim();

        Files.deleteIfExists(txtFile);
        Files.deleteIfExists(audioFile);

        return transcription;
    }
}
