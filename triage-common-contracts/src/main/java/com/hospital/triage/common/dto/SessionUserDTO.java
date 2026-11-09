package com.hospital.triage.common.dto;

import com.hospital.triage.common.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionUserDTO {
    private UUID id;
    private String username;
    private String displayName;
    private UserRole role;
    private boolean active;
    private String password;
}
