package com.hcmute.bmcsdl.nhom3.repository;

import com.hcmute.bmcsdl.nhom3.config.DatabaseConfig;
import com.hcmute.bmcsdl.nhom3.dto.ManagerProfileDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Repository cho Manager (Profile Manager).
 *
 * NGUYEN TAC BAO MAT: moi cau lenh chay bang connection cua CHINH MANAGER
 * (truyen username/password tu session) -> VPD/OLS/object-privilege phat huy.
 *
 * Phan 1: thao tac bang APP_TABLE.USER_PROFILE (xem/sua/xoa + nang/ha nhan OLS).
 * Phan 2: lay thong tin "cua chinh minh" (account/role/profile/ols) qua view USER_*.
 */
@Repository
public class ManagerProfileRepository {

    private JdbcTemplate jdbc(String username, String password) {
        return new JdbcTemplate(DatabaseConfig.createDataSource(username, password));
    }

    // ========================================================================
    // PHAN 1: USER_PROFILE (cung phong ban - VPD tu loc)
    // ========================================================================

    /**
     * Lay danh sach ho so user hien tai duoc phep xem (VPD loc theo phong ban,
     * OLS loc theo nhan, column-mask che EMAIL/PHONE nguoi khac).
     * Thu query co cot nhan OLS truoc; neu cot chua ton tai thi query khong co.
     */
    public List<ManagerProfileDTO> findVisibleProfiles(String username, String password) {
        String sqlWithLabel = """
                SELECT USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL,
                       DEPARTMENT, ROLE_LEVEL, USERNAME,
                       LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
                FROM   APP_TABLE.USER_PROFILE
                ORDER  BY USER_ID
                """;
        try {
            return jdbc(username, password).query(sqlWithLabel, (rs, i) -> mapRow(rs, true));
        } catch (Exception e) {
            String sqlNoLabel = """
                    SELECT USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL,
                           DEPARTMENT, ROLE_LEVEL, USERNAME
                    FROM   APP_TABLE.USER_PROFILE
                    ORDER  BY USER_ID
                    """;
            return jdbc(username, password).query(sqlNoLabel, (rs, i) -> mapRow(rs, false));
        }
    }

