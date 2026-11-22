package com.explainly.video;

import java.util.EnumSet;
import java.util.Set;

public enum AnalysisStatus {
    PENDING(5),
    FETCHING_TRANSCRIPT(10),
    AWAITING_TRANSCRIPT(10),
    PROCESSING(25),
    GENERATING_EXPLANATION(40),
    GENERATING_ILLUSTRATIONS(80),
    COMPLETED(100),
    FAILED(0);

    public static final Set<AnalysisStatus> IN_FLIGHT =
            EnumSet.of(PENDING, FETCHING_TRANSCRIPT, PROCESSING, GENERATING_EXPLANATION, GENERATING_ILLUSTRATIONS);

    private final int baseProgress;

    AnalysisStatus(int baseProgress) { this.baseProgress = baseProgress; }

    public int baseProgress() { return baseProgress; }
}
