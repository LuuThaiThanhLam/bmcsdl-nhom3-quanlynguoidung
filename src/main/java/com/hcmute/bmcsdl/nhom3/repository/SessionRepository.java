package com.hcmute.bmcsdl.nhom3.repository;

import com.hcmute.bmcsdl.nhom3.config.DatabaseConfig;
import com.hcmute.bmcsdl.nhom3.dto.SessionDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class SessionRepository {

    private JdbcTemplate jdbc(String username, String password) {
        return new JdbcTemplate(DatabaseConfig.createDataSource(username, password));
    }

    public List<SessionDTO> getAllSessions(String adminUser, String adminPass) {
        String sql = "SELECT sid, serial#, username, status, osuser, machine, program, logon_time " +
                     "FROM v$session " +
                     "WHERE type != 'BACKGROUND' AND username IS NOT NULL " +
                     "ORDER BY logon_time DESC";

        return jdbc(adminUser, adminPass).query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("logon_time");
            return new SessionDTO(
                rs.getString("sid"),
                rs.getString("serial#"),
                rs.getString("username"),
                rs.getString("status"),
                rs.getString("osuser"),
                rs.getString("machine"),
                rs.getString("program"),
                ts != null ? ts.toLocalDateTime() : null
            );
        });
    }

    public void killSession(String adminUser, String adminPass, String sid, String serialNum) {
        String sql = "ALTER SYSTEM KILL SESSION '" + sid + "," + serialNum + "' IMMEDIATE";
        try {
            jdbc(adminUser, adminPass).execute(sql);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new RuntimeException("ORA Error: " + (cause != null ? cause.getMessage() : e.getMessage()));
        }
    }
}
