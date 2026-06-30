package com.hcmute.bmcsdl.nhom3.repository;

import com.hcmute.bmcsdl.nhom3.config.DatabaseConfig;
import com.hcmute.bmcsdl.nhom3.dto.AdminUserDTO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
public class UserManagermentRepository {

    private static final Pattern ORACLE_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_$#]{0,127}");
    private static final int MAX_PASSWORD_LENGTH = 128;

    public List<AdminUserDTO> findAllUsers(String adminUsername, String adminPassword) {
        String sql = """
                SELECT USERNAME, ACCOUNT_STATUS, DEFAULT_TABLESPACE, TEMPORARY_TABLESPACE,
                       PROFILE, CREATED, EXPIRY_DATE, LOCK_DATE
                FROM DBA_USERS
                WHERE USERNAME LIKE 'APP\\_%' ESCAPE '\\'
                ORDER BY USERNAME
                """;

        return jdbc(adminUsername, adminPassword).query(sql, (rs, rowNum) -> mapUser(rs));
    }

    public Optional<AdminUserDTO> findUserByUsername(String adminUsername, String adminPassword, String username) {
        // Dung read-only validation: cho phep xem ca APP_DBA_ADMIN
        String normalizedUsername = normalizeAppUsernameReadOnly(username);
        String sql = """
                SELECT USERNAME, ACCOUNT_STATUS, DEFAULT_TABLESPACE, TEMPORARY_TABLESPACE,
                       PROFILE, CREATED, EXPIRY_DATE, LOCK_DATE
                FROM DBA_USERS
                WHERE USERNAME = ?
                """;

        try {
            return Optional.ofNullable(jdbc(adminUsername, adminPassword)
                    .queryForObject(sql, (rs, rowNum) -> mapUser(rs), normalizedUsername));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<String> findUserRoles(String adminUsername, String adminPassword, String username) {
        String normalizedUsername = normalizeAppUsernameReadOnly(username);
        String sql = """
                SELECT GRANTED_ROLE
                FROM DBA_ROLE_PRIVS
                WHERE GRANTEE = ?
                ORDER BY GRANTED_ROLE
                """;

        return jdbc(adminUsername, adminPassword).queryForList(sql, String.class, normalizedUsername);
    }

    public List<String> findAppRoles(String adminUsername, String adminPassword) {
        String sql = """
                SELECT ROLE
                FROM DBA_ROLES
                WHERE ROLE LIKE 'APP\\_ROLE\\_%' ESCAPE '\\'
                ORDER BY ROLE
                """;

        return jdbc(adminUsername, adminPassword).queryForList(sql, String.class);
    }

    public List<String> findAppProfiles(String adminUsername, String adminPassword) {
        String sql = """
                SELECT DISTINCT PROFILE
                FROM DBA_PROFILES
                WHERE PROFILE LIKE 'APP\\_PROFILE\\_%' ESCAPE '\\'
                ORDER BY PROFILE
                """;

        return jdbc(adminUsername, adminPassword).queryForList(sql, String.class);
    }

    public void createUser(String adminUsername, String adminPassword, AdminUserDTO userDTO) {
        Objects.requireNonNull(userDTO, "userDTO must not be null");
        String username = normalizeAppUsername(userDTO.getUsername());
        String password = quotePassword(userDTO.getPassword());
        String defaultTablespace = normalizeIdentifierOrDefault(userDTO.getDefaultTablespace(), "TS_USERS");
        String temporaryTablespace = normalizeIdentifierOrDefault(userDTO.getTemporaryTablespace(), "TEMP");
        String quotaTablespace = normalizeIdentifierOrDefault(userDTO.getQuotaTablespace(), defaultTablespace);
        String profile = normalizeAppProfileOrDefault(userDTO.getProfile(), "APP_PROFILE_USER");
        String quota = sanitizeQuotaOrDefault(userDTO.getQuota(), "50M");

        jdbc(adminUsername, adminPassword).execute("""
                CREATE USER %s IDENTIFIED BY %s
                  DEFAULT TABLESPACE %s
                  TEMPORARY TABLESPACE %s
                  QUOTA %s ON %s
                  PROFILE %s
                """.formatted(username, password, defaultTablespace, temporaryTablespace,
                quota, quotaTablespace, profile));

        if (userDTO.getGrantedRole() != null && !userDTO.getGrantedRole().isBlank()) {
            grantRole(adminUsername, adminPassword, username, userDTO.getGrantedRole());
        }
    }

    public void updateUser(String adminUsername, String adminPassword, AdminUserDTO userDTO) {
        Objects.requireNonNull(userDTO, "userDTO must not be null");
        String username = normalizeAppUsername(userDTO.getUsername());
        JdbcTemplate jdbcTemplate = jdbc(adminUsername, adminPassword);
        String newPassword = firstNotBlank(userDTO.getNewPassword(), userDTO.getPassword());

        if (newPassword != null) {
            jdbcTemplate.execute("ALTER USER %s IDENTIFIED BY %s".formatted(username, quotePassword(newPassword)));
        }

        if (userDTO.getDefaultTablespace() != null && !userDTO.getDefaultTablespace().isBlank()) {
            jdbcTemplate.execute("ALTER USER %s DEFAULT TABLESPACE %s".formatted(
                    username, normalizeIdentifier(userDTO.getDefaultTablespace())));
        }

        if (userDTO.getTemporaryTablespace() != null && !userDTO.getTemporaryTablespace().isBlank()) {
            jdbcTemplate.execute("ALTER USER %s TEMPORARY TABLESPACE %s".formatted(
                    username, normalizeIdentifier(userDTO.getTemporaryTablespace())));
        }

        if (userDTO.getProfile() != null && !userDTO.getProfile().isBlank()) {
            jdbcTemplate.execute("ALTER USER %s PROFILE %s".formatted(username, normalizeAppProfile(userDTO.getProfile())));
        }
    }

    public void deleteUser(String adminUsername, String adminPassword, String username) {
        String normalizedUsername = normalizeAppUsername(username);
        jdbc(adminUsername, adminPassword).execute("DROP USER %s CASCADE".formatted(normalizedUsername));
    }

    public void lockUser(String adminUsername, String adminPassword, String username) {
        String normalizedUsername = normalizeAppUsername(username);
        jdbc(adminUsername, adminPassword).execute("ALTER USER %s ACCOUNT LOCK".formatted(normalizedUsername));
    }

    public void unlockUser(String adminUsername, String adminPassword, String username) {
        String normalizedUsername = normalizeAppUsername(username);
        jdbc(adminUsername, adminPassword).execute("ALTER USER %s ACCOUNT UNLOCK".formatted(normalizedUsername));
    }

    public void resetPassword(String adminUsername, String adminPassword, String username, String newPassword) {
        String normalizedUsername = normalizeAppUsername(username);
        jdbc(adminUsername, adminPassword).execute("ALTER USER %s IDENTIFIED BY %s".formatted(
                normalizedUsername, quotePassword(newPassword)));
    }

    public void grantRole(String adminUsername, String adminPassword, String username, String role) {
        grantRole(adminUsername, adminPassword, username, role, false);
    }

    public void grantRole(String adminUsername, String adminPassword, String username, String role, boolean adminOption) {
        String normalizedUsername = normalizeAppUsername(username);
        String normalizedRole = normalizeAppRole(role);
        String sql = "GRANT %s TO %s".formatted(normalizedRole, normalizedUsername);
        if (adminOption) {
            sql += " WITH ADMIN OPTION";
        }
        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void revokeRole(String adminUsername, String adminPassword, String username, String role) {
        String normalizedUsername = normalizeAppUsername(username);
        String normalizedRole = normalizeAppRole(role);
        jdbc(adminUsername, adminPassword).execute("REVOKE %s FROM %s".formatted(normalizedRole, normalizedUsername));
    }

    public void changeProfile(String adminUsername, String adminPassword, String username, String profile) {
        String normalizedUsername = normalizeAppUsername(username);
        String normalizedProfile = normalizeAppProfile(profile);
        jdbc(adminUsername, adminPassword).execute("ALTER USER %s PROFILE %s".formatted(normalizedUsername, normalizedProfile));
    }

    private JdbcTemplate jdbc(String username, String password) {
        return new JdbcTemplate(DatabaseConfig.createDataSource(normalizeIdentifier(username), password));
    }

    private AdminUserDTO mapUser(ResultSet rs) throws SQLException {
        AdminUserDTO userDTO = new AdminUserDTO();
        userDTO.setUsername(rs.getString("USERNAME"));
        userDTO.setAccountStatus(rs.getString("ACCOUNT_STATUS"));
        userDTO.setDefaultTablespace(rs.getString("DEFAULT_TABLESPACE"));
        userDTO.setTemporaryTablespace(rs.getString("TEMPORARY_TABLESPACE"));
        userDTO.setProfile(rs.getString("PROFILE"));
        userDTO.setCreated(rs.getTimestamp("CREATED"));
        userDTO.setExpiryDate(rs.getTimestamp("EXPIRY_DATE"));
        userDTO.setLockDate(rs.getTimestamp("LOCK_DATE"));
        return userDTO;
    }

    private String firstNotBlank(String firstValue, String secondValue) {
        if (firstValue != null && !firstValue.isBlank()) {
            return firstValue;
        }
        if (secondValue != null && !secondValue.isBlank()) {
            return secondValue;
        }
        return null;
    }

    /**
     * Validate username cho cac thao tac GHI (INSERT/UPDATE/DELETE).
     * Chan APP_DBA_ADMIN va APP_TABLE de bao ve user quan tri he thong.
     */
    private String normalizeAppUsername(String value) {
        String username = normalizeIdentifier(value);
        if (!username.startsWith("APP_")) {
            throw new IllegalArgumentException("Chi duoc thao tac voi user ung dung co tien to APP_");
        }
        if ("APP_DBA_ADMIN".equals(username) || "APP_TABLE".equals(username)) {
            throw new IllegalArgumentException("Khong the sua/xoa user quan tri he thong");
        }
        return username;
    }

    /**
     * Validate username cho cac thao tac DOC (SELECT).
     * Cho phep xem thong tin cua APP_DBA_ADMIN nhung van bat buoc tien to APP_.
     */
    private String normalizeAppUsernameReadOnly(String value) {
        String username = normalizeIdentifier(value);
        if (!username.startsWith("APP_")) {
            throw new IllegalArgumentException("Chi duoc xem user ung dung co tien to APP_");
        }
        return username;
    }

    private String normalizeAppRole(String value) {
        String role = normalizeIdentifier(value);
        if (!role.startsWith("APP_ROLE_")) {
            throw new IllegalArgumentException("Role phai co tien to APP_ROLE_");
        }
        return role;
    }

    private String normalizeAppProfile(String value) {
        String profile = normalizeIdentifier(value);
        if (!profile.startsWith("APP_PROFILE_")) {
            throw new IllegalArgumentException("Profile phai co tien to APP_PROFILE_");
        }
        return profile;
    }

    private String normalizeAppProfileOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return normalizeAppProfile(value);
    }

    private String normalizeIdentifierOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return normalizeIdentifier(value);
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

    private String quotePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Mat khau khong duoc de trong");
        }
        if (password.length() > MAX_PASSWORD_LENGTH || password.contains("\"") || password.contains("\n") || password.contains("\r")) {
            throw new IllegalArgumentException("Mat khau Oracle khong hop le");
        }
        return "\"" + password + "\"";
    }

