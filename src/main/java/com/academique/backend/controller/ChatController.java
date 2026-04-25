package com.academique.backend.controller;

import com.academique.backend.dto.request.ChatMessageRequest;
import com.academique.backend.dto.response.ChatMessageResponse;
import com.academique.backend.dto.response.ContactResponse;
import com.academique.backend.entity.User;
import com.academique.backend.repository.UserRepository;
import com.academique.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    // Get current user info (id, nom, prenom, photo)
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "nom", user.getNom() != null ? user.getNom() : "",
                "prenom", user.getPrenom() != null ? user.getPrenom() : "",
                "email", user.getEmail(),
                "photo", user.getPhoto() != null ? user.getPhoto() : ""
        ));
    }


    // Send a message (direct or group)
    @PostMapping("/send")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            Authentication auth,
            @RequestBody ChatMessageRequest req) {
        return ResponseEntity.ok(chatService.sendMessage(auth.getName(), req));
    }

    // Get direct messages between current user and another user
    @GetMapping("/direct/{userId}")
    public ResponseEntity<List<ChatMessageResponse>> getDirectMessages(
            Authentication auth,
            @PathVariable Long userId) {
        return ResponseEntity.ok(chatService.getDirectMessages(auth.getName(), userId));
    }

    // Get group messages for a class
    @GetMapping("/group/{classeId}")
    public ResponseEntity<List<ChatMessageResponse>> getGroupMessages(
            @PathVariable Long classeId) {
        return ResponseEntity.ok(chatService.getGroupMessages(classeId));
    }

    // Get all conversations (chat list) for current user
    @GetMapping("/conversations")
    public ResponseEntity<List<ContactResponse>> getConversations(Authentication auth) {
        return ResponseEntity.ok(chatService.getConversations(auth.getName()));
    }

    // Get all enseignants (for étudiant contact list)
    @GetMapping("/enseignants")
    public ResponseEntity<List<ContactResponse>> getAllEnseignants(Authentication auth) {
        return ResponseEntity.ok(chatService.getAllEnseignants(auth.getName()));
    }

    // Get all étudiants (for enseignant chat list)
    @GetMapping("/etudiants")
    public ResponseEntity<List<ContactResponse>> getAllEtudiants(Authentication auth) {
        return ResponseEntity.ok(chatService.getAllEtudiants(auth.getName()));
    }

    // Get all classes (for enseignant groups)
    @GetMapping("/classes")
    public ResponseEntity<List<ContactResponse>> getAllClasses(Authentication auth) {
        return ResponseEntity.ok(chatService.getAllClasses(auth.getName()));
    }

    // Get étudiant's own class (for étudiant groups)
    @GetMapping("/my-classe")
    public ResponseEntity<List<ContactResponse>> getMyClasse(Authentication auth) {
        return ResponseEntity.ok(chatService.getMyClasse(auth.getName()));
    }

    // Mark direct messages as read
    @PostMapping("/read/{userId}")
    public ResponseEntity<Void> markAsRead(Authentication auth, @PathVariable Long userId) {
        chatService.markAsRead(auth.getName(), userId);
        return ResponseEntity.ok().build();
    }

    // Delete a message
    @DeleteMapping("/message/{msgId}")
    public ResponseEntity<Void> deleteMessage(Authentication auth, @PathVariable Long msgId) {
        chatService.deleteMessage(msgId, auth.getName());
        return ResponseEntity.ok().build();
    }

    // Edit a message
    @PutMapping("/message/{msgId}")
    public ResponseEntity<ChatMessageResponse> editMessage(
            Authentication auth,
            @PathVariable Long msgId,
            @RequestBody ChatMessageRequest req) {
        return ResponseEntity.ok(chatService.editMessage(msgId, auth.getName(), req.getContent()));
    }

    // Delete entire conversation for the user
    @DeleteMapping("/conversation/{userId}")
    public ResponseEntity<Void> deleteConversation(Authentication auth, @PathVariable Long userId) {
        chatService.deleteConversation(auth.getName(), userId);
        return ResponseEntity.ok().build();
    }
}
