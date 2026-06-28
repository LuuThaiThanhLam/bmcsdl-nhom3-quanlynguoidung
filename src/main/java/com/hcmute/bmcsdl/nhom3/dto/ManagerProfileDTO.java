package com.hcmute.bmcsdl.nhom3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO cho 1 dong trong bang APP_TABLE.USER_PROFILE.
 * Dung cho man hinh Manager (Profile Manager) quan ly ho so nhan vien.
 *
 * Luu y bao mat:
 *  - Khi Manager (APP_MANAGER_PROFILE) SELECT bang nay, Oracle VPD se tu dong
 *    them dieu kien WHERE DEPARTMENT = 'HR' -> Manager chi thay duoc ho so phong HR.
 *  - olsLabelText la nhan OLS (PUB::HR / PRI::HR...) chuyen sang chuoi de hien thi.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManagerProfileDTO {
    private Long userId;
    private String fullName;
    private String address;
    private String phoneNumber;
    private String email;
    private String department;
    private Integer roleLevel;
    private String username;     // username Oracle gan voi ho so (dung cho VPD)
    private String olsLabelText; // nhan OLS dang chuoi, vd PUB::HR / PRI::HR
}
