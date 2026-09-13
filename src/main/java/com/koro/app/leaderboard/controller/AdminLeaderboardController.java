package com.koro.app.leaderboard.controller;

import com.koro.app.leaderboard.dto.AdminLeaderboardEntryResponse;
import com.koro.app.leaderboard.dto.AdminUserContributionDetail;
import com.koro.app.leaderboard.service.LeaderboardService;
import com.koro.app.submission.entity.SubmissionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/leaderboard")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
@Tag(name = "Admin Leaderboard", description = "Administrative leaderboard, contributor audits, and submission breakdowns")
public class AdminLeaderboardController {

    @Autowired
    private LeaderboardService leaderboardService;

    @GetMapping
    @Operation(summary = "Get detailed contributor rankings with status filters and emails")
    public ResponseEntity<List<AdminLeaderboardEntryResponse>> getAdminLeaderboard(
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "50") int limit) {

        LocalDateTime start = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime end = (to != null) ? to.atTime(LocalTime.MAX) : null;

        return ResponseEntity.ok(leaderboardService.getAdminLeaderboard(status, start, end, Math.min(limit, 500)));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get comprehensive submission and activity audit for a single user")
    public ResponseEntity<AdminUserContributionDetail> getUserContributionDetails(
            @PathVariable String userId) {
        return ResponseEntity.ok(leaderboardService.getUserContributionDetails(userId));
    }
}
