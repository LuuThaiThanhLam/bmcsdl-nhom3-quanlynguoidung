package com.hcmute.bmcsdl.nhom3.repository;

import com.hcmute.bmcsdl.nhom3.config.DatabaseConfig;
import com.hcmute.bmcsdl.nhom3.dto.AdminRoleDTO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
public class RoleManagermentRepository {

    private static final Pattern ORACLE_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_$#]{0,127}");
    private static final int MAX_PASSWORD_LENGTH = 128;

    public List<AdminRoleDTO> findAllRoles(String adminUsername, String adminPassword) {
        String sql = """
                SELECT ROLE, PASSWORD_REQUIRED, COMMON, ORACLE_MAINTAINED
                FROM DBA_ROLES
                WHERE ROLE LIKE 'APP\\_ROLE\\_%' ESCAPE '\\'
                ORDER BY ROLE
                """;

        return jdbc(adminUsername, adminPassword).query(sql, (rs, rowNum) -> mapRole(rs));
    }

    public Optional<AdminRoleDTO> findRoleByName(String adminUsername, String adminPassword, String roleName) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String sql = """
                SELECT ROLE, PASSWORD_REQUIRED, COMMON, ORACLE_MAINTAINED
                FROM DBA_ROLES
                WHERE ROLE = ?
                """;

