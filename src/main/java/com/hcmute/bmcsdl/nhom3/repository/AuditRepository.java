package com.hcmute.bmcsdl.nhom3.repository;

import com.hcmute.bmcsdl.nhom3.config.DatabaseConfig;
import com.hcmute.bmcsdl.nhom3.dto.AuditLogDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Repository
public class AuditRepository {

    private static final Pattern ORACLE_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_$#]{0,127}");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private JdbcTemplate jdbc(String username, String password) {
        return new JdbcTemplate(DatabaseConfig.createDataSource(username, password));
    }

    /** List all APP_* usernames that appear in the unified audit trail */
    public List<String> findAuditedUsernames(String adminUser, String adminPass) {
        String sql = """
                SELECT DISTINCT DBUSERNAME
                FROM UNIFIED_AUDIT_TRAIL
                WHERE DBUSERNAME LIKE 'APP\\_%' ESCAPE '\\'
                ORDER BY DBUSERNAME
                """;
        return jdbc(adminUser, adminPass).queryForList(sql, String.class);
    }

    /** Query Unified Audit Trail with optional username, date range filters and pagination */
    public List<AuditLogDTO> findUnifiedAudit(String adminUser, String adminPass,
                                               String usernameFilter, String dateFrom, String dateTo,
                                               int page, int pageSize) {
        StringBuilder where = new StringBuilder("WHERE DBUSERNAME LIKE 'APP\\_%' ESCAPE '\\'");
        if (usernameFilter != null && !usernameFilter.isBlank()) {
            where.append(" AND DBUSERNAME = '").append(normalizeIdentifier(usernameFilter)).append("'");
        }
        if (dateFrom != null && !dateFrom.isBlank()) {
            where.append(" AND EVENT_TIMESTAMP >= TO_TIMESTAMP('").append(sanitizeDate(dateFrom)).append("', 'YYYY-MM-DD')");
        }
        if (dateTo != null && !dateTo.isBlank()) {
            where.append(" AND EVENT_TIMESTAMP < TO_TIMESTAMP('").append(sanitizeDate(dateTo)).append("', 'YYYY-MM-DD') + INTERVAL '1' DAY");
        }
        int offset = (page - 1) * pageSize;
        String sql = """
                SELECT * FROM (
                    SELECT inner_q.*, ROWNUM rn FROM (
                        SELECT EVENT_TIMESTAMP, DBUSERNAME, OS_USERNAME,
                               ACTION_NAME, OBJECT_SCHEMA, OBJECT_NAME, RETURN_CODE, SQL_TEXT
                        FROM UNIFIED_AUDIT_TRAIL
                        %s
                        ORDER BY EVENT_TIMESTAMP DESC
                    ) inner_q WHERE ROWNUM <= %d
                ) WHERE rn > %d
                """.formatted(where, offset + pageSize, offset);

        return jdbc(adminUser, adminPass).query(sql, (rs, rowNum) -> {
            AuditLogDTO dto = new AuditLogDTO();
            Timestamp ts = rs.getTimestamp("EVENT_TIMESTAMP");
            dto.setEventTimestamp(ts != null ? ts.toLocalDateTime().format(TS_FMT) : "-");
            dto.setDbUsername(rs.getString("DBUSERNAME"));
            dto.setOsUsername(rs.getString("OS_USERNAME"));
            dto.setActionName(rs.getString("ACTION_NAME"));
            dto.setObjectSchema(rs.getString("OBJECT_SCHEMA"));
            String obj = rs.getString("OBJECT_NAME");
            dto.setObjectName(obj != null ? obj : "N/A");
            dto.setReturnCode(String.valueOf(rs.getInt("RETURN_CODE")));
            String sqlText = rs.getString("SQL_TEXT");
            dto.setSqlText(sqlText != null ? sqlText : "N/A");
            dto.setAuditType("UNIFIED");
            return dto;
        });
    }

    /** Count total rows for Unified Audit (for pagination) */
    public int countUnifiedAudit(String adminUser, String adminPass,
                                  String usernameFilter, String dateFrom, String dateTo) {
        StringBuilder where = new StringBuilder("WHERE DBUSERNAME LIKE 'APP\\_%' ESCAPE '\\'");
        if (usernameFilter != null && !usernameFilter.isBlank()) {
            where.append(" AND DBUSERNAME = '").append(normalizeIdentifier(usernameFilter)).append("'");
        }
        if (dateFrom != null && !dateFrom.isBlank()) {
            where.append(" AND EVENT_TIMESTAMP >= TO_TIMESTAMP('").append(sanitizeDate(dateFrom)).append("', 'YYYY-MM-DD')");
        }
        if (dateTo != null && !dateTo.isBlank()) {
            where.append(" AND EVENT_TIMESTAMP < TO_TIMESTAMP('").append(sanitizeDate(dateTo)).append("', 'YYYY-MM-DD') + INTERVAL '1' DAY");
        }
        String sql = "SELECT COUNT(*) FROM UNIFIED_AUDIT_TRAIL " + where;
        Integer count = jdbc(adminUser, adminPass).queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    /** Query Fine-Grained Audit Trail with optional username, date range filters and pagination */
    public List<AuditLogDTO> findFgaAudit(String adminUser, String adminPass,
                                           String usernameFilter, String dateFrom, String dateTo,
                                           int page, int pageSize) {
        StringBuilder where = new StringBuilder("WHERE DB_USER LIKE 'APP\\_%' ESCAPE '\\'");
        if (usernameFilter != null && !usernameFilter.isBlank()) {
            where.append(" AND DB_USER = '").append(normalizeIdentifier(usernameFilter)).append("'");
        }
        if (dateFrom != null && !dateFrom.isBlank()) {
            where.append(" AND TIMESTAMP >= TO_TIMESTAMP('").append(sanitizeDate(dateFrom)).append("', 'YYYY-MM-DD')");
        }
        if (dateTo != null && !dateTo.isBlank()) {
            where.append(" AND TIMESTAMP < TO_TIMESTAMP('").append(sanitizeDate(dateTo)).append("', 'YYYY-MM-DD') + INTERVAL '1' DAY");
        }
        int offset = (page - 1) * pageSize;
        String sql = """
                SELECT * FROM (
                    SELECT inner_q.*, ROWNUM rn FROM (
                        SELECT TIMESTAMP, DB_USER, OS_USER,
                               STATEMENT_TYPE, OBJECT_SCHEMA, OBJECT_NAME, SQL_TEXT,
                               0 AS RETURN_CODE
                        FROM DBA_FGA_AUDIT_TRAIL
                        %s
                        ORDER BY TIMESTAMP DESC
                    ) inner_q WHERE ROWNUM <= %d
                ) WHERE rn > %d
                """.formatted(where, offset + pageSize, offset);

        return jdbc(adminUser, adminPass).query(sql, (rs, rowNum) -> {
            AuditLogDTO dto = new AuditLogDTO();
            Timestamp ts = rs.getTimestamp("TIMESTAMP");
            dto.setEventTimestamp(ts != null ? ts.toLocalDateTime().format(TS_FMT) : "-");
            dto.setDbUsername(rs.getString("DB_USER"));
            dto.setOsUsername(rs.getString("OS_USER"));
            dto.setActionName(rs.getString("STATEMENT_TYPE"));
            dto.setObjectSchema(rs.getString("OBJECT_SCHEMA"));
            String obj = rs.getString("OBJECT_NAME");
            dto.setObjectName(obj != null ? obj : "N/A");
            dto.setReturnCode("0");
            String sqlText = rs.getString("SQL_TEXT");
            dto.setSqlText(sqlText != null ? sqlText : "N/A");
            dto.setAuditType("FGA");
            return dto;
        });
    }

    /** Count total rows for FGA Audit (for pagination) */
    public int countFgaAudit(String adminUser, String adminPass,
                              String usernameFilter, String dateFrom, String dateTo) {
        StringBuilder where = new StringBuilder("WHERE DB_USER LIKE 'APP\\_%' ESCAPE '\\'");
        if (usernameFilter != null && !usernameFilter.isBlank()) {
            where.append(" AND DB_USER = '").append(normalizeIdentifier(usernameFilter)).append("'");
        }
        if (dateFrom != null && !dateFrom.isBlank()) {
            where.append(" AND TIMESTAMP >= TO_TIMESTAMP('").append(sanitizeDate(dateFrom)).append("', 'YYYY-MM-DD')");
        }
        if (dateTo != null && !dateTo.isBlank()) {
            where.append(" AND TIMESTAMP < TO_TIMESTAMP('").append(sanitizeDate(dateTo)).append("', 'YYYY-MM-DD') + INTERVAL '1' DAY");
        }
        String sql = "SELECT COUNT(*) FROM DBA_FGA_AUDIT_TRAIL " + where;
        Integer count = jdbc(adminUser, adminPass).queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    /** List all APP_* usernames that appear in FGA audit trail */
    public List<String> findFgaAuditedUsernames(String adminUser, String adminPass) {
        String sql = """
                SELECT DISTINCT DB_USER
                FROM DBA_FGA_AUDIT_TRAIL
                WHERE DB_USER LIKE 'APP\\_%' ESCAPE '\\'
                ORDER BY DB_USER
                """;
        return jdbc(adminUser, adminPass).queryForList(sql, String.class);
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

    /** Validate date string format yyyy-MM-dd to prevent injection */
    private String sanitizeDate(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Invalid date format, expected yyyy-MM-dd: " + date);
        }
        return date;
    }


}
