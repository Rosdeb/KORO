package com.koro.app.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateRequest {
    private String name;
    private String profileImage;
    private String nativeLanguage;
    private String preferredLanguage;
}
