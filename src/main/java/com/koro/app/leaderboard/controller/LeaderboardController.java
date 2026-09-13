package com.koro.app.leaderboard.controller;

import com.koro.app.leaderboard.dto.LeaderboardPeriod;
import com.koro.app.leaderboard.dto.LeaderboardResponse;
import com.koro.app.leaderboard.dto.LeaderboardType;
import com.koro.app.leaderboard.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leaderboard")
@Tag(name = "Public Leaderboard", description = "Public rankings for top word submitters and translators")
public class LeaderboardController {

    @Autowired
    private LeaderboardService leaderboardService;

    @GetMapping
    @Operation(summary = "Get overall public leaderboard")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
            @RequestParam(defaultValue = "OVERALL") LeaderboardType type,
            @RequestParam(defaultValue = "ALL_TIME") LeaderboardPeriod period,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(leaderboardService.getPublicLeaderboard(type, period, Math.min(limit, 100)));
    }

    @GetMapping("/submissions")
    @Operation(summary = "Get public leaderboard sorted by approved submissions")
    public ResponseEntity<LeaderboardResponse> getSubmissionsLeaderboard(
            @RequestParam(defaultValue = "ALL_TIME") LeaderboardPeriod period,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(leaderboardService.getPublicLeaderboard(LeaderboardType.SUBMISSIONS, period, Math.min(limit, 100)));
    }

    @GetMapping("/translations")
    @Operation(summary = "Get public leaderboard sorted by translation activity")
    public ResponseEntity<LeaderboardResponse> getTranslationsLeaderboard(
            @RequestParam(defaultValue = "ALL_TIME") LeaderboardPeriod period,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(leaderboardService.getPublicLeaderboard(LeaderboardType.TRANSLATIONS, period, Math.min(limit, 100)));
    }
}