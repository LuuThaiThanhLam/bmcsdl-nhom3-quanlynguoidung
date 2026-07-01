package com.hcmute.bmcsdl.nhom3.controller;

import com.hcmute.bmcsdl.nhom3.config.DatabaseConfig;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.sql.*;

@Controller
public class LoginController {

    @GetMapping({ "/", "/login" })
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            Model model) {
        try {
            String user = username.toUpperCase().trim();
            String role = getUserRole(user, password);

            session.setAttribute("username", user);
            session.setAttribute("dbUser", user);
            session.setAttribute("dbPass", password);

            switch (role) {
                case "ADMIN":
                    return "redirect:/admin/dashboard";
                case "MANAGER":
                    return "redirect:/manager/dashboard";
                case "USER":
                    return "redirect:/user/profile";
                default:
                    model.addAttribute("error", "Không có role phù hợp!");
                    return "login";
            }
        } catch (SQLException e) {
            model.addAttribute("error", loginErrorMessage(e));
            return "login";
        }
    }

    private String loginErrorMessage(SQLException e) {
        return switch (e.getErrorCode()) {
            case 28000 -> "Tài khoản đã bị khóa!";
            case 28001 -> "Mật khẩu đã hết hạn!";
            case 1017 -> "Sai tài khoản hoặc mật khẩu!";
            default -> "Không thể đăng nhập: " + e.getMessage();
        };
    }

    private String getUserRole(String username, String password) throws SQLException {
        String sql = "SELECT ROLE FROM SESSION_ROLES";
        try (Connection conn = DatabaseConfig.createDataSource(username, password).getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String r = rs.getString("ROLE");
                if ("APP_ROLE_DB_ADMIN".equals(r))
                    return "ADMIN";
                if ("APP_ROLE_PROFILE_MGR".equals(r))
                    return "MANAGER";
                if ("APP_ROLE_USER".equals(r))
                    return "USER";
            }
        }
        return "UNKNOWN";
    }
}
