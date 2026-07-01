package com.hcmute.bmcsdl.nhom3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private String eventTimestamp;
    private String dbUsername;
    private String osUsername;
    private String terminalName;
    private String actionName;
    private String objectSchema;
    private String objectName;
    private String returnCode;
    private String sqlText;
    /** "UNIFIED" or "FGA" */
    private String auditType;
    private String sqlBinds;
}
