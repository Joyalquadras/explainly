package com.explainly.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long analysisId;
    private String role;
    private String content;
    private String citationsJson;
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
