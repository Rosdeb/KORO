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
import java.util.Map;

@Service
public class ActivityLogService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private UserRepository userRepository;

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
        
        return stats;
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
