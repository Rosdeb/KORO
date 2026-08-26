package com.koro.app.submission.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmissionReviewRequest {
    private String reviewerNote;

    // Required by the reject endpoint; ignored by approve.
    private String rejectionReason;
}
