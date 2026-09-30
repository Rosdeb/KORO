package com.koro.app.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyEmailRequest {
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank @Pattern(regexp = "[0-9]{6}")
    private String otp;
}
