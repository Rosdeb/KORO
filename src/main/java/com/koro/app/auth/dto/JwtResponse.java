package com.koro.app.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class JwtResponse {
    private TokenInfo token;
    private String id;
    private String name;
    private String email;
    private List<String> roles;

    public JwtResponse(String accessToken, String refreshToken, String id, String name, String email, List<String> roles) {
        this.token = new TokenInfo(accessToken, refreshToken);
        this.id = id;
        this.name = name;
        this.email = email;
        this.roles = roles;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class TokenInfo {
        private String access_token;
        private String refresh_token;
    }
}
