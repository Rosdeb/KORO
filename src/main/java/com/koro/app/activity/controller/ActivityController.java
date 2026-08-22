package com.koro.app.activity.controller;

import com.koro.app.activity.entity.ActivityLog;
import com.koro.app.activity.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {

    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping
    public ResponseEntity<List<ActivityLog>> getActivityLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (from != null || to != null) {
            LocalDateTime start = (from != null) ? from.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
            LocalDateTime end = (to != null) ? to.atTime(LocalTime.MAX) : LocalDateTime.now();
            return ResponseEntity.ok(activityLogService.getLogsForCurrentUserFiltered(start, end));
        }

        return ResponseEntity.ok(activityLogService.getLogsForCurrentUser());
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getActivityStatistics() {
        return ResponseEntity.ok(activityLogService.getStatisticsForCurrentUser());
    }
}
