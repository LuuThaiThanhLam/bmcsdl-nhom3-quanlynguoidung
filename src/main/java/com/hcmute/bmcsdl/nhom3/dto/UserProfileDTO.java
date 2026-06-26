package com.hcmute.bmcsdl.nhom3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private Long userId;
    private String fullName;
    private String address;
    private String phoneNumber;
    private String email;
    private String department;
    private Integer roleLevel;
    private String username;
    private Date createdAt;
    private Date updatedAt;
}