    public ManagerProfileDTO findById(String username, String password, long userId) {
        String sql = """
                SELECT USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL,
                       DEPARTMENT, ROLE_LEVEL, USERNAME
                FROM   APP_TABLE.USER_PROFILE
                WHERE  USER_ID = ?
                """;
        List<ManagerProfileDTO> list = jdbc(username, password).query(sql, (rs, i) -> mapRow(rs, false), userId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Cap nhat 4 cot co ban (RBAC chi grant 4 cot nay). VPD DML chi cho phong ban cua minh. */
    public int updateBasicInfo(String username, String password, ManagerProfileDTO dto) {
        String sql = """
                UPDATE APP_TABLE.USER_PROFILE
                SET    FULL_NAME = ?, ADDRESS = ?, PHONE_NUMBER = ?, EMAIL = ?
                WHERE  USER_ID = ?
                """;
        return jdbc(username, password).update(sql,
                dto.getFullName(), dto.getAddress(), dto.getPhoneNumber(), dto.getEmail(), dto.getUserId());
    }

    public int deleteProfile(String username, String password, long userId) {
        String sql = "DELETE FROM APP_TABLE.USER_PROFILE WHERE USER_ID = ?";
        return jdbc(username, password).update(sql, userId);
    }

    public void upgradeLabel(String username, String password, long userId) {
        jdbc(username, password).update("BEGIN APP_TABLE.UPGRADE_PROFILE_LABEL(?); END;", userId);
    }

    public void downgradeLabel(String username, String password, long userId) {
        jdbc(username, password).update("BEGIN APP_TABLE.DOWNGRADE_PROFILE_LABEL(?); END;", userId);
    }

    private ManagerProfileDTO mapRow(java.sql.ResultSet rs, boolean withLabel) throws java.sql.SQLException {
        ManagerProfileDTO dto = new ManagerProfileDTO();
        dto.setUserId(rs.getLong("USER_ID"));
        dto.setFullName(rs.getString("FULL_NAME"));
        dto.setAddress(rs.getString("ADDRESS"));
        dto.setPhoneNumber(rs.getString("PHONE_NUMBER"));
        dto.setEmail(rs.getString("EMAIL"));
        dto.setDepartment(rs.getString("DEPARTMENT"));
        int lvl = rs.getInt("ROLE_LEVEL");
        dto.setRoleLevel(rs.wasNull() ? null : lvl);
        dto.setUsername(rs.getString("USERNAME"));
        if (withLabel) {
            dto.setOlsLabelText(rs.getString("OLS_LABEL_TEXT"));
        }
        return dto;
    }

    // ========================================================================
    // PHAN 2: THONG TIN CUA CHINH MANAGER (read-only, qua view USER_*)
    // ========================================================================

    /** Phong ban cua manager (tu bang phu USER_DEPT_MAP). */
    public String findMyDepartment(String username, String password) {
        try {
            return jdbc(username, password).queryForObject(
                    "SELECT DEPARTMENT FROM APP_TABLE.USER_DEPT_MAP WHERE USERNAME = USER", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Thong tin tai khoan cua chinh minh (USER_USERS - moi user tu xem duoc). */
    public Map<String, Object> findMyAccount(String username, String password) {
        String sql = """
                SELECT USERNAME, ACCOUNT_STATUS, DEFAULT_TABLESPACE, TEMPORARY_TABLESPACE,
                       CREATED, EXPIRY_DATE, LOCK_DATE
                FROM   USER_USERS
                """;
        List<Map<String, Object>> list = jdbc(username, password).queryForList(sql);
        return list.isEmpty() ? Map.of() : list.get(0);
    }

    /** Role dang enable trong session hien tai. */
    public List<String> findMySessionRoles(String username, String password) {
        return jdbc(username, password).queryForList("SELECT ROLE FROM SESSION_ROLES ORDER BY ROLE", String.class);
    }

    /** Role duoc cap cho chinh minh (USER_ROLE_PRIVS). */
    public List<Map<String, Object>> findMyRolePrivs(String username, String password) {
        String sql = """
                SELECT GRANTED_ROLE, ADMIN_OPTION, DEFAULT_ROLE
                FROM   USER_ROLE_PRIVS
                ORDER  BY GRANTED_ROLE
                """;
        return jdbc(username, password).queryForList(sql);
    }

    /** System privilege cap truc tiep cho minh (USER_SYS_PRIVS). */
    public List<Map<String, Object>> findMySysPrivs(String username, String password) {
        String sql = "SELECT PRIVILEGE, ADMIN_OPTION FROM USER_SYS_PRIVS ORDER BY PRIVILEGE";
        return jdbc(username, password).queryForList(sql);
    }

    /** Object privilege minh duoc nhan (USER_TAB_PRIVS_RECD). */
    public List<Map<String, Object>> findMyTabPrivs(String username, String password) {
        String sql = """
                SELECT OWNER, TABLE_NAME, GRANTOR, PRIVILEGE, GRANTABLE
                FROM   USER_TAB_PRIVS_RECD
                ORDER  BY OWNER, TABLE_NAME, PRIVILEGE
                """;
        return jdbc(username, password).queryForList(sql);
    }

    /** Column privilege minh duoc nhan (USER_COL_PRIVS_RECD). */
    public List<Map<String, Object>> findMyColPrivs(String username, String password) {
        String sql = """
                SELECT OWNER, TABLE_NAME, COLUMN_NAME, GRANTOR, PRIVILEGE, GRANTABLE
                FROM   USER_COL_PRIVS_RECD
                ORDER  BY OWNER, TABLE_NAME, COLUMN_NAME, PRIVILEGE
                """;
        return jdbc(username, password).queryForList(sql);
    }

    /** Gioi han profile cua minh (USER_RESOURCE_LIMITS) - loc 3 resource chinh. */
    public List<Map<String, Object>> findMyProfileLimits(String username, String password) {
        String sql = """
                SELECT RESOURCE_NAME, LIMIT
                FROM   USER_RESOURCE_LIMITS
                WHERE  RESOURCE_NAME IN ('SESSIONS_PER_USER','CONNECT_TIME','IDLE_TIME',
                                         'FAILED_LOGIN_ATTEMPTS','PASSWORD_LIFE_TIME')
                ORDER  BY RESOURCE_NAME
                """;
        return jdbc(username, password).queryForList(sql);
    }

    /** Nhan OLS cua chinh minh (qua view LBACSYS.V_MY_OLS_LABEL). Co the rong neu chua co OLS. */
    public Map<String, Object> findMyOlsLabel(String username, String password) {
        try {
            String sql = """
                    SELECT USER_NAME, POLICY_NAME, USER_PRIVILEGES,
                           MAX_READ_LABEL, MAX_WRITE_LABEL, MIN_WRITE_LABEL,
                           DEFAULT_READ_LABEL, DEFAULT_ROW_LABEL
                    FROM   LBACSYS.V_MY_OLS_LABEL
                    """;
            List<Map<String, Object>> list = jdbc(username, password).queryForList(sql);
            return list.isEmpty() ? Map.of() : list.get(0);
        } catch (Exception e) {
            return Map.of();  // chua chay patch OLS hoac chua co view
        }
    }
}
