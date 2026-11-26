package com.koro.app.translation.service;

import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.translation.dto.RateLimitConfigDto;
import com.koro.app.translation.dto.RateLimitResult;
import com.koro.app.translation.dto.UserRateLimitDto;
import com.koro.app.translation.dto.UserRateLimitUpdateRequest;
import com.koro.app.translation.entity.SearchRateLimit;
import com.koro.app.translation.repository.SearchRateLimitRepository;
import com.koro.app.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class TranslationRateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(TranslationRateLimitService.class);

    private final SearchRateLimitRepository searchRateLimitRepository;

    @Value("${app.rate-limit.search.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.search.user-daily-limit:50}")
    private int userDailyLimit;

    @Value("${app.rate-limit.search.user-monthly-limit:500}")
    private int userMonthlyLimit;

    @Value("${app.rate-limit.search.anon-daily-limit:15}")
    private int anonDailyLimit;

    @Value("${app.rate-limit.search.anon-monthly-limit:150}")
    private int anonMonthlyLimit;

    public TranslationRateLimitService(SearchRateLimitRepository searchRateLimitRepository) {
        this.searchRateLimitRepository = searchRateLimitRepository;
    }

    public RateLimitResult checkSearchQuota(HttpServletRequest request) {
        return processSearchQuota(request, false);
    }

    public RateLimitResult consumeSearchQuota(HttpServletRequest request) {
        return processSearchQuota(request, true);
    }

    public RateLimitResult checkAndConsumeSearchQuota(HttpServletRequest request) {
        return processSearchQuota(request, true);
    }

    private RateLimitResult processSearchQuota(HttpServletRequest request, boolean consume) {
        if (!enabled) {
            return RateLimitResult.allowed(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());

        // Check if user is admin or moderator -> exempt from rate limits
        if (isAuthenticated && hasAdminOrModeratorRole(auth)) {
            logger.debug("Bypassing search rate limit for admin/moderator user: {}", auth.getName());
            return RateLimitResult.allowed(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        String identifierKey;
        String rawIdentifier;
        String type;
        int dailyLimit;
        int monthlyLimit;

        if (isAuthenticated && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            rawIdentifier = userDetails.getId();
            identifierKey = "USER:" + rawIdentifier;
            type = "USER";
            dailyLimit = userDailyLimit;
            monthlyLimit = userMonthlyLimit;
        } else {
            rawIdentifier = extractClientIp(request);
            identifierKey = "IP:" + rawIdentifier;
            type = "IP";
            dailyLimit = anonDailyLimit;
            monthlyLimit = anonMonthlyLimit;
        }

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String currentMonth = today.substring(0, 7);

        synchronized (identifierKey.intern()) {
            SearchRateLimit record = searchRateLimitRepository.findById(identifierKey)
                    .orElseGet(() -> SearchRateLimit.builder()
                            .id(identifierKey)
                            .identifier(rawIdentifier)
                            .type(type)
                            .dailyDate(today)
                            .dailyCount(0)
                            .monthlyMonth(currentMonth)
                            .monthlyCount(0)
                            .build());

            // Reset daily bucket if on a new day
            if (!today.equals(record.getDailyDate())) {
                record.setDailyDate(today);
                record.setDailyCount(0);
            }

            // Reset monthly bucket if on a new month
            if (!currentMonth.equals(record.getMonthlyMonth())) {
                record.setMonthlyMonth(currentMonth);
                record.setMonthlyCount(0);
            }

            // Apply custom limit overrides if configured for this user
            if ("USER".equals(type)) {
                if (record.getCustomDailyLimit() != null && record.getCustomDailyLimit() > 0) {
                    dailyLimit = record.getCustomDailyLimit();
                }
                if (record.getCustomMonthlyLimit() != null && record.getCustomMonthlyLimit() > 0) {
                    monthlyLimit = record.getCustomMonthlyLimit();
                }
            }

            // Check if daily quota is reached
            if (record.getDailyCount() >= dailyLimit) {
                String message = "USER".equals(type)
                        ? String.format("Daily search limit of %d reached. Please try again tomorrow.", dailyLimit)
                        : String.format("Daily search limit of %d reached for guest users. Please log in for higher limits or try again tomorrow.", dailyLimit);
                return RateLimitResult.denied(message, dailyLimit, 0, monthlyLimit, Math.max(0, monthlyLimit - record.getMonthlyCount()));
            }

            // Check if monthly quota is reached
            if (record.getMonthlyCount() >= monthlyLimit) {
                String message = "USER".equals(type)
                        ? String.format("Monthly search limit of %d reached. Quota will reset next month.", monthlyLimit)
                        : String.format("Monthly search limit of %d reached for guest users. Please log in for higher limits or try again next month.", monthlyLimit);
                return RateLimitResult.denied(message, dailyLimit, Math.max(0, dailyLimit - record.getDailyCount()), monthlyLimit, 0);
            }

            // Increment and persist only when consuming quota
            if (consume) {
                record.setDailyCount(record.getDailyCount() + 1);
                record.setMonthlyCount(record.getMonthlyCount() + 1);
                record.setLastSearchAt(Instant.now());
                searchRateLimitRepository.save(record);
            }

            int remainingDaily = dailyLimit - record.getDailyCount();
            int remainingMonthly = monthlyLimit - record.getMonthlyCount();
            return RateLimitResult.allowed(dailyLimit, remainingDaily, monthlyLimit, remainingMonthly);
        }
    }

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "0.0.0.0";
        }
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (StringUtils.hasText(cfConnectingIp)) {
            return cfConnectingIp.trim();
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            int commaIndex = xForwardedFor.indexOf(',');
            return (commaIndex != -1 ? xForwardedFor.substring(0, commaIndex) : xForwardedFor).trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "0.0.0.0";
    }

    private boolean hasAdminOrModeratorRole(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ROLE_MODERATOR".equals(role)
                    || "ADMIN".equals(role) || "MODERATOR".equals(role)) {
                return true;
            }
        }
        return false;
    }

    public synchronized RateLimitConfigDto getConfig() {
        return new RateLimitConfigDto(enabled, userDailyLimit, userMonthlyLimit, anonDailyLimit, anonMonthlyLimit);
    }

    public synchronized RateLimitConfigDto updateConfig(RateLimitConfigDto dto) {
        if (dto != null) {
            this.enabled = dto.isEnabled();
            if (dto.getUserDailyLimit() > 0) this.userDailyLimit = dto.getUserDailyLimit();
            if (dto.getUserMonthlyLimit() > 0) this.userMonthlyLimit = dto.getUserMonthlyLimit();
            if (dto.getAnonDailyLimit() > 0) this.anonDailyLimit = dto.getAnonDailyLimit();
            if (dto.getAnonMonthlyLimit() > 0) this.anonMonthlyLimit = dto.getAnonMonthlyLimit();
        }
        return getConfig();
    }

    public UserRateLimitDto getUserRateLimit(User user) {
        String identifierKey = "USER:" + user.getId();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String currentMonth = today.substring(0, 7);

        SearchRateLimit record = searchRateLimitRepository.findById(identifierKey).orElse(null);

        int effectiveDailyLimit = (record != null && record.getCustomDailyLimit() != null && record.getCustomDailyLimit() > 0)
                ? record.getCustomDailyLimit()
                : userDailyLimit;

        int effectiveMonthlyLimit = (record != null && record.getCustomMonthlyLimit() != null && record.getCustomMonthlyLimit() > 0)
                ? record.getCustomMonthlyLimit()
                : userMonthlyLimit;

        int dailyCount = 0;
        int monthlyCount = 0;
        Integer customDailyLimit = null;
        Integer customMonthlyLimit = null;
        Instant lastSearchAt = null;

        if (record != null) {
            if (today.equals(record.getDailyDate())) {
                dailyCount = record.getDailyCount();
            }
            if (currentMonth.equals(record.getMonthlyMonth())) {
                monthlyCount = record.getMonthlyCount();
            }
            customDailyLimit = record.getCustomDailyLimit();
            customMonthlyLimit = record.getCustomMonthlyLimit();
            lastSearchAt = record.getLastSearchAt();
        }

        int dailyRemaining = Math.max(0, effectiveDailyLimit - dailyCount);
        int monthlyRemaining = Math.max(0, effectiveMonthlyLimit - monthlyCount);

        return new UserRateLimitDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                dailyCount,
                effectiveDailyLimit,
                dailyRemaining,
                monthlyCount,
                effectiveMonthlyLimit,
                monthlyRemaining,
                customDailyLimit,
                customMonthlyLimit,
                lastSearchAt
        );
    }

    public UserRateLimitDto updateUserRateLimit(User user, UserRateLimitUpdateRequest request) {
        String identifierKey = "USER:" + user.getId();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String currentMonth = today.substring(0, 7);

        synchronized (identifierKey.intern()) {
            SearchRateLimit record = searchRateLimitRepository.findById(identifierKey)
                    .orElseGet(() -> SearchRateLimit.builder()
                            .id(identifierKey)
                            .identifier(user.getId())
                            .type("USER")
                            .dailyDate(today)
                            .dailyCount(0)
                            .monthlyMonth(currentMonth)
                            .monthlyCount(0)
                            .build());

            // Reset date buckets if outdated
            if (!today.equals(record.getDailyDate())) {
                record.setDailyDate(today);
                record.setDailyCount(0);
            }
            if (!currentMonth.equals(record.getMonthlyMonth())) {
                record.setMonthlyMonth(currentMonth);
                record.setMonthlyCount(0);
            }

            if (request != null) {
                // Update custom limits (<= 0 clears the custom limit override)
                if (request.getCustomDailyLimit() != null) {
                    if (request.getCustomDailyLimit() > 0) {
                        record.setCustomDailyLimit(request.getCustomDailyLimit());
                    } else {
                        record.setCustomDailyLimit(null);
                    }
                }

                if (request.getCustomMonthlyLimit() != null) {
                    if (request.getCustomMonthlyLimit() > 0) {
                        record.setCustomMonthlyLimit(request.getCustomMonthlyLimit());
                    } else {
                        record.setCustomMonthlyLimit(null);
                    }
                }

                if (request.isResetDailyCount()) {
                    record.setDailyCount(0);
                }

                if (request.isResetMonthlyCount()) {
                    record.setMonthlyCount(0);
                }
            }

            searchRateLimitRepository.save(record);
            return getUserRateLimit(user);
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getUserDailyLimit() { return userDailyLimit; }
    public void setUserDailyLimit(int userDailyLimit) { this.userDailyLimit = userDailyLimit; }
    public int getUserMonthlyLimit() { return userMonthlyLimit; }
    public void setUserMonthlyLimit(int userMonthlyLimit) { this.userMonthlyLimit = userMonthlyLimit; }
    public int getAnonDailyLimit() { return anonDailyLimit; }
    public void setAnonDailyLimit(int anonDailyLimit) { this.anonDailyLimit = anonDailyLimit; }
    public int getAnonMonthlyLimit() { return anonMonthlyLimit; }
    public void setAnonMonthlyLimit(int anonMonthlyLimit) { this.anonMonthlyLimit = anonMonthlyLimit; }
}
