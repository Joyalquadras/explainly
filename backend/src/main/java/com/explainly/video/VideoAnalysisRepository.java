package com.explainly.video;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VideoAnalysisRepository extends JpaRepository<VideoAnalysis, Long> {

    List<VideoAnalysis> findByStatusIn(Collection<AnalysisStatus> statuses);

    @Query("""
            select a from VideoAnalysis a join a.transcript t
            where a.video.id = :videoId and a.status = com.explainly.video.AnalysisStatus.COMPLETED
              and t.source = com.explainly.transcript.TranscriptSource.YOUTUBE_CAPTIONS
            order by a.completedAt desc""")
    List<VideoAnalysis> findCompletedFromOfficialCaptions(Long videoId);

    @Query("""
            select a from VideoAnalysis a
            where a.transcript.id = :transcriptId and a.id <> :excludeId
              and a.status = com.explainly.video.AnalysisStatus.COMPLETED
            order by a.completedAt desc""")
    List<VideoAnalysis> findCompletedWithTranscript(Long transcriptId, Long excludeId);

    long countByCreatedByIdAndCreatedAtAfter(Long userId, Instant after);

    long countByStatus(AnalysisStatus status);

    Optional<VideoAnalysis> findFirstByVideoIdAndCreatedByIdOrderByCreatedAtDesc(Long videoId, Long userId);
}
