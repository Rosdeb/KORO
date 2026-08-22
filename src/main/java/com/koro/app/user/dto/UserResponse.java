package com.koro.app.user.dto;

import com.koro.app.user.entity.User;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private String profileImage;
    private String nativeLanguage;
    private String preferredLanguage;
    private Set<String> roles;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    public static UserResponse fromUser(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .nativeLanguage(user.getNativeLanguage())
                .preferredLanguage(user.getPreferredLanguage())
                .roles(user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()))
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
