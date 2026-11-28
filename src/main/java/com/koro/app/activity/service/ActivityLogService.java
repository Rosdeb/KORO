package com.koro.app.activity.service;

import com.koro.app.activity.entity.ActivityLog;
import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.repository.ActivityLogRepository;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.LocalDateTime;
import java.util.HashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import com.koro.app.submission.repository.TranslationSubmissionRepository;
import com.koro.app.submission.entity.TranslationSubmission;

@Service
public class ActivityLogService {

    private static final Set<ActivityType> GENUINE_CONTRIBUTION_TYPES = EnumSet.of(
            ActivityType.TRANSLATION,
            ActivityType.SAVE_WORD,
            ActivityType.IMAGE_RECOGNITION,
            ActivityType.IMAGE_UPLOAD,
            ActivityType.EXPORT_PDF,
            ActivityType.DOWNLOAD_BOOK,
            ActivityType.ADD_VOCABULARY
    );

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private TranslationSubmissionRepository submissionRepository;

    @Transactional
    public void log(ActivityType type, String description, String referenceId, String metadata) {
        User user = getCurrentUser();
        HttpServletRequest request = getCurrentRequest();
        
        String ipAddress = "0.0.0.0";
        String userAgent = "Unknown";
        if (request != null) {
            ipAddress = request.getRemoteAddr();
            userAgent = request.getHeader("User-Agent");
        }

        ActivityLog log = ActivityLog.builder()
                .user(user)
                .activityType(type)
                .description(description)
                .referenceId(referenceId)
                .metadata(metadata)
                .ipAddress(ipAddress)
                .device(userAgent)
                .build();

        activityLogRepository.save(log);
    }

    public Page<ActivityLog> getLogsForCurrentUser(Pageable pageable) {
        User user = getCurrentUser();
        if (user == null) {
            throw new RuntimeException("Authentication required to get logs");
        }
        return activityLogRepository.findByUserId(user.getId(), pageable);
    }

    public Page<ActivityLog> getLogsForCurrentUserFiltered(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        User user = getCurrentUser();
        if (user == null) {
            throw new RuntimeException("Authentication required to get logs");
        }
        return activityLogRepository.findByUserIdAndCreatedAtBetween(user.getId(), start, end, pageable);
    }

    public Map<String, Object> getStatisticsForCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            throw new RuntimeException("Authentication required to get statistics");
        }
        String userId = user.getId();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalActivities", activityLogRepository.countByUserId(userId));
        stats.put("translations", activityLogRepository.countByUserIdAndActivityType(userId, ActivityType.TRANSLATION));
        stats.put("savedWords", activityLogRepository.countByUserIdAndActivityType(userId, ActivityType.SAVE_WORD));
        stats.put("imageRecognitions", activityLogRepository.countByUserIdAndActivityType(userId, ActivityType.IMAGE_RECOGNITION));
        stats.put("pdfExports", activityLogRepository.countByUserIdAndActivityType(userId, ActivityType.EXPORT_PDF));
        stats.put("submissions", submissionRepository != null ? submissionRepository.countBySubmittedById(userId) : 0);
        
        return stats;
    }

    public Map<String, Object> getDailySummaryForCurrentUser(String timezoneStr) {
        User user = getCurrentUser();
        if (user == null) {
            throw new RuntimeException("Authentication required to get summary");
        }
        String userId = user.getId();

        Map<String, Long> dayCounts = new TreeMap<>();
        long totalContributions = 0;

        // Fetch up to 2000 logs covering the full 52-week calendar window
        Pageable pageable = PageRequest.of(0, 2000, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ActivityLog> logs = activityLogRepository.findByUserId(userId, pageable);

        Set<String> trackedReferenceIds = new HashSet<>();
        for (ActivityLog log : logs.getContent()) {
            if (log.getReferenceId() != null) {
                trackedReferenceIds.add(log.getReferenceId());
            }
            if (log.getCreatedAt() != null && log.getActivityType() != null && GENUINE_CONTRIBUTION_TYPES.contains(log.getActivityType())) {
                String day = log.getCreatedAt().toLocalDate().toString();
                dayCounts.merge(day, 1L, Long::sum);
                totalContributions++;
            }
        }

        // Also check if any submissions were not tracked in activity_logs
        if (submissionRepository != null) {
            try {
                var subs = submissionRepository.findBySubmittedById(userId);
                for (var sub : subs) {
                    if (sub.getId() != null && !trackedReferenceIds.contains(sub.getId()) && sub.getCreatedAt() != null) {
                        String day = sub.getCreatedAt().toLocalDate().toString();
                        dayCounts.merge(day, 1L, Long::sum);
                        totalContributions++;
                    }
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("dayCounts", dayCounts);
        summary.put("totalContributions", totalContributions);
        summary.put("stats", getStatisticsForCurrentUser());

        return summary;
    }

    // Overloaded log for anonymous or manual logs
    @Transactional
    public void logManual(User user, ActivityType type, String description) {
        ActivityLog log = ActivityLog.builder()
                .user(user)
                .activityType(type)
                .description(description)
                .ipAddress("127.0.0.1")
                .device("System")
                .build();
        activityLogRepository.save(log);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
