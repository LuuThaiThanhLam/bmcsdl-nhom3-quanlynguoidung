package com.hcmute.bmcsdl.nhom3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRoleDTO {
    private String role;
    private String passwordRequired;
    private String common;
    private String oracleMaintained;
    
    // For privileges
    private List<String> sysPrivileges;
    private List<String> tabPrivileges;
    private List<String> rolePrivileges;
    
    private String privilegeToGrant;
    private String privilegeToRevoke;
    private String objectToGrant;
    private String roleToGrant;
}
