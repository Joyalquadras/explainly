package com.explainly.video;

import com.explainly.common.ApiException;
import com.explainly.common.CurrentUser;
import com.explainly.history.LearningHistoryRepository;
import org.springframework.stereotype.Component;

/** A user may access an analysis once it is in their learning library. */
@Component
public class AnalysisAccess {

    private final LearningHistoryRepository history;
    private final VideoAnalysisRepository analyses;

    public AnalysisAccess(LearningHistoryRepository history, VideoAnalysisRepository analyses) {
        this.history = history;
        this.analyses = analyses;
    }

    public VideoAnalysis require(Long userId, Long analysisId) {
        VideoAnalysis analysis = analyses.findById(analysisId).orElseThrow(() -> ApiException.notFound("Video analysis"));
        if (!history.existsByUserIdAndAnalysisId(userId, analysisId) && !CurrentUser.isAdmin()) {
            // Same response as missing to avoid leaking which analyses exist.
            throw ApiException.notFound("Video analysis");
        }
        return analysis;
    }

    public VideoAnalysis requireCompleted(Long userId, Long analysisId) {
        VideoAnalysis analysis = require(userId, analysisId);
        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw ApiException.conflict("ANALYSIS_NOT_READY", "This video is still being prepared. Please wait until it's ready.");
        }
        return analysis;
    }
}
