package com.koro.app.activity;

import com.koro.app.activity.controller.ActivityController;
import com.koro.app.activity.entity.ActivityLog;
import com.koro.app.activity.repository.ActivityLogRepository;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityApiTest {
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void controllerPassesDateRangeAndPaginationAndReturnsMetadata() {
        ActivityLogService service = mock(ActivityLogService.class);
        ActivityController controller = new ActivityController();
        ReflectionTestUtils.setField(controller, "activityLogService", service);
        LocalDate from = LocalDate.of(2026, 9, 14);
        LocalDate to = LocalDate.of(2026, 9, 19);
        when(service.getLogsForCurrentUserFiltered(any(), any(), any())).thenAnswer(invocation ->
                new PageImpl<>(List.of(new ActivityLog()), invocation.getArgument(2, Pageable.class), 21));

        var result = controller.getActivityLogs(from, to, 2, 10).getBody();
        assertNotNull(result);
        assertEquals(2, result.page());
        assertEquals(10, result.size());
        assertEquals(21, result.totalElements());
        assertEquals(3, result.totalPages());
        assertTrue(result.last());
        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getLogsForCurrentUserFiltered(eq(from.atStartOfDay()), eq(to.atTime(LocalTime.MAX)), paging.capture());
        assertEquals(20, paging.getValue().getOffset());
        assertTrue(paging.getValue().getSort().getOrderFor("createdAt").isDescending());
        assertTrue(paging.getValue().getSort().getOrderFor("id").isDescending());
    }

    @Test
    void rejectsInvalidParametersBeforeServiceCanDeleteHistory() {
        ActivityLogService service = mock(ActivityLogService.class);
        ActivityController controller = new ActivityController();
        ReflectionTestUtils.setField(controller, "activityLogService", service);
        assertThrows(ResponseStatusException.class, () -> controller.getActivityLogs(null, null, -1, 10));
        assertThrows(ResponseStatusException.class, () -> controller.getActivityLogs(null, null, 0, 0));
        assertThrows(ResponseStatusException.class, () -> controller.getActivityLogs(null, null, 0, 101));
        assertThrows(ResponseStatusException.class, () -> controller.getActivityLogs(LocalDate.now(), LocalDate.now().minusDays(1), 0, 10));
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedReadDeletesOnlyExpiredActivityBeforeReadingUserPage() {
        ActivityLogRepository repository = mock(ActivityLogRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserRepository users = mock(UserRepository.class);
        ActivityLogService service = new ActivityLogService();
        ReflectionTestUtils.setField(service, "activityLogRepository", repository);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);
        ReflectionTestUtils.setField(service, "userRepository", users);
        User user = new User();
        user.setId("user-1");
        when(users.findById("user-1")).thenReturn(Optional.of(user));
        var principal = new CustomUserDetails("user-1", "User", "user@example.com", "", List.of(), true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        var pageable = PageRequest.of(0, 10);
        when(repository.findByUserId("user-1", pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        LocalDateTime earliestCutoff = LocalDateTime.now().minusDays(7);
        service.getLogsForCurrentUser(pageable);
        LocalDateTime latestCutoff = LocalDateTime.now().minusDays(7);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        var ordered = inOrder(mongo, repository);
        ordered.verify(mongo).remove(query.capture(), eq(ActivityLog.class));
        ordered.verify(repository).findByUserId("user-1", pageable);
        var criteria = (org.bson.Document) query.getValue().getQueryObject().get("createdAt");
        LocalDateTime cutoff = (LocalDateTime) criteria.get("$lt");
        assertFalse(cutoff.isBefore(earliestCutoff));
        assertFalse(cutoff.isAfter(latestCutoff));
        assertEquals(1, criteria.size());
        verifyNoMoreInteractions(mongo, repository);

        SecurityContextHolder.clearContext();
        clearInvocations(mongo, repository);
        assertThrows(RuntimeException.class, () -> service.getLogsForCurrentUser(pageable));
        verifyNoInteractions(mongo, repository);
    }
}
