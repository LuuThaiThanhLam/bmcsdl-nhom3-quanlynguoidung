package com.hcmute.bmcsdl.nhom3.service;

import com.hcmute.bmcsdl.nhom3.config.DatabaseConfig;
import org.springframework.stereotype.Service;
import java.sql.*;

@Service
public class CheckAccessService {

    // Kiểm tra user hiện tại có quyền gì không
    // Dùng SESSION_PRIVS - view mặc định, ai cũng xem được
    public boolean hasPrivilege(String dbUser, String dbPass, String privilege) {
        String sql = "SELECT PRIVILEGE FROM SESSION_PRIVS WHERE PRIVILEGE = ?";
        try (Connection conn = DatabaseConfig.createDataSource(dbUser, dbPass).getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, privilege);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // Kiểm tra user có role không
    public boolean hasRole(String dbUser, String dbPass, String role) {
        String sql = "SELECT ROLE FROM SESSION_ROLES WHERE ROLE = ?";
        try (Connection conn = DatabaseConfig.createDataSource(dbUser, dbPass).getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }
}