package com.koro.app.leaderboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLeaderboardEntryResponse {
    private int rank;
    private String userId;
    private String name;
    private String email;
    private String profileImage;
    private String status;
    private Set<String> roles;
    private long approvedSubmissions;
    private long pendingSubmissions;
    private long rejectedSubmissions;
    private long totalSubmissions;
    private long translationActivities;
    private long score;
    private LocalDateTime createdAt;
}
