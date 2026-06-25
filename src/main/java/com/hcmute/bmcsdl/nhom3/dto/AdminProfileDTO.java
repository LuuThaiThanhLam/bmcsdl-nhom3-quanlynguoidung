package com.hcmute.bmcsdl.nhom3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileDTO {
    private String profileName;
    private String sessionsPerUser;
    private String connectTime;
    private String idleTime;
}
