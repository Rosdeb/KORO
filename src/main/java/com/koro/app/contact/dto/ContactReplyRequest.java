package com.koro.app.contact.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactReplyRequest {

    @Size(max = 200, message = "Subject must be under 200 characters")
    private String subject;

    @NotBlank(message = "Reply message cannot be empty")
    @Size(min = 2, max = 10000, message = "Reply message must be between 2 and 10000 characters")
    private String message;
}
