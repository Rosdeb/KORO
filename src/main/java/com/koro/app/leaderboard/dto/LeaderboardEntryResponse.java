package com.koro.app.leaderboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryResponse {
    private int rank;
    private String userId;
    private String name;
    private String profileImage;
    private String nativeLanguage;
    private long submissionsCount;
    private long translationsCount;
    private long score;
    private String badge;
}