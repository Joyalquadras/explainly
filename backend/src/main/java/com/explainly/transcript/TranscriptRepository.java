package com.explainly.transcript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TranscriptRepository extends JpaRepository<Transcript, Long> {
    Optional<Transcript> findFirstByVideoIdAndContentHash(Long videoId, String contentHash);
}
