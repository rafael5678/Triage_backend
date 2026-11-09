/** UpdateUserRequest: application type for the triage hospital services. */
package com.hospital.triage.common.dto;

import com.hospital.triage.common.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    private String username;
    private String password;
    private String displayName;
    private UserRole role;
    private Boolean active;
}
