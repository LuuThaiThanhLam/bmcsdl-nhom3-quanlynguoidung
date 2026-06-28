package com.hcmute.bmcsdl.nhom3.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Gom thong tin "cua chinh Manager" de hien thi cac trang xem (read-only):
 *  - account: thong tin tai khoan Oracle cua minh (USER_USERS)
 *  - roles: danh sach role cua minh (SESSION_ROLES / USER_ROLE_PRIVS)
 *  - sysPrivs / tabPrivs / colPrivs: cac quyen cua minh
 *  - profileLimits: gioi han profile (USER_RESOURCE_LIMITS)
 *  - olsLabel: nhan OLS cua minh (qua view LBACSYS.V_MY_OLS_LABEL)
 *
 * Dung Map cho linh hoat (khong can tao nhieu DTO nho).
 */
@Data
public class ManagerSelfInfoDTO {
    private Map<String, Object> account;            // 1 dong USER_USERS
    private List<String> sessionRoles;              // role dang enable trong session
    private List<Map<String, Object>> rolePrivs;    // USER_ROLE_PRIVS
    private List<Map<String, Object>> sysPrivs;     // USER_SYS_PRIVS
    private List<Map<String, Object>> tabPrivs;     // USER_TAB_PRIVS_RECD
    private List<Map<String, Object>> colPrivs;     // USER_COL_PRIVS_RECD
    private List<Map<String, Object>> profileLimits;// USER_RESOURCE_LIMITS (loc 3 resource chinh)
    private Map<String, Object> olsLabel;           // 1 dong V_MY_OLS_LABEL (co the null neu chua co OLS)
    private String department;                      // phong ban cua manager (tu USER_DEPT_MAP)
}