    private String sanitizeQuotaOrDefault(String quota, String defaultValue) {
        if (quota == null || quota.isBlank()) {
            return defaultValue;
        }

        String normalized = quota.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if ("UNLIMITED".equals(normalized)) {
            return normalized;
        }
        if (normalized.matches("\\d+(K|KB|M|MB|G|GB)?")) {
            return normalized.replaceAll("B$", "");
        }
        throw new IllegalArgumentException("Quota khong hop le. Vi du: 50 MB, 1024K, 1G hoac UNLIMITED");
    }

    public List<String> findPermanentTablespaces(String adminUsername, String adminPassword) {
        String sql = "SELECT TABLESPACE_NAME FROM DBA_TABLESPACES WHERE CONTENTS = 'PERMANENT' ORDER BY TABLESPACE_NAME";
        return jdbc(adminUsername, adminPassword).queryForList(sql, String.class);
    }

    public List<String> findTemporaryTablespaces(String adminUsername, String adminPassword) {
        String sql = "SELECT TABLESPACE_NAME FROM DBA_TABLESPACES WHERE CONTENTS = 'TEMPORARY' ORDER BY TABLESPACE_NAME";
        return jdbc(adminUsername, adminPassword).queryForList(sql, String.class);
    }

    public List<java.util.Map<String, Object>> findUserQuotas(String adminUsername, String adminPassword, String username) {
        String sql = """
                SELECT TABLESPACE_NAME, BYTES, MAX_BYTES, BLOCKS, MAX_BLOCKS
                FROM DBA_TS_QUOTAS
                WHERE USERNAME = ?
                ORDER BY TABLESPACE_NAME
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizeAppUsernameReadOnly(username));
    }

    public List<java.util.Map<String, Object>> findUserRolesWithOptions(String adminUsername, String adminPassword, String username) {
        String sql = """
                SELECT GRANTED_ROLE, ADMIN_OPTION, DELEGATE_OPTION, DEFAULT_ROLE
                FROM DBA_ROLE_PRIVS
                WHERE GRANTEE = ?
                ORDER BY GRANTED_ROLE
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizeAppUsernameReadOnly(username));
    }

    public List<java.util.Map<String, Object>> findUserSysPrivs(String adminUsername, String adminPassword, String username) {
        String sql = """
                SELECT PRIVILEGE, ADMIN_OPTION
                FROM DBA_SYS_PRIVS
                WHERE GRANTEE = ?
                ORDER BY PRIVILEGE
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizeAppUsernameReadOnly(username));
    }

    public List<java.util.Map<String, Object>> findUserTabPrivs(String adminUsername, String adminPassword, String username) {
        String sql = """
                SELECT OWNER, TABLE_NAME, PRIVILEGE, GRANTABLE
                FROM DBA_TAB_PRIVS
                WHERE GRANTEE = ?
                  AND TABLE_NAME NOT LIKE 'BIN$%'
                ORDER BY OWNER, TABLE_NAME, PRIVILEGE
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizeAppUsernameReadOnly(username));
    }

    public List<java.util.Map<String, Object>> findUserColPrivs(String adminUsername, String adminPassword, String username) {
        String sql = """
                SELECT OWNER, TABLE_NAME, COLUMN_NAME, PRIVILEGE, GRANTABLE
                FROM DBA_COL_PRIVS
                WHERE GRANTEE = ?
                ORDER BY OWNER, TABLE_NAME, COLUMN_NAME, PRIVILEGE
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizeAppUsernameReadOnly(username));
    }

    public void setUserQuota(String adminUsername, String adminPassword, String username, String tablespace, String quota) {
        String normalizedUsername = normalizeAppUsername(username);
        String normalizedTablespace = normalizeIdentifier(tablespace);
        String sanitizedQuota = sanitizeQuotaOrDefault(quota, "UNLIMITED");
        jdbc(adminUsername, adminPassword).execute("ALTER USER %s QUOTA %s ON %s".formatted(
                normalizedUsername, sanitizedQuota, normalizedTablespace));
    }

    public void grantSysPriv(String adminUsername, String adminPassword, String username, String privilege, boolean adminOption) {
        String normalizedUsername = normalizeAppUsername(username);
        String cleanPriv = privilege.trim().toUpperCase(Locale.ROOT);
        String sql = "GRANT %s TO %s".formatted(cleanPriv, normalizedUsername);
        if (adminOption) {
            sql += " WITH ADMIN OPTION";
        }
        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void revokeSysPriv(String adminUsername, String adminPassword, String username, String privilege) {
        String normalizedUsername = normalizeAppUsername(username);
        String cleanPriv = privilege.trim().toUpperCase(Locale.ROOT);
        jdbc(adminUsername, adminPassword).execute("REVOKE %s FROM %s".formatted(cleanPriv, normalizedUsername));
    }

    public void grantObjPriv(String adminUsername, String adminPassword, String username, String owner, String tableName, List<String> privileges, boolean grantOption) {
        String normalizedUsername = normalizeAppUsername(username);
        String cleanOwner = normalizeIdentifier(owner);
        String cleanTable = normalizeIdentifier(tableName);
        List<String> cleanPrivs = privileges.stream().map(p -> p.trim().toUpperCase(Locale.ROOT)).toList();
        String privsJoined = String.join(", ", cleanPrivs);
        String sql = "GRANT %s ON %s.%s TO %s".formatted(privsJoined, cleanOwner, cleanTable, normalizedUsername);
        if (grantOption) {
            sql += " WITH GRANT OPTION";
        }
        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void revokeObjPriv(String adminUsername, String adminPassword, String username, String owner, String tableName, String privilege) {
        String normalizedUsername = normalizeAppUsername(username);
        String cleanOwner = normalizeIdentifier(owner);
        String cleanTable = normalizeIdentifier(tableName);
        String cleanPriv = privilege.trim().toUpperCase(Locale.ROOT);
        jdbc(adminUsername, adminPassword).execute("REVOKE %s ON %s.%s FROM %s".formatted(cleanPriv, cleanOwner, cleanTable, normalizedUsername));
    }

    public void grantColPriv(String adminUsername, String adminPassword, String username, String owner, String tableName, List<String> columnNames, String privilege) {
        String normalizedUsername = normalizeAppUsername(username);
        String cleanOwner = normalizeIdentifier(owner);
        String cleanTable = normalizeIdentifier(tableName);
        List<String> cleanCols = columnNames.stream().map(this::normalizeIdentifier).toList();
        String colsJoined = String.join(", ", cleanCols);
        String cleanPriv = privilege.trim().toUpperCase(Locale.ROOT);
        String sql = "GRANT %s(%s) ON %s.%s TO %s".formatted(cleanPriv, colsJoined, cleanOwner, cleanTable, normalizedUsername);
        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void revokeColPriv(String adminUsername, String adminPassword, String username, String owner, String tableName, String columnName, String privilege) {
        String normalizedUsername = normalizeAppUsername(username);
        String cleanOwner = normalizeIdentifier(owner);
        String cleanTable = normalizeIdentifier(tableName);
        String cleanColumn = normalizeIdentifier(columnName);
        String cleanPriv = privilege.trim().toUpperCase(Locale.ROOT);
        JdbcTemplate jdbcTemplate = jdbc(adminUsername, adminPassword);
        List<String> remainingColumns = findGrantedColumnsForPrivilege(jdbcTemplate, normalizedUsername, cleanOwner, cleanTable, cleanPriv).stream()
                .filter(column -> !column.equals(cleanColumn))
                .toList();

        jdbcTemplate.execute("REVOKE %s ON %s.%s FROM %s".formatted(cleanPriv, cleanOwner, cleanTable, normalizedUsername));
        if (!remainingColumns.isEmpty()) {
            jdbcTemplate.execute("GRANT %s(%s) ON %s.%s TO %s".formatted(
                    cleanPriv, String.join(", ", remainingColumns), cleanOwner, cleanTable, normalizedUsername));
        }
    }

    private List<String> findGrantedColumnsForPrivilege(JdbcTemplate jdbcTemplate, String grantee, String owner, String tableName, String privilege) {
        String sql = """
                SELECT DISTINCT COLUMN_NAME
                FROM DBA_COL_PRIVS
                WHERE GRANTEE = ?
                  AND OWNER = ?
                  AND TABLE_NAME = ?
                  AND PRIVILEGE = ?
                ORDER BY COLUMN_NAME
                """;
        return jdbcTemplate.queryForList(sql, String.class, grantee, owner, tableName, privilege);
    }

    public List<String> findAllSchemas(String adminUsername, String adminPassword) {
        String sql = """
                SELECT DISTINCT OWNER
                FROM DBA_TABLES
                WHERE OWNER LIKE 'APP\\_%' ESCAPE '\\'
                ORDER BY OWNER
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql, String.class);
    }

    public List<java.util.Map<String, Object>> findAllTables(String adminUsername, String adminPassword) {
        String sql = """
                SELECT OWNER, TABLE_NAME
                FROM DBA_TABLES
                WHERE OWNER LIKE 'APP\\_%' ESCAPE '\\'
                ORDER BY OWNER, TABLE_NAME
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql);
    }

    public List<java.util.Map<String, Object>> findAllColumns(String adminUsername, String adminPassword) {
        String sql = """
                SELECT OWNER, TABLE_NAME, COLUMN_NAME
                FROM DBA_TAB_COLS
                WHERE OWNER LIKE 'APP\\_%' ESCAPE '\\'
                ORDER BY OWNER, TABLE_NAME, COLUMN_NAME
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql);
    }
}
