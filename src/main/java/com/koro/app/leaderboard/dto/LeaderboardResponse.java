package com.koro.app.leaderboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardResponse {
    private LeaderboardType type;
    private LeaderboardPeriod period;
    private List<LeaderboardEntryResponse> entries;
    private LeaderboardEntryResponse currentUserRank;
}