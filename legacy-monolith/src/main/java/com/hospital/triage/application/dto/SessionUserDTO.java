package com.hospital.triage.application.dto;

import com.hospital.triage.domain.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionUserDTO {
    private UUID id;
    private String username;
    private String displayName;
    private UserRole role;
    private boolean active;
}
