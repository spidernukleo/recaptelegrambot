package it.nukleo.recaptelegrambot.telegram.persistence.repository;

import it.nukleo.recaptelegrambot.telegram.persistence.entity.TelegramMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface TelegramMessageRepository extends JpaRepository<TelegramMessageEntity, Long> {

    @Query("""
        select m
        from TelegramMessageEntity m
        where m.chatId = :chatId and m.sentAt between :from and :to
        order by m.sentAt asc
    """)
    List<TelegramMessageEntity> findMessagesByDuration(
            @Param("chatId") Long chatId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
        select m
        from TelegramMessageEntity m
        where m.chatId = :chatId
        order by m.sentAt desc
    """)
    List<TelegramMessageEntity> findMessagesByLimit(
            @Param("chatId") Long chatId,
            Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("""
        delete
        from TelegramMessageEntity m
        where m.sentAt < :cutoff
    """)
    int deleteMessagesOlderThan(
            @Param("cutoff") LocalDateTime cutoff
    );
}
