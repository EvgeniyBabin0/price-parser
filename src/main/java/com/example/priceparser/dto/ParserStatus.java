package com.example.priceparser.dto;

import java.time.Instant;

public record ParserStatus(
        int poolSize,
        int activeThreads,
        int queuedTasks,
        long completedTasks,
        long succeeded,
        long failed,
        long skippedInProgress,
        long scheduledRuns,
        Instant lastScheduledRunAt,
        int trackedUrlsInProgress
) {
}
