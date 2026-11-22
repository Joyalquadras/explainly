package com.explainly.youtube;

import com.explainly.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "connected_accounts")
@Getter
@Setter
public class ConnectedAccount {
    public static final String PROVIDER_YOUTUBE = "YOUTUBE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    private String provider;
    private String providerAccountId;
    private String accountName;
    /** AES-GCM encrypted. */
    private String accessTokenEnc;
    /** AES-GCM encrypted. */
    private String refreshTokenEnc;
    private Instant tokenExpiresAt;
    private String scopes;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
