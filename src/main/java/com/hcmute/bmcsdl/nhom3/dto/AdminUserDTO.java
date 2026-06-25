package com.hcmute.bmcsdl.nhom3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDTO {
    private String username;
    private String password;
    private String newPassword;

    private String accountStatus;
    private String defaultTablespace;
    private String temporaryTablespace;
    private String quota;
    private String quotaTablespace;
    private String profile;

    private String grantedRole;
    private String roleToGrant;
    private String roleToRevoke;
    private List<String> roles;

    private Date created;
    private Date expiryDate;
    private Date lockDate;

    public boolean isLocked() {
        return accountStatus != null && accountStatus.contains("LOCKED");
    }
}