package com.koro.app.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest extends VerifyEmailRequest {
    @NotBlank @Size(min = 6, max = 40)
    private String newPassword;
}
