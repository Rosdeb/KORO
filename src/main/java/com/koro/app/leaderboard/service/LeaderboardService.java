package com.koro.app.leaderboard.service;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.repository.ActivityLogRepository;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.leaderboard.dto.*;
import com.koro.app.leaderboard.repository.LeaderboardRepository;
import com.koro.app.leaderboard.repository.LeaderboardRepository.UserCountAggregate;
import com.koro.app.submission.entity.SubmissionStatus;
import com.koro.app.submission.repository.TranslationSubmissionRepository;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    @Autowired
    private LeaderboardRepository leaderboardRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TranslationSubmissionRepository submissionRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    // ==========================================
    //            PUBLIC METHODS
    // ==========================================

    public LeaderboardResponse getPublicLeaderboard(LeaderboardType type, LeaderboardPeriod period, int limit) {
        LocalDateTime since = resolveSinceDate(period);
        List<LeaderboardEntryResponse> entries = switch (type) {
            case SUBMISSIONS -> buildPublicSubmissions(since, limit);
            case TRANSLATIONS -> buildPublicTranslations(since, limit);
            case OVERALL -> buildPublicOverall(since, limit);
        };

        return LeaderboardResponse.builder()
                .type(type)
                .period(period)
                .entries(entries)
                .currentUserRank(findCurrentUserRank(entries))
                .build();
    }

    private List<LeaderboardEntryResponse> buildPublicSubmissions(LocalDateTime since, int limit) {
        List<UserCountAggregate> top = leaderboardRepository.getSubmissionsAggregated(SubmissionStatus.APPROVED, since, null, limit);
        return enrichPublic(top, (agg, entry) -> {
            entry.setSubmissionsCount(agg.getCount());
            entry.setScore(agg.getCount() * 10L);
        });
    }

    private List<LeaderboardEntryResponse> buildPublicTranslations(LocalDateTime since, int limit) {
        List<UserCountAggregate> top = leaderboardRepository.getActivitiesAggregated(ActivityType.TRANSLATION, since, null, limit);
        return enrichPublic(top, (agg, entry) -> {
            entry.setTranslationsCount(agg.getCount());
            entry.setScore(agg.getCount() * 2L);
        });
    }

    private List<LeaderboardEntryResponse> buildPublicOverall(LocalDateTime since, int limit) {
        List<UserCountAggregate> subs = leaderboardRepository.getSubmissionsAggregated(SubmissionStatus.APPROVED, since, null, 150);
        List<UserCountAggregate> trans = leaderboardRepository.getActivitiesAggregated(ActivityType.TRANSLATION, since, null, 150);

        Map<String, Long> subMap = subs.stream()
                .filter(a -> a.getUserIdString() != null)
                .collect(Collectors.toMap(UserCountAggregate::getUserIdString, UserCountAggregate::getCount, Long::sum));
        Map<String, Long> transMap = trans.stream()
                .filter(a -> a.getUserIdString() != null)
                .collect(Collectors.toMap(UserCountAggregate::getUserIdString, UserCountAggregate::getCount, Long::sum));

        Set<String> userIds = new HashSet<>(subMap.keySet());
        userIds.addAll(transMap.keySet());

        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<LeaderboardEntryResponse> result = new ArrayList<>();
        for (String uid : userIds) {
            User u = userMap.get(uid);
            if (u == null) continue;
            long sCount = subMap.getOrDefault(uid, 0L);
            long tCount = transMap.getOrDefault(uid, 0L);
            long score = (sCount * 10L) + (tCount * 2L);

            result.add(LeaderboardEntryResponse.builder()
                    .userId(uid)
                    .name(u.getName())
                    .profileImage(u.getProfileImage())
                    .nativeLanguage(u.getNativeLanguage())
                    .submissionsCount(sCount)
                    .translationsCount(tCount)
                    .score(score)
                    .build());
        }

        result.sort((a, b) -> Long.compare(b.getScore(), a.getScore()));
        int rank = 1;
        for (LeaderboardEntryResponse entry : result) {
            entry.setRank(rank);
            entry.setBadge(assignBadge(rank));
            rank++;
        }
        return result.stream().limit(limit).collect(Collectors.toList());
    }

    // ==========================================
    //            ADMIN METHODS
    // ==========================================

    public List<AdminLeaderboardEntryResponse> getAdminLeaderboard(
            SubmissionStatus statusFilter, LocalDateTime start, LocalDateTime end, int limit) {

        List<UserCountAggregate> topSubs = leaderboardRepository.getSubmissionsAggregated(statusFilter, start, end, limit);
        List<String> userIds = topSubs.stream()
                .map(UserCountAggregate::getUserIdString)
                .filter(Objects::nonNull)
                .toList();

        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminLeaderboardEntryResponse> list = new ArrayList<>();

        for (UserCountAggregate agg : topSubs) {
            String uid = agg.getUserIdString();
            User u = userMap.get(uid);
            if (u == null) continue;

            var userSubs = submissionRepository.findBySubmittedById(uid);
            long approved = userSubs.stream().filter(s -> s.getStatus() == SubmissionStatus.APPROVED).count();
            long pending = userSubs.stream().filter(s -> s.getStatus() == SubmissionStatus.PENDING).count();
            long rejected = userSubs.stream().filter(s -> s.getStatus() == SubmissionStatus.REJECTED).count();
            long translations = activityLogRepository.countByUserIdAndActivityType(uid, ActivityType.TRANSLATION);

            Set<String> roleStrings = (u.getRoles() != null)
                    ? u.getRoles().stream().map(Enum::name).collect(Collectors.toSet())
                    : Collections.emptySet();

            list.add(AdminLeaderboardEntryResponse.builder()
                    .userId(uid)
                    .name(u.getName())
                    .email(u.getEmail())
                    .profileImage(u.getProfileImage())
                    .status(u.getStatus() != null ? u.getStatus().name() : "ACTIVE")
                    .roles(roleStrings)
                    .approvedSubmissions(approved)
                    .pendingSubmissions(pending)
                    .rejectedSubmissions(rejected)
                    .totalSubmissions(approved + pending + rejected)
                    .translationActivities(translations)
                    .score((approved * 10L) + (translations * 2L))
                    .createdAt(u.getCreatedAt())
                    .build());
        }

        // Sort by total score or submissions
        list.sort((a, b) -> Long.compare(b.getScore(), a.getScore()));
        int rank = 1;
        for (AdminLeaderboardEntryResponse entry : list) {
            entry.setRank(rank++);
        }

        return list;
    }

    public AdminUserContributionDetail getUserContributionDetails(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        var submissions = submissionRepository.findBySubmittedById(userId);
        long approved = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.APPROVED).count();
        long pending = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.PENDING).count();
        long rejected = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.REJECTED).count();

        Map<String, Long> activityBreakdown = new HashMap<>();
        for (ActivityType type : ActivityType.values()) {
            long count = activityLogRepository.countByUserIdAndActivityType(userId, type);
            if (count > 0) {
                activityBreakdown.put(type.name(), count);
            }
        }

        long transCount = activityBreakdown.getOrDefault(ActivityType.TRANSLATION.name(), 0L);

        return AdminUserContributionDetail.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .totalSubmissions(submissions.size())
                .approvedSubmissions(approved)
                .pendingSubmissions(pending)
                .rejectedSubmissions(rejected)
                .activityTypeBreakdown(activityBreakdown)
                .calculatedScore((approved * 10L) + (transCount * 2L))
                .build();
    }

    // ==========================================
    //            HELPERS
    // ==========================================

    private List<LeaderboardEntryResponse> enrichPublic(
            List<UserCountAggregate> aggregates,
            java.util.function.BiConsumer<UserCountAggregate, LeaderboardEntryResponse> scoreMapper) {

        List<String> userIds = aggregates.stream()
                .map(UserCountAggregate::getUserIdString)
                .filter(Objects::nonNull)
                .toList();

        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<LeaderboardEntryResponse> result = new ArrayList<>();
        int rank = 1;

        for (UserCountAggregate agg : aggregates) {
            String uid = agg.getUserIdString();
            User u = userMap.get(uid);
            if (u == null) continue;

            LeaderboardEntryResponse entry = LeaderboardEntryResponse.builder()
                    .rank(rank)
                    .userId(u.getId())
                    .name(u.getName())
                    .profileImage(u.getProfileImage())
                    .nativeLanguage(u.getNativeLanguage())
                    .badge(assignBadge(rank))
                    .build();

            scoreMapper.accept(agg, entry);
            result.add(entry);
            rank++;
        }
        return result;
    }

    private String assignBadge(int rank) {
        return switch (rank) {
            case 1 -> "🥇 Gold";
            case 2 -> "🥈 Silver";
            case 3 -> "🥉 Bronze";
            default -> rank <= 10 ? "Top 10" : null;
        };
    }

    private LocalDateTime resolveSinceDate(LeaderboardPeriod period) {
        if (period == null) return null;
        return switch (period) {
            case WEEKLY -> LocalDateTime.now().minusDays(7);
            case MONTHLY -> LocalDateTime.now().minusDays(30);
            case ALL_TIME -> null;
        };
    }

    private LeaderboardEntryResponse findCurrentUserRank(List<LeaderboardEntryResponse> entries) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }
        String currentUserId = userDetails.getId();

        // Check if current user is in top results
        Optional<LeaderboardEntryResponse> match = entries.stream()
                .filter(e -> e.getUserId().equals(currentUserId))
                .findFirst();

        if (match.isPresent()) {
            return match.get();
        }

        // Otherwise compute user's own stats
        return userRepository.findById(currentUserId).map(u -> {
            long approved = submissionRepository.findBySubmittedById(currentUserId).stream()
                    .filter(s -> s.getStatus() == SubmissionStatus.APPROVED).count();
            long translations = activityLogRepository.countByUserIdAndActivityType(currentUserId, ActivityType.TRANSLATION);
            long score = (approved * 10L) + (translations * 2L);

            return LeaderboardEntryResponse.builder()
                    .rank(-1) // Indicates unranked or beyond top entries
                    .userId(u.getId())
                    .name(u.getName())
                    .profileImage(u.getProfileImage())
                    .nativeLanguage(u.getNativeLanguage())
                    .submissionsCount(approved)
                    .translationsCount(translations)
                    .score(score)
                    .badge(null)
                    .build();
        }).orElse(null);
    }
}