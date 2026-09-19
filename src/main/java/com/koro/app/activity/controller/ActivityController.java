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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {

    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping
    public ResponseEntity<ActivityPage> getActivityLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        if (from != null || to != null) {
            LocalDateTime start = (from != null) ? from.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
            LocalDateTime end = (to != null) ? to.atTime(LocalTime.MAX) : LocalDateTime.now();
            return ResponseEntity.ok(ActivityPage.from(activityLogService.getLogsForCurrentUserFiltered(start, end, pageable)));
        }

        return ResponseEntity.ok(ActivityPage.from(activityLogService.getLogsForCurrentUser(pageable)));
    }

    public record ActivityPage(List<ActivityLog> content, int page, int size,
                               long totalElements, int totalPages, boolean first, boolean last) {
        static ActivityPage from(Page<ActivityLog> result) {
            return new ActivityPage(result.getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getActivityStatistics() {
        return ResponseEntity.ok(activityLogService.getStatisticsForCurrentUser());
    }
}
