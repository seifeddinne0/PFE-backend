package com.academique.backend.repository;

import com.academique.backend.entity.ChatMessage;
import com.academique.backend.entity.Classe;
import com.academique.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Direct messages between two users (both directions)
    @Query("SELECT m FROM ChatMessage m WHERE m.classe IS NULL AND " +
           "((m.sender = :u1 AND m.receiver = :u2) OR (m.sender = :u2 AND m.receiver = :u1)) " +
           "ORDER BY m.sentAt ASC")
    List<ChatMessage> findDirectMessages(@Param("u1") User u1, @Param("u2") User u2);

    // All group messages for a class
    List<ChatMessage> findByClasseOrderBySentAtAsc(Classe classe);

    // All conversations (latest message per contact) for a user — direct messages
    @Query("SELECT m FROM ChatMessage m WHERE m.classe IS NULL AND " +
           "(m.sender = :user OR m.receiver = :user) ORDER BY m.sentAt DESC")
    List<ChatMessage> findAllDirectMessagesForUser(@Param("user") User user);

    // Count unread messages for a receiver
    long countByReceiverAndIsReadFalse(User receiver);

       // Distinct users this user sent direct messages to
       List<User> findDistinctReceiverBySenderAndClasseIsNullAndReceiverIsNotNull(User sender);

       // Distinct users who sent direct messages to this user
       List<User> findDistinctSenderByReceiverAndClasseIsNull(User receiver);
}
