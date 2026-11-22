package com.explainly.youtube;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, Long> {
    Optional<ConnectedAccount> findByUserIdAndProvider(Long userId, String provider);
}
