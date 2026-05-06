package com.academique.backend.service;

import com.academique.backend.dto.request.ChatMessageRequest;
import com.academique.backend.dto.response.ChatMessageResponse;
import com.academique.backend.dto.response.ContactResponse;
import com.academique.backend.entity.*;
import com.academique.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final EnseignantRepository enseignantRepository;
    private final EtudiantRepository etudiantRepository;
    private final ClasseRepository classeRepository;
    private final SeanceRepository seanceRepository;
    private final NotificationService notificationService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ======== SEND MESSAGE ========

    @Transactional
    public ChatMessageResponse sendMessage(String senderEmail, ChatMessageRequest req) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                .sender(sender)
                .content(req.getContent());

        if (req.getClasseId() != null) {
            // Group message
            Classe classe = classeRepository.findById(req.getClasseId())
                    .orElseThrow(() -> new RuntimeException("Classe non trouvée"));
            builder.classe(classe);
        } else if (req.getReceiverId() != null) {
            // Direct message
            User receiver = userRepository.findById(req.getReceiverId())
                    .orElseThrow(() -> new RuntimeException("Destinataire non trouvé"));
            builder.receiver(receiver);
        } else {
            throw new RuntimeException("Destinataire ou classe requis");
        }

        ChatMessage saved = chatMessageRepository.save(builder.build());

        // Notify Receiver(s)
        if (saved.getReceiver() != null) {
            // Direct Message
            notificationService.createNotification(
                saved.getReceiver().getId(),
                getRoleName(saved.getReceiver()),
                "Nouveau message de " + sender.getNom() + " " + sender.getPrenom(),
                saved.getContent(),
                "CHAT",
                saved.getReceiver().getEmail()
            );
        } else if (saved.getClasse() != null) {
            // Group Message
            List<Etudiant> students = etudiantRepository.findByClasseId(saved.getClasse().getId());
            for (Etudiant e : students) {
                if (!e.getEmail().equals(senderEmail)) {
                    notificationService.createNotification(
                        e.getUser().getId(),
                        "ETUDIANT",
                        "Nouveau message dans le groupe " + saved.getClasse().getCode(),
                        sender.getNom() + " " + sender.getPrenom() + " : " + saved.getContent(),
                        "CHAT",
                        null // Only in-app for group messages to avoid spam
                    );
                }
            }
        }

        return toResponse(saved);
    }

    // ======== GET DIRECT MESSAGES ========

    public List<ChatMessageResponse> getDirectMessages(String currentEmail, Long otherUserId) {
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        return chatMessageRepository.findDirectMessages(currentUser, otherUser)
                .stream()
                .filter(m -> !isMessageDeletedForUser(m, currentUser.getId()))
                .map(this::toResponse).collect(Collectors.toList());
    }

    // ======== GET GROUP MESSAGES ========

    public List<ChatMessageResponse> getGroupMessages(Long classeId) {
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new RuntimeException("Classe non trouvée"));
        return chatMessageRepository.findByClasseOrderBySentAtAsc(classe)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ======== GET CONVERSATIONS (for chat list) ========

    public List<ContactResponse> getConversations(String currentEmail) {
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // Use the repository to find all messages involving the user
        List<ChatMessage> allMessages = chatMessageRepository.findAllDirectMessagesForUser(currentUser)
                .stream()
                .filter(m -> !isMessageDeletedForUser(m, currentUser.getId()))
                .collect(Collectors.toList());

        // Map to find unique users we have talked to
        Map<Long, User> partners = new HashMap<>();
        for (ChatMessage m : allMessages) {
            User partner = m.getSender().getId().equals(currentUser.getId()) ? m.getReceiver() : m.getSender();
            if (partner != null && !partner.getId().equals(currentUser.getId())) {
                partners.putIfAbsent(partner.getId(), partner);
            }
        }

        List<ContactResponse> result = new ArrayList<>();
        for (User partner : partners.values()) {
            // Find last message with this partner
            ChatMessage lastMsg = allMessages.stream()
                    .filter(m -> (m.getSender().getId().equals(partner.getId()) && m.getReceiver() != null && m.getReceiver().getId().equals(currentUser.getId()))
                            || (m.getSender().getId().equals(currentUser.getId()) && m.getReceiver() != null && m.getReceiver().getId().equals(partner.getId())))
                    .max(Comparator.comparing(ChatMessage::getSentAt))
                    .orElse(null);

            long unread = allMessages.stream()
                    .filter(m -> m.getSender().getId().equals(partner.getId())
                            && m.getReceiver() != null && m.getReceiver().getId().equals(currentUser.getId())
                            && !m.isRead())
                    .count();

            result.add(ContactResponse.builder()
                    .userId(partner.getId())
                    .nom(partner.getNom())
                    .prenom(partner.getPrenom())
                    .email(partner.getEmail())
                    .photo(partner.getPhoto())
                    .role(getRoleName(partner))
                    .lastMessage(lastMsg != null ? lastMsg.getContent() : "")
                    .lastMessageTime(lastMsg != null ? lastMsg.getSentAt().format(TIME_FMT) : "")
                    .sentAtRaw(lastMsg != null ? lastMsg.getSentAt() : null) // added field for sorting
                    .unreadCount(unread)
                    .build());
        }

        // Sort by raw timestamp desc
        result.sort((a, b) -> {
            if (a.getSentAtRaw() == null) return 1;
            if (b.getSentAtRaw() == null) return -1;
            return b.getSentAtRaw().compareTo(a.getSentAtRaw());
        });
        return result;
    }

    // ======== GET ALL ENSEIGNANTS (for étudiant contacts) ========

    public List<ContactResponse> getAllEnseignants(String currentEmail) {
        List<Enseignant> enseignants = enseignantRepository.findAll();
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        List<ChatMessage> allMessages = chatMessageRepository.findAllDirectMessagesForUser(currentUser)
                .stream()
                .filter(m -> !isMessageDeletedForUser(m, currentUser.getId()))
                .collect(Collectors.toList());

        List<ContactResponse> result = enseignants.stream().map(e -> {
            if (e.getUser() == null) return null;
            User u = e.getUser();
            ChatMessage lastMsg = allMessages.stream()
                    .filter(m -> m.getClasse() == null &&
                            ((m.getSender().getId().equals(currentUser.getId()) && m.getReceiver() != null && m.getReceiver().getId().equals(u.getId()))
                                    || (m.getSender().getId().equals(u.getId()) && m.getReceiver() != null && m.getReceiver().getId().equals(currentUser.getId()))))
                    .max(Comparator.comparing(ChatMessage::getSentAt))
                    .orElse(null);
            return ContactResponse.builder()
                    .userId(u.getId())
                    .entityId(e.getId())
                    .nom(e.getNom())
                    .prenom(e.getPrenom())
                    .email(e.getEmail())
                    .photo(u.getPhoto())
                    .role("ENSEIGNANT")
                    .lastMessage(lastMsg != null ? lastMsg.getContent() : "")
                    .lastMessageTime(lastMsg != null ? lastMsg.getSentAt().format(TIME_FMT) : "")
                    .build();
        }).filter(Objects::nonNull).collect(Collectors.toList());

        result.sort((a, b) -> b.getLastMessageTime().compareTo(a.getLastMessageTime()));
        return result;
    }

    // ======== GET ALL CLASSES (for enseignant groups) ========

    public List<ContactResponse> getAllClasses(String currentEmail) {
        Enseignant enseignant = enseignantRepository.findByEmail(currentEmail).orElse(null);
        if (enseignant == null) return List.of();

        Map<Long, Classe> classesById = new LinkedHashMap<>();
        for (Seance seance : seanceRepository.findByEnseignantId(enseignant.getId())) {
            Classe classe = seance.getClasse();
            if (classe != null) classesById.putIfAbsent(classe.getId(), classe);
        }

        return classesById.values().stream().map(c -> {
            List<ChatMessage> msgs = chatMessageRepository.findByClasseOrderBySentAtAsc(c);
            ChatMessage lastMsg = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);
            return ContactResponse.builder()
                    .classeId(c.getId())
                    .classeCode(c.getCode())
                    .classeNom(c.getNom())
                    .nom(c.getNom())
                    .memberCount(c.getEtudiants() != null ? c.getEtudiants().size() : 0)
                    .lastMessage(lastMsg != null ? lastMsg.getContent() : "")
                    .lastMessageTime(lastMsg != null ? lastMsg.getSentAt().format(TIME_FMT) : "")
                    .build();
        }).collect(Collectors.toList());
    }

    // ======== GET ETUDIANT'S CLASS (for étudiant groups) ========

    public List<ContactResponse> getMyClasse(String etudiantEmail) {
        Etudiant etudiant = etudiantRepository.findByEmail(etudiantEmail).orElse(null);
        if (etudiant == null || etudiant.getClasse() == null) return List.of();

        Classe c = etudiant.getClasse();
        List<ChatMessage> msgs = chatMessageRepository.findByClasseOrderBySentAtAsc(c);
        ChatMessage lastMsg = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);

        return List.of(ContactResponse.builder()
                .classeId(c.getId())
                .classeCode(c.getCode())
                .classeNom(c.getNom())
                .nom(c.getNom())
                .memberCount(c.getEtudiants() != null ? c.getEtudiants().size() : 0)
                .lastMessage(lastMsg != null ? lastMsg.getContent() : "")
                .lastMessageTime(lastMsg != null ? lastMsg.getSentAt().format(TIME_FMT) : "")
                .build());
    }

    // ======== ALL ETUDIANTS FOR ENSEIGNANT CHAT ========

    public List<ContactResponse> getAllEtudiants(String currentEmail) {
        List<Etudiant> etudiants = etudiantRepository.findAll();
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        List<ChatMessage> allMessages = chatMessageRepository.findAllDirectMessagesForUser(currentUser)
                .stream()
                .filter(m -> !isMessageDeletedForUser(m, currentUser.getId()))
                .collect(Collectors.toList());

        List<ContactResponse> result = etudiants.stream().map(e -> {
            if (e.getUser() == null) return null;
            User u = e.getUser();
            ChatMessage lastMsg = allMessages.stream()
                    .filter(m -> m.getClasse() == null &&
                            ((m.getSender().getId().equals(currentUser.getId()) && m.getReceiver() != null && m.getReceiver().getId().equals(u.getId()))
                                    || (m.getSender().getId().equals(u.getId()) && m.getReceiver() != null && m.getReceiver().getId().equals(currentUser.getId()))))
                    .max(Comparator.comparing(ChatMessage::getSentAt))
                    .orElse(null);
            long unread = allMessages.stream()
                    .filter(m -> m.getClasse() == null && m.getSender().getId().equals(u.getId())
                            && m.getReceiver() != null && m.getReceiver().getId().equals(currentUser.getId())
                            && !m.isRead())
                    .count();
            return ContactResponse.builder()
                    .userId(u.getId())
                    .entityId(e.getId())
                    .nom(e.getNom())
                    .prenom(e.getPrenom())
                    .email(e.getEmail())
                    .photo(u.getPhoto())
                    .role("ETUDIANT")
                    .lastMessage(lastMsg != null ? lastMsg.getContent() : "")
                    .lastMessageTime(lastMsg != null ? lastMsg.getSentAt().format(TIME_FMT) : "")
                    .unreadCount(unread)
                    .build();
        }).filter(Objects::nonNull).collect(Collectors.toList());

        result.sort((a, b) -> b.getLastMessageTime().compareTo(a.getLastMessageTime()));
        return result;
    }

    // ======== MARK AS READ ========

    @Transactional
    public void markAsRead(String currentEmail, Long otherUserId) {
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        List<ChatMessage> unread = chatMessageRepository.findDirectMessages(currentUser, otherUser)
                .stream()
                .filter(m -> m.getReceiver() != null
                        && m.getReceiver().getId().equals(currentUser.getId())
                        && !m.isRead())
                .collect(Collectors.toList());
        unread.forEach(m -> m.setRead(true));
        chatMessageRepository.saveAll(unread);
    }

    // ======== DELETE MESSAGE ========

    @Transactional
    public void deleteMessage(Long msgId, String userEmail) {
        ChatMessage msg = chatMessageRepository.findById(msgId)
                .orElseThrow(() -> new RuntimeException("Message non trouvé"));
        if (!msg.getSender().getEmail().equals(userEmail)) {
            throw new RuntimeException("Non autorisé à supprimer ce message");
        }
        msg.setDeleted(true);
        chatMessageRepository.save(msg);
    }

    // ======== EDIT MESSAGE ========

    @Transactional
    public ChatMessageResponse editMessage(Long msgId, String userEmail, String newContent) {
        ChatMessage msg = chatMessageRepository.findById(msgId)
                .orElseThrow(() -> new RuntimeException("Message non trouvé"));
        if (!msg.getSender().getEmail().equals(userEmail)) {
            throw new RuntimeException("Non autorisé à modifier ce message");
        }
        if (msg.isDeleted()) {
            throw new RuntimeException("Impossible de modifier un message supprimé");
        }
        msg.setContent(newContent);
        msg.setEdited(true);
        return toResponse(chatMessageRepository.save(msg));
    }

    // ======== DELETE CONVERSATION ========

    @Transactional
    public void deleteConversation(String currentEmail, Long otherUserId) {
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        List<ChatMessage> msgs = chatMessageRepository.findDirectMessages(currentUser, otherUser);
        for (ChatMessage m : msgs) {
            if (m.getSender().getId().equals(currentUser.getId())) {
                m.setDeletedForSender(true);
            } else if (m.getReceiver() != null && m.getReceiver().getId().equals(currentUser.getId())) {
                m.setDeletedForReceiver(true);
            }
        }
        chatMessageRepository.saveAll(msgs);
    }

    // ======== HELPERS ========

    private boolean isMessageDeletedForUser(ChatMessage m, Long userId) {
        if (m.getSender().getId().equals(userId) && m.isDeletedForSender()) return true;
        if (m.getReceiver() != null && m.getReceiver().getId().equals(userId) && m.isDeletedForReceiver()) return true;
        return false;
    }

    private ChatMessageResponse toResponse(ChatMessage m) {
        return ChatMessageResponse.builder()
                .id(m.getId())
                .senderId(m.getSender().getId())
                .senderNom(m.getSender().getNom())
                .senderPrenom(m.getSender().getPrenom())
                .senderPhoto(m.getSender().getPhoto())
                .senderRole(getRoleName(m.getSender()))
                .receiverId(m.getReceiver() != null ? m.getReceiver().getId() : null)
                .receiverNom(m.getReceiver() != null ? m.getReceiver().getNom() : null)
                .receiverPrenom(m.getReceiver() != null ? m.getReceiver().getPrenom() : null)
                .classeId(m.getClasse() != null ? m.getClasse().getId() : null)
                .classeNom(m.getClasse() != null ? m.getClasse().getNom() : null)
                .content(m.getContent())
                .sentAt(m.getSentAt())
                .isRead(m.isRead())
                .isDeleted(m.isDeleted())
                .isEdited(m.isEdited())
                .build();
    }

    private String getRoleName(User u) {
        if (u.getRoles() == null || u.getRoles().isEmpty()) return "USER";
        return u.getRoles().iterator().next().getName().name().replace("ROLE_", "");
    }
}
