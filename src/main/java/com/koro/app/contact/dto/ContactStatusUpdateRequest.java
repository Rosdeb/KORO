package com.koro.app.contact.dto;

import com.koro.app.contact.entity.ContactStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private ContactStatus status;
}
