package it.nukleo.recaptelegrambot.telegram.persistence.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "telegram_message",
        indexes = {
                @Index(name = "idx_telegram_message_chat_id", columnList = "chat_id"),
                @Index(name = "idx_telegram_message_sent_at", columnList = "sent_at"),
                @Index(name = "idx_telegram_message_message_id", columnList = "message_id")
        }
)
@Entity
public class TelegramMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="chat_id", nullable = false)
    private Long chatId;

    @Column(name = "user_first_name")
    private String userFirstName;

    @Column(name = "text", columnDefinition = "TEXT")
    @Convert(converter = TextEncryptConverter.class)
    private String text;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "message_id", nullable = false)
    private Long messageId;
}
