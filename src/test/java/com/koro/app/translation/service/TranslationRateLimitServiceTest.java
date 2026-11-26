package com.koro.app.translation.service;

import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.translation.dto.RateLimitResult;
import com.koro.app.translation.dto.UserRateLimitDto;
import com.koro.app.translation.dto.UserRateLimitUpdateRequest;
import com.koro.app.translation.entity.SearchRateLimit;
import com.koro.app.translation.repository.SearchRateLimitRepository;
import com.koro.app.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationRateLimitServiceTest {

    @Mock
    private SearchRateLimitRepository searchRateLimitRepository;

    private TranslationRateLimitService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        service = new TranslationRateLimitService(searchRateLimitRepository);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "userDailyLimit", 5);
        ReflectionTestUtils.setField(service, "userMonthlyLimit", 20);
        ReflectionTestUtils.setField(service, "anonDailyLimit", 2);
        ReflectionTestUtils.setField(service, "anonMonthlyLimit", 10);
    }

    @Test
    void anonymousUserAllowedUnderLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        when(searchRateLimitRepository.findById("IP:192.168.1.100")).thenReturn(Optional.empty());
        when(searchRateLimitRepository.save(any(SearchRateLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RateLimitResult result = service.checkAndConsumeSearchQuota(request);

        assertTrue(result.isAllowed());
        assertEquals(2, result.getDailyLimit());
        assertEquals(1, result.getDailyRemaining());
        assertEquals(10, result.getMonthlyLimit());
        assertEquals(9, result.getMonthlyRemaining());
        verify(searchRateLimitRepository).save(any(SearchRateLimit.class));
    }

    @Test
    void anonymousUserReachesDailyLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String currentMonth = today.substring(0, 7);

        SearchRateLimit existing = SearchRateLimit.builder()
                .id("IP:192.168.1.100")
                .identifier("192.168.1.100")
                .type("IP")
                .dailyDate(today)
                .dailyCount(2) // already at limit
                .monthlyMonth(currentMonth)
                .monthlyCount(2)
                .build();

        when(searchRateLimitRepository.findById("IP:192.168.1.100")).thenReturn(Optional.of(existing));

        RateLimitResult result = service.checkAndConsumeSearchQuota(request);

        assertFalse(result.isAllowed());
        assertEquals(0, result.getDailyRemaining());
        assertTrue(result.getMessage().contains("Daily search limit of 2 reached"));
        verify(searchRateLimitRepository, never()).save(any(SearchRateLimit.class));
    }

    @Test
    void anonymousUserReachesMonthlyLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String currentMonth = today.substring(0, 7);

        SearchRateLimit existing = SearchRateLimit.builder()
                .id("IP:192.168.1.100")
                .identifier("192.168.1.100")
                .type("IP")
                .dailyDate(today)
                .dailyCount(1)
                .monthlyMonth(currentMonth)
                .monthlyCount(10) // already at monthly limit
                .build();

        when(searchRateLimitRepository.findById("IP:192.168.1.100")).thenReturn(Optional.of(existing));

        RateLimitResult result = service.checkAndConsumeSearchQuota(request);

        assertFalse(result.isAllowed());
        assertEquals(0, result.getMonthlyRemaining());
        assertTrue(result.getMessage().contains("Monthly search limit of 10 reached"));
        verify(searchRateLimitRepository, never()).save(any(SearchRateLimit.class));
    }

    @Test
    void dailyCounterResetsOnNewDay() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String currentMonth = today.substring(0, 7);

        SearchRateLimit yesterdayRecord = SearchRateLimit.builder()
                .id("IP:192.168.1.100")
                .identifier("192.168.1.100")
                .type("IP")
                .dailyDate("2020-01-01") // Old day
                .dailyCount(2)
                .monthlyMonth(currentMonth)
                .monthlyCount(2)
                .build();

        when(searchRateLimitRepository.findById("IP:192.168.1.100")).thenReturn(Optional.of(yesterdayRecord));
        when(searchRateLimitRepository.save(any(SearchRateLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RateLimitResult result = service.checkAndConsumeSearchQuota(request);

        assertTrue(result.isAllowed());
        assertEquals(1, result.getDailyRemaining());
        assertEquals(today, yesterdayRecord.getDailyDate());
        assertEquals(1, yesterdayRecord.getDailyCount());
    }

    @Test
    void authenticatedUserUsesUserLimits() {
        CustomUserDetails userDetails = new CustomUserDetails(
                "user-123", "Alice", "alice@example.com", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), true
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        when(searchRateLimitRepository.findById("USER:user-123")).thenReturn(Optional.empty());
        when(searchRateLimitRepository.save(any(SearchRateLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RateLimitResult result = service.checkAndConsumeSearchQuota(request);

        assertTrue(result.isAllowed());
        assertEquals(5, result.getDailyLimit());
        assertEquals(4, result.getDailyRemaining());
        assertEquals(20, result.getMonthlyLimit());
        assertEquals(19, result.getMonthlyRemaining());
    }

    @Test
    void authenticatedUserWithCustomLimitsUsesCustomDailyAndMonthlyLimits() {
        CustomUserDetails userDetails = new CustomUserDetails(
                "user-vip", "VIP User", "vip@example.com", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), true
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String currentMonth = today.substring(0, 7);

        SearchRateLimit existing = SearchRateLimit.builder()
                .id("USER:user-vip")
                .identifier("user-vip")
                .type("USER")
                .dailyDate(today)
                .dailyCount(5) // exceeds default 5, but custom limit is 50
                .monthlyMonth(currentMonth)
                .monthlyCount(25) // exceeds default 20, but custom limit is 200
                .customDailyLimit(50)
                .customMonthlyLimit(200)
                .build();

        when(searchRateLimitRepository.findById("USER:user-vip")).thenReturn(Optional.of(existing));
        when(searchRateLimitRepository.save(any(SearchRateLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        RateLimitResult result = service.checkAndConsumeSearchQuota(request);

        assertTrue(result.isAllowed());
        assertEquals(50, result.getDailyLimit());
        assertEquals(44, result.getDailyRemaining());
        assertEquals(200, result.getMonthlyLimit());
        assertEquals(174, result.getMonthlyRemaining());
    }

    @Test
    void getUserRateLimitReturnsDefaultsWhenNoRecord() {
        User user = User.builder().id("user-1").name("Alice").email("alice@example.com").build();
        when(searchRateLimitRepository.findById("USER:user-1")).thenReturn(Optional.empty());

        UserRateLimitDto dto = service.getUserRateLimit(user);

        assertEquals("user-1", dto.getUserId());
        assertEquals("Alice", dto.getUserName());
        assertEquals("alice@example.com", dto.getUserEmail());
        assertEquals(0, dto.getDailyCount());
        assertEquals(5, dto.getDailyLimit());
        assertEquals(5, dto.getDailyRemaining());
        assertEquals(0, dto.getMonthlyCount());
        assertEquals(20, dto.getMonthlyLimit());
        assertEquals(20, dto.getMonthlyRemaining());
        assertNull(dto.getCustomDailyLimit());
        assertNull(dto.getCustomMonthlyLimit());
    }

    @Test
    void updateUserRateLimitSetsCustomLimitsAndResetsCounts() {
        User user = User.builder().id("user-1").name("Alice").email("alice@example.com").build();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String currentMonth = today.substring(0, 7);

        SearchRateLimit existing = SearchRateLimit.builder()
                .id("USER:user-1")
                .identifier("user-1")
                .type("USER")
                .dailyDate(today)
                .dailyCount(10)
                .monthlyMonth(currentMonth)
                .monthlyCount(50)
                .build();

        when(searchRateLimitRepository.findById("USER:user-1")).thenReturn(Optional.of(existing));
        when(searchRateLimitRepository.save(any(SearchRateLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserRateLimitUpdateRequest request = new UserRateLimitUpdateRequest(100, 1000, true, true);
        UserRateLimitDto result = service.updateUserRateLimit(user, request);

        assertEquals(100, result.getDailyLimit());
        assertEquals(1000, result.getMonthlyLimit());
        assertEquals(0, result.getDailyCount());
        assertEquals(0, result.getMonthlyCount());
        assertEquals(100, result.getDailyRemaining());
        assertEquals(1000, result.getMonthlyRemaining());
        assertEquals(100, result.getCustomDailyLimit());
        assertEquals(1000, result.getCustomMonthlyLimit());
    }

    @Test
    void updateUserRateLimitClearsCustomLimitsWhenZeroOrNegative() {
        User user = User.builder().id("user-1").name("Alice").email("alice@example.com").build();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String currentMonth = today.substring(0, 7);

        SearchRateLimit existing = SearchRateLimit.builder()
                .id("USER:user-1")
                .identifier("user-1")
                .type("USER")
                .dailyDate(today)
                .dailyCount(2)
                .monthlyMonth(currentMonth)
                .monthlyCount(5)
                .customDailyLimit(100)
                .customMonthlyLimit(1000)
                .build();

        when(searchRateLimitRepository.findById("USER:user-1")).thenReturn(Optional.of(existing));
        when(searchRateLimitRepository.save(any(SearchRateLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserRateLimitUpdateRequest request = new UserRateLimitUpdateRequest(0, -1, false, false);
        UserRateLimitDto result = service.updateUserRateLimit(user, request);

        assertEquals(5, result.getDailyLimit()); // reverts to default 5
        assertEquals(20, result.getMonthlyLimit()); // reverts to default 20
        assertNull(result.getCustomDailyLimit());
        assertNull(result.getCustomMonthlyLimit());
    }

    @Test
    void adminUserIsExemptFromRateLimits() {
        CustomUserDetails adminDetails = new CustomUserDetails(
                "admin-1", "Admin", "admin@example.com", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), true
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        RateLimitResult result = service.checkAndConsumeSearchQuota(request);

        assertTrue(result.isAllowed());
        assertEquals(Integer.MAX_VALUE, result.getDailyLimit());
        verifyNoInteractions(searchRateLimitRepository);
    }

    @Test
    void clientIpExtractionPrefersCloudflareHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "203.0.113.195");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.1");
        request.setRemoteAddr("10.0.0.1");

        String ip = service.extractClientIp(request);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void disabledRateLimitAlwaysAllows() {
        ReflectionTestUtils.setField(service, "enabled", false);
        MockHttpServletRequest request = new MockHttpServletRequest();

        RateLimitResult result = service.checkAndConsumeSearchQuota(request);

        assertTrue(result.isAllowed());
        assertEquals(Integer.MAX_VALUE, result.getDailyLimit());
        verifyNoInteractions(searchRateLimitRepository);
    }
}
