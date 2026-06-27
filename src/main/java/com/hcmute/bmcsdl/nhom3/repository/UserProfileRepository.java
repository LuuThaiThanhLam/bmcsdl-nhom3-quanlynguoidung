package com.hcmute.bmcsdl.nhom3.repository;

import com.hcmute.bmcsdl.nhom3.config.DatabaseConfig;
import com.hcmute.bmcsdl.nhom3.dto.DirectoryEntryDTO;
import com.hcmute.bmcsdl.nhom3.dto.UserProfileDTO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Truy van bang APP_TABLE.USER_PROFILE duoi danh nghia chinh user dang dang nhap.
 * Nho do VPD (file 02) va OLS (file 03) tu dong loc dong theo SESSION_USER,
 * ung dung khong can tu them dieu kien WHERE.
 */
@Repository
public class UserProfileRepository {

    private static final String OWNER = "APP_TABLE";
    private static final String TABLE = "USER_PROFILE";
    private static final String BASIC_VIEW = "VIEW_USER_BASIC_INFO";
    private static final Pattern ORACLE_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_$#]{0,127}");

    /** Lay cac ho so ma user dang nhap duoc phep nhin thay (VPD/OLS quyet dinh). */
    public List<UserProfileDTO> findMyProfiles(String username, String password) {
        String sql = """
                SELECT USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL,
                       DEPARTMENT, ROLE_LEVEL, USERNAME, CREATED_AT, UPDATED_AT
                FROM %s.%s
                ORDER BY USER_ID
                """.formatted(OWNER, TABLE);

        return jdbc(username, password).query(sql, (rs, rowNum) -> mapProfile(rs, false));
    }

    /**
     * Giong findMyProfiles nhung lay them nhan OLS cua tung dong (file 03).
     * Neu OLS chua kich hoat (cot OLS_LABEL chua ton tai) se nem loi -> service fallback.
     */
    public List<UserProfileDTO> findMyProfilesWithOls(String username, String password) {
        String sql = """
                SELECT USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL,
                       DEPARTMENT, ROLE_LEVEL, USERNAME, CREATED_AT, UPDATED_AT,
                       LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
                FROM %s.%s
                ORDER BY USER_ID
                """.formatted(OWNER, TABLE);

        return jdbc(username, password).query(sql, (rs, rowNum) -> mapProfile(rs, true));
    }

    /**
     * Nhan READ cua chinh phien user dang dang nhap (OLS gan tai luc logon).
     * Vi du: 'PUB::ALL,SALES,HR,IT'. Nem loi neu OLS chua kich hoat.
     */
    public String findMySessionReadLabel(String username, String password) {
        String sql = "SELECT SA_SESSION.READ_LABEL('USER_PROFILE_OLS') FROM DUAL";
        return jdbc(username, password).queryForObject(sql, String.class);
    }

    /** Lay 1 ho so theo USER_ID (van bi VPD loc, neu khong thuoc quyen se rong). */
    public Optional<UserProfileDTO> findMyProfileById(String username, String password, long userId) {
        String sql = """
                SELECT USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL,
                       DEPARTMENT, ROLE_LEVEL, USERNAME, CREATED_AT, UPDATED_AT
                FROM %s.%s
                WHERE USER_ID = ?
                """.formatted(OWNER, TABLE);

        try {
            return Optional.ofNullable(
                    jdbc(username, password).queryForObject(sql, (rs, rowNum) -> mapProfile(rs, false), userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Cap nhat cac cot ma APP_ROLE_USER duoc GRANT UPDATE (file 02):
     * FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL.
     * VPD chi cho cap nhat dong thuoc ve user dang nhap; dong khac -> 0 rows.
     */
    public int updateMyProfile(String username, String password, UserProfileDTO profile) {
        String sql = """
                UPDATE %s.%s
                SET FULL_NAME = ?, ADDRESS = ?, PHONE_NUMBER = ?, EMAIL = ?
                WHERE USER_ID = ?
                """.formatted(OWNER, TABLE);

        return jdbc(username, password).update(sql,
                profile.getFullName(),
                profile.getAddress(),
                profile.getPhoneNumber(),
                profile.getEmail(),
                profile.getUserId());
    }

    /** Danh ba co ban theo RBAC (view chi lo FULL_NAME, DEPARTMENT) - file 01. */
    public List<DirectoryEntryDTO> findDirectory(String username, String password) {
        String sql = """
                SELECT FULL_NAME, DEPARTMENT
                FROM %s.%s
                ORDER BY FULL_NAME
                """.formatted(OWNER, BASIC_VIEW);

        return jdbc(username, password).query(sql,
                (rs, rowNum) -> new DirectoryEntryDTO(rs.getString("FULL_NAME"), rs.getString("DEPARTMENT")));
    }

    private JdbcTemplate jdbc(String username, String password) {
        return new JdbcTemplate(DatabaseConfig.createDataSource(normalizeIdentifier(username), password));
    }

    private UserProfileDTO mapProfile(ResultSet rs, boolean withOls) throws SQLException {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setUserId(rs.getLong("USER_ID"));
        dto.setFullName(rs.getString("FULL_NAME"));
        dto.setAddress(rs.getString("ADDRESS"));
        dto.setPhoneNumber(rs.getString("PHONE_NUMBER"));
        dto.setEmail(rs.getString("EMAIL"));
        dto.setDepartment(rs.getString("DEPARTMENT"));
        int roleLevel = rs.getInt("ROLE_LEVEL");
        dto.setRoleLevel(rs.wasNull() ? null : roleLevel);
        dto.setUsername(rs.getString("USERNAME"));
        dto.setCreatedAt(rs.getTimestamp("CREATED_AT"));
        dto.setUpdatedAt(rs.getTimestamp("UPDATED_AT"));
        if (withOls) {
            dto.setOlsLabel(rs.getString("OLS_LABEL_TEXT"));
        }
        return dto;
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Identifier khong duoc de trong");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ORACLE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Identifier Oracle khong hop le: " + value);
        }
        return normalized;
    }
}