        try {
            return Optional.ofNullable(jdbc(adminUsername, adminPassword)
                    .queryForObject(sql, (rs, rowNum) -> mapRole(rs), normalizedRoleName));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Map<String, Object>> findSysPrivileges(String adminUsername, String adminPassword, String roleName) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String sql = """
                SELECT PRIVILEGE, ADMIN_OPTION
                FROM DBA_SYS_PRIVS
                WHERE GRANTEE = ?
                ORDER BY PRIVILEGE
                """;

        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizedRoleName);
    }

    public List<Map<String, Object>> findTabPrivileges(String adminUsername, String adminPassword, String roleName) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String sql = """
                SELECT OWNER, TABLE_NAME AS OBJECT_NAME, PRIVILEGE, GRANTABLE
                FROM DBA_TAB_PRIVS
                WHERE GRANTEE = ?
                ORDER BY TABLE_NAME, PRIVILEGE
                """;

        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizedRoleName);
    }

    public List<Map<String, Object>> findColPrivileges(String adminUsername, String adminPassword, String roleName) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String sql = """
                SELECT OWNER, TABLE_NAME, COLUMN_NAME, PRIVILEGE, GRANTOR
                FROM DBA_COL_PRIVS
                WHERE GRANTEE = ?
                ORDER BY TABLE_NAME, COLUMN_NAME, PRIVILEGE
                """;

        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizedRoleName);
    }

    public List<Map<String, Object>> findRolePrivileges(String adminUsername, String adminPassword, String roleName) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String sql = """
                SELECT GRANTED_ROLE AS ROLE, ADMIN_OPTION
                FROM DBA_ROLE_PRIVS
                WHERE GRANTEE = ?
                ORDER BY GRANTED_ROLE
                """;

        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizedRoleName);
    }

    public List<Map<String, Object>> findGrantees(String adminUsername, String adminPassword, String roleName) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String sql = """
                SELECT GRANTEE, ADMIN_OPTION, DEFAULT_ROLE
                FROM DBA_ROLE_PRIVS
                WHERE GRANTED_ROLE = ?
                ORDER BY GRANTEE
                """;

        return jdbc(adminUsername, adminPassword).queryForList(sql, normalizedRoleName);
    }

    public void createRole(String adminUsername, String adminPassword, String roleName, String password) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String sql = "CREATE ROLE " + normalizedRoleName;

        if (password != null && !password.isBlank()) {
            sql += " IDENTIFIED BY " + quotePassword(password);
        }

        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void deleteRole(String adminUsername, String adminPassword, String roleName) {
        String normalizedRoleName = normalizeAppRole(roleName);
        jdbc(adminUsername, adminPassword).execute("DROP ROLE %s".formatted(normalizedRoleName));
    }

    public void updateRolePassword(String adminUsername, String adminPassword, String roleName, String password) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String sql = "ALTER ROLE " + normalizedRoleName;

        if (password != null && !password.isBlank()) {
            sql += " IDENTIFIED BY " + quotePassword(password);
        } else {
            sql += " NOT IDENTIFIED";
        }

        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void grantSysPrivilege(String adminUsername, String adminPassword, String roleName, String privilege, boolean withAdminOption) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String sql = "GRANT %s TO %s".formatted(privilege, normalizedRoleName);
        if (withAdminOption) {
            sql += " WITH ADMIN OPTION";
        }
        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void revokeSysPrivilege(String adminUsername, String adminPassword, String roleName, String privilege) {
        String normalizedRoleName = normalizeAppRole(roleName);
        jdbc(adminUsername, adminPassword).execute("REVOKE %s FROM %s".formatted(privilege, normalizedRoleName));
    }

    public void grantRoleToRole(String adminUsername, String adminPassword, String granteeRole, String grantedRole, boolean withAdminOption) {
        String normalizedGrantee = normalizeAppRole(granteeRole);
        String normalizedGranted = normalizeAppRole(grantedRole);
        String sql = "GRANT %s TO %s".formatted(normalizedGranted, normalizedGrantee);
        if (withAdminOption) {
            sql += " WITH ADMIN OPTION";
        }
        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void revokeRoleFromRole(String adminUsername, String adminPassword, String granteeRole, String grantedRole) {
        String normalizedGrantee = normalizeAppRole(granteeRole);
        String normalizedGranted = normalizeAppRole(grantedRole);
        jdbc(adminUsername, adminPassword).execute("REVOKE %s FROM %s".formatted(normalizedGranted, normalizedGrantee));
    }

    public void grantRoleToUser(String adminUsername, String adminPassword, String granteeUser, String grantedRole, boolean withAdminOption) {
        String normalizedGrantee = normalizeAppUser(granteeUser);
        String normalizedGranted = normalizeAppRole(grantedRole);
        String sql = "GRANT %s TO %s".formatted(normalizedGranted, normalizedGrantee);
        if (withAdminOption) {
            sql += " WITH ADMIN OPTION";
        }
        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void revokeRoleFromUser(String adminUsername, String adminPassword, String granteeUser, String grantedRole) {
        String normalizedGrantee = normalizeAppUser(granteeUser);
        String normalizedGranted = normalizeAppRole(grantedRole);
        jdbc(adminUsername, adminPassword).execute("REVOKE %s FROM %s".formatted(normalizedGranted, normalizedGrantee));
    }

    public void grantTabPrivilege(String adminUsername, String adminPassword, String roleName, String owner, String tableName, List<String> privileges) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String normalizedOwner = normalizeIdentifier(owner);
        String normalizedTable = normalizeIdentifier(tableName);
        List<String> normalizedPrivs = privileges.stream().map(this::normalizeIdentifier).toList();
        String privsJoined = String.join(", ", normalizedPrivs);
        jdbc(adminUsername, adminPassword).execute("GRANT %s ON %s.%s TO %s".formatted(privsJoined, normalizedOwner, normalizedTable, normalizedRoleName));
    }

    public void revokeTabPrivilege(String adminUsername, String adminPassword, String roleName, String owner, String tableName, String privilege) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String normalizedOwner = normalizeIdentifier(owner);
        String normalizedTable = normalizeIdentifier(tableName);
        jdbc(adminUsername, adminPassword).execute("REVOKE %s ON %s.%s FROM %s".formatted(privilege, normalizedOwner, normalizedTable, normalizedRoleName));
    }

    public void grantColPrivilege(String adminUsername, String adminPassword, String roleName, String owner, String tableName, List<String> columnNames, String privilege) {
        String normalizedRoleName = normalizeAppRole(roleName);
        String normalizedOwner = normalizeIdentifier(owner);
        String normalizedTable = normalizeIdentifier(tableName);
        List<String> normalizedColumns = columnNames.stream().map(this::normalizeIdentifier).toList();
        String colsJoined = String.join(", ", normalizedColumns);
        jdbc(adminUsername, adminPassword).execute("GRANT %s(%s) ON %s.%s TO %s".formatted(privilege, colsJoined, normalizedOwner, normalizedTable, normalizedRoleName));
    }

    public void revokeColPrivilege(String adminUsername, String adminPassword, String roleName, String owner, String tableName, String privilege) {
        // In Oracle, you cannot revoke a column-specific privilege. You have to revoke the object privilege entirely.
        // However, we will provide a standard REVOKE statement, which usually requires revoking the whole privilege on the table.
        // For simplicity and matching standard UI, we'll execute the table revoke.
        revokeTabPrivilege(adminUsername, adminPassword, roleName, owner, tableName, privilege);
    }

    public List<String> getTablesByOwner(String adminUsername, String adminPassword, String owner) {
        String sql = """
                SELECT OBJECT_NAME || ' (' || OBJECT_TYPE || ')'
                FROM DBA_OBJECTS
                WHERE OWNER = ?
                  AND OBJECT_TYPE IN ('TABLE', 'VIEW', 'PROCEDURE', 'FUNCTION', 'PACKAGE', 'SEQUENCE')
                ORDER BY OBJECT_NAME
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql, String.class, owner);
    }

    public List<String> getAllAppTables(String adminUsername, String adminPassword) {
        String sql = """
                SELECT OWNER || '.' || OBJECT_NAME
                FROM DBA_OBJECTS
                WHERE OWNER LIKE 'APP\\_%' ESCAPE '\\'
                  AND OBJECT_TYPE IN ('TABLE', 'VIEW')
                ORDER BY OWNER, OBJECT_NAME
                """;
        return jdbc(adminUsername, adminPassword).queryForList(sql, String.class);
    }

    public List<String> getColumnsByTable(String adminUsername, String adminPassword, String owner, String tableName) {
        String sql = "SELECT COLUMN_NAME || ' (' || DATA_TYPE || ')' FROM DBA_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ? ORDER BY COLUMN_ID";
        return jdbc(adminUsername, adminPassword).queryForList(sql, String.class, owner, tableName);
    }

    private JdbcTemplate jdbc(String username, String password) {
        return new JdbcTemplate(DatabaseConfig.createDataSource(normalizeIdentifier(username), password));
    }

    private AdminRoleDTO mapRole(ResultSet rs) throws SQLException {
        AdminRoleDTO roleDTO = new AdminRoleDTO();
        roleDTO.setRole(rs.getString("ROLE"));
        roleDTO.setPasswordRequired(rs.getString("PASSWORD_REQUIRED"));
        roleDTO.setCommon(rs.getString("COMMON"));
        roleDTO.setOracleMaintained(rs.getString("ORACLE_MAINTAINED"));
        return roleDTO;
    }

    private String normalizeAppRole(String value) {
        String role = normalizeIdentifier(value);
        if (!role.startsWith("APP_ROLE_")) {
            throw new IllegalArgumentException("Role phai co tien to APP_ROLE_");
        }
        return role;
    }

    private String normalizeAppUser(String value) {
        String user = normalizeIdentifier(value);
        if (!user.startsWith("APP_USER_")) {
            throw new IllegalArgumentException("User phai co tien to APP_USER_");
        }
        return user;
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
        if (password.length() > MAX_PASSWORD_LENGTH || password.contains("\"") || password.contains("\n")
                || password.contains("\r")) {
            throw new IllegalArgumentException("Mat khau Oracle khong hop le");
        }
        return "\"" + password + "\"";
    }
}
