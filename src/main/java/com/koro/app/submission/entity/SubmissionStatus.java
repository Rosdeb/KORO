package com.koro.app.submission.entity;

/**
 * APPROVED is the terminal, published state — approval immediately creates/updates
 * the official dictionary entry, so no separate PUBLISHED status is tracked.
 */
public enum SubmissionStatus {
    PENDING,
    APPROVED,
    REJECTED
}
