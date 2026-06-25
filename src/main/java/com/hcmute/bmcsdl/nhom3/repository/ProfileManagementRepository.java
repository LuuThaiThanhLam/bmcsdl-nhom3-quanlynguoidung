package com.hcmute.bmcsdl.nhom3.repository;

import com.hcmute.bmcsdl.nhom3.config.DatabaseConfig;
import com.hcmute.bmcsdl.nhom3.dto.AdminProfileDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
public class ProfileManagementRepository {

    private static final Pattern ORACLE_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_$#]{0,127}");

    private JdbcTemplate jdbc(String username, String password) {
        return new JdbcTemplate(DatabaseConfig.createDataSource(username, password));
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

    private String normalizeAppProfile(String value) {
        String profile = normalizeIdentifier(value);
        if (!profile.startsWith("APP_PROFILE_") && !profile.startsWith("APP_PF_")) {
            throw new IllegalArgumentException("Profile phai co tien to APP_PROFILE_ hoac APP_PF_");
        }
        return profile;
    }

    private String sanitizeLimit(String limit) {
        if (limit == null || limit.isBlank()) return "DEFAULT";
        String s = limit.trim().toUpperCase(Locale.ROOT);
        if ("UNLIMITED".equals(s) || "DEFAULT".equals(s)) {
            return s;
        }
        try {
            int val = Integer.parseInt(s);
            if (val < 0) throw new IllegalArgumentException("Limit khong duoc am");
            return String.valueOf(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Gia tri limit khong hop le: " + limit);
        }
    }

    public List<AdminProfileDTO> findAllProfiles(String adminUsername, String adminPassword) {
        String listSql = """
                SELECT DISTINCT PROFILE 
                FROM DBA_PROFILES 
                WHERE PROFILE LIKE 'APP\\_PROFILE\\_%' ESCAPE '\\' 
                   OR PROFILE LIKE 'APP\\_PF\\_%' ESCAPE '\\' 
                ORDER BY PROFILE
                """;
        List<String> profileNames = jdbc(adminUsername, adminPassword).queryForList(listSql, String.class);
        return profileNames.stream()
                .map(name -> findProfileByName(adminUsername, adminPassword, name))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public Optional<AdminProfileDTO> findProfileByName(String adminUsername, String adminPassword, String profileName) {
        String normalizedName = normalizeAppProfile(profileName);
        String sql = """
                SELECT RESOURCE_NAME, LIMIT 
                FROM DBA_PROFILES 
                WHERE PROFILE = ? 
                  AND RESOURCE_NAME IN ('SESSIONS_PER_USER', 'CONNECT_TIME', 'IDLE_TIME')
                """;
        try {
            AdminProfileDTO dto = new AdminProfileDTO();
            dto.setProfileName(normalizedName);
            jdbc(adminUsername, adminPassword).query(sql, rs -> {
                String resName = rs.getString("RESOURCE_NAME");
                String limit = rs.getString("LIMIT");
                if ("SESSIONS_PER_USER".equalsIgnoreCase(resName)) {
                    dto.setSessionsPerUser(limit);
                } else if ("CONNECT_TIME".equalsIgnoreCase(resName)) {
                    dto.setConnectTime(limit);
                } else if ("IDLE_TIME".equalsIgnoreCase(resName)) {
                    dto.setIdleTime(limit);
                }
            }, normalizedName);
            return Optional.of(dto);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void createProfile(String adminUsername, String adminPassword, AdminProfileDTO dto) {
        String profileName = normalizeAppProfile(dto.getProfileName());
        String sessions = sanitizeLimit(dto.getSessionsPerUser());
        String connect = sanitizeLimit(dto.getConnectTime());
        String idle = sanitizeLimit(dto.getIdleTime());

        String sql = "CREATE PROFILE %s LIMIT SESSIONS_PER_USER %s CONNECT_TIME %s IDLE_TIME %s".formatted(
                profileName, sessions, connect, idle
        );
        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void updateProfile(String adminUsername, String adminPassword, AdminProfileDTO dto) {
        String profileName = normalizeAppProfile(dto.getProfileName());
        String sessions = sanitizeLimit(dto.getSessionsPerUser());
        String connect = sanitizeLimit(dto.getConnectTime());
        String idle = sanitizeLimit(dto.getIdleTime());

        String sql = "ALTER PROFILE %s LIMIT SESSIONS_PER_USER %s CONNECT_TIME %s IDLE_TIME %s".formatted(
                profileName, sessions, connect, idle
        );
        jdbc(adminUsername, adminPassword).execute(sql);
    }

    public void deleteProfile(String adminUsername, String adminPassword, String profileName) {
        String profile = normalizeAppProfile(profileName);
        jdbc(adminUsername, adminPassword).execute("DROP PROFILE %s CASCADE".formatted(profile));
    }
}
