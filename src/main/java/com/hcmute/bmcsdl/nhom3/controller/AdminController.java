package com.hcmute.bmcsdl.nhom3.controller;

import com.hcmute.bmcsdl.nhom3.dto.AdminProfileDTO;
import com.hcmute.bmcsdl.nhom3.dto.AdminRoleDTO;
import com.hcmute.bmcsdl.nhom3.dto.AdminUserDTO;
import com.hcmute.bmcsdl.nhom3.dto.AuditLogDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.service.AuditService;
import java.util.List;
import com.hcmute.bmcsdl.nhom3.service.ProfileManagementService;
import com.hcmute.bmcsdl.nhom3.service.RoleManagermentService;
import com.hcmute.bmcsdl.nhom3.service.SessionManagementService;
import com.hcmute.bmcsdl.nhom3.service.UserManagermentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserManagermentService userManagermentService;
    private final RoleManagermentService roleService;
    private final ProfileManagementService profileService;
    private final AuditService auditService;
    private final SessionManagementService sessionService;

    public AdminController(UserManagermentService userManagermentService, 
                           RoleManagermentService roleService, 
                           ProfileManagementService profileService, 
                           AuditService auditService,
                           SessionManagementService sessionService) {
        this.userManagermentService = userManagermentService;
        this.roleService = roleService;
        this.profileService = profileService;
        this.auditService = auditService;
        this.sessionService = sessionService;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        try {
            // Users
            List<AdminUserDTO> users = userManagermentService.getAllUsers(session);
            long totalUsers = users.size();
            long openUsers = users.stream().filter(u -> u.getAccountStatus() != null && u.getAccountStatus().contains("OPEN")).count();
            long lockedUsers = users.stream().filter(AdminUserDTO::isLocked).count();
            long expiredUsers = users.stream().filter(u -> u.getAccountStatus() != null && u.getAccountStatus().contains("EXPIRED")).count();

            // Roles
            List<AdminRoleDTO> roles = roleService.getAllRoles(session);
            long totalRoles = roles.size();
            long passwordRoles = roles.stream().filter(r -> "YES".equalsIgnoreCase(r.getPasswordRequired())).count();
            long noneRoles = roles.stream().filter(r -> "NO".equalsIgnoreCase(r.getPasswordRequired())).count();
            long externalRoles = totalRoles - passwordRoles - noneRoles;

            // Profiles
            List<AdminProfileDTO> profiles = profileService.getAllProfiles(session);
            long totalProfiles = profiles.size();
            long inUseProfiles = users.stream().map(AdminUserDTO::getProfile).filter(p -> p != null && !p.isBlank()).distinct().count();
            long unusedProfiles = totalProfiles - inUseProfiles;

            // Audit
            long unifiedAuditCount = auditService.countUnifiedAudit(session, null, null, null);
            long fgaAuditCount = auditService.countFgaAudit(session, null, null, null);
            long totalAudits = unifiedAuditCount + fgaAuditCount;

            model.addAttribute("userCount", totalUsers);
            model.addAttribute("openUsers", openUsers);
            model.addAttribute("lockedUsers", lockedUsers);
            model.addAttribute("expiredUsers", expiredUsers);

            model.addAttribute("roleCount", totalRoles);
            model.addAttribute("passwordRoles", passwordRoles);
            model.addAttribute("noneRoles", noneRoles);
            model.addAttribute("externalRoles", externalRoles);

            model.addAttribute("profileCount", totalProfiles);
            model.addAttribute("inUseProfiles", inUseProfiles);
            model.addAttribute("unusedProfiles", unusedProfiles);

            model.addAttribute("auditCount", totalAudits);
            model.addAttribute("unifiedAuditCount", unifiedAuditCount);
            model.addAttribute("fgaAuditCount", fgaAuditCount);

        } catch (Exception e) {
            model.addAttribute("userCount", 0);
            model.addAttribute("roleCount", 0);
            model.addAttribute("profileCount", 0);
            model.addAttribute("auditCount", 0);
        }
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        loadDashboardData(session, model);
        return "admin/users";
    }

    @GetMapping("/sessions")
    public String sessions(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        try {
            model.addAttribute("sessionsList", sessionService.getAllSessions(session));
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi tải phiên kết nối: " + e.getMessage());
        }
        return "admin/sessions";
    }

    @PostMapping("/sessions/kill")
    public String killSession(@RequestParam String sid, @RequestParam String serialNum, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        return runAction(redirectAttributes, "Đã ngắt kết nối phiên làm việc " + sid + "," + serialNum + " thành công!", 
            () -> sessionService.killSession(session, sid, serialNum), "/admin/sessions");
    }

    @GetMapping("/users/new")
    public String createUserForm(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        try {
            model.addAttribute("roles", userManagermentService.getAppRoles(session));
            model.addAttribute("profiles", userManagermentService.getAppProfiles(session));
            model.addAttribute("permanentTablespaces", userManagermentService.getPermanentTablespaces(session));
            model.addAttribute("temporaryTablespaces", userManagermentService.getTemporaryTablespaces(session));
            model.addAttribute("adminUserDTO", new com.hcmute.bmcsdl.nhom3.dto.AdminUserDTO());
        } catch (Exception e) {
            model.addAttribute("roles", java.util.List.of());
            model.addAttribute("profiles", java.util.List.of());
            model.addAttribute("permanentTablespaces", java.util.List.of("TS_USERS"));
            model.addAttribute("temporaryTablespaces", java.util.List.of("TEMP"));
            model.addAttribute("adminUserDTO", new com.hcmute.bmcsdl.nhom3.dto.AdminUserDTO());
        }
        return "admin/user_create";
    }

    @GetMapping("/users/{username}")
    public String userDetail(@PathVariable String username,
            HttpSession session,
            Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        loadDashboardData(session, model);
        try {
            model.addAttribute("selectedUser", userManagermentService.getUserDetail(session, username));
            model.addAttribute("roles", userManagermentService.getAppRoles(session));
            model.addAttribute("profiles", userManagermentService.getAppProfiles(session));
            model.addAttribute("permanentTablespaces", userManagermentService.getPermanentTablespaces(session));
            model.addAttribute("temporaryTablespaces", userManagermentService.getTemporaryTablespaces(session));
            model.addAttribute("quotas", userManagermentService.getUserQuotas(session, username));
            model.addAttribute("userRolesWithOptions", userManagermentService.getUserRolesWithOptions(session, username));
            model.addAttribute("sysPrivs", userManagermentService.getUserSysPrivs(session, username));
            model.addAttribute("tabPrivs", userManagermentService.getUserTabPrivs(session, username));
            model.addAttribute("colPrivs", userManagermentService.getUserColPrivs(session, username));
            model.addAttribute("allSchemas", userManagermentService.getAllSchemas(session));
            model.addAttribute("allTables", userManagermentService.getAllTables(session));
            model.addAttribute("allColumns", userManagermentService.getAllColumns(session));
        } catch (Exception e) {
            model.addAttribute("selectedUser", new com.hcmute.bmcsdl.nhom3.dto.AdminUserDTO());
            model.addAttribute("roles", java.util.List.of());
            model.addAttribute("profiles", java.util.List.of());
            model.addAttribute("permanentTablespaces", java.util.List.of("TS_USERS"));
            model.addAttribute("temporaryTablespaces", java.util.List.of("TEMP"));
            model.addAttribute("quotas", java.util.List.of());
            model.addAttribute("userRolesWithOptions", java.util.List.of());
            model.addAttribute("sysPrivs", java.util.List.of());
            model.addAttribute("tabPrivs", java.util.List.of());
            model.addAttribute("colPrivs", java.util.List.of());
            model.addAttribute("allSchemas", java.util.List.of());
            model.addAttribute("allTables", java.util.List.of());
            model.addAttribute("allColumns", java.util.List.of());
        }
        return "admin/user_detail";
    }

    @PostMapping("/users/create")
    public String createUser(@ModelAttribute AdminUserDTO userDTO,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "User created successfully",
                () -> userManagermentService.createUser(session, userDTO), "/admin/users");
    }

    @PostMapping("/users/update")
    public String updateUser(@ModelAttribute AdminUserDTO userDTO,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "User updated successfully",
                () -> userManagermentService.updateUser(session, userDTO), "/admin/users");
    }

    @PostMapping("/users/{username}/delete")
    public String deleteUser(@PathVariable String username,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "User deleted successfully",
                () -> userManagermentService.deleteUser(session, username), "/admin/users");
    }

    @PostMapping("/users/{username}/lock")
    public String lockUser(@PathVariable String username,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "User locked successfully",
                () -> userManagermentService.lockUser(session, username), "/admin/users");
    }

    @PostMapping("/users/{username}/unlock")
    public String unlockUser(@PathVariable String username,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "User unlocked successfully",
                () -> userManagermentService.unlockUser(session, username), "/admin/users");
    }

    @PostMapping("/users/{username}/reset-password")
    public String resetPassword(@PathVariable String username,
            @RequestParam String newPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        AdminUserDTO userDTO = new AdminUserDTO();
        userDTO.setUsername(username);
        userDTO.setNewPassword(newPassword);

        return runAction(redirectAttributes, "Password reset successfully",
                () -> userManagermentService.resetPassword(session, userDTO), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/grant-role")
    public String grantRole(@PathVariable String username,
            @RequestParam String roleToGrant,
            @RequestParam(required = false, defaultValue = "false") boolean adminOption,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "Role granted successfully",
                () -> userManagermentService.grantRole(session, username, roleToGrant, adminOption), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/revoke-role")
    public String revokeRole(@PathVariable String username,
            @RequestParam String roleToRevoke,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        AdminUserDTO userDTO = new AdminUserDTO();
        userDTO.setUsername(username);
        userDTO.setRoleToRevoke(roleToRevoke);

        return runAction(redirectAttributes, "Role revoked successfully",
                () -> userManagermentService.revokeRole(session, userDTO), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/change-profile")
    public String changeProfile(@PathVariable String username,
            @RequestParam String profile,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        AdminUserDTO userDTO = new AdminUserDTO();
        userDTO.setUsername(username);
        userDTO.setProfile(profile);

        return runAction(redirectAttributes, "Profile changed successfully",
                () -> userManagermentService.changeProfile(session, userDTO), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/set-quota")
    public String setUserQuota(@PathVariable String username,
            @RequestParam String tablespace,
            @RequestParam String quota,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "Quota updated successfully",
                () -> userManagermentService.setUserQuota(session, username, tablespace, quota), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/grant-sys-priv")
    public String grantSysPriv(@PathVariable String username,
            @RequestParam String privilege,
            @RequestParam(required = false, defaultValue = "false") boolean adminOption,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "System privilege granted successfully",
                () -> userManagermentService.grantSysPriv(session, username, privilege, adminOption), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/revoke-sys-priv")
    public String revokeSysPriv(@PathVariable String username,
            @RequestParam String privilege,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "System privilege revoked successfully",
                () -> userManagermentService.revokeSysPriv(session, username, privilege), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/grant-obj-priv")
    public String grantObjPriv(@PathVariable String username,
            @RequestParam String owner,
            @RequestParam String tableName,
            @RequestParam("privilege") java.util.List<String> privilege,
            @RequestParam(required = false, defaultValue = "false") boolean grantOption,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        String cleanTableName = tableName.contains(" ") ? tableName.split(" ")[0] : tableName;
        return runAction(redirectAttributes, "Object privilege granted successfully",
                () -> userManagermentService.grantObjPriv(session, username, owner, cleanTableName, privilege, grantOption), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/revoke-obj-priv")
    public String revokeObjPriv(@PathVariable String username,
            @RequestParam String owner,
            @RequestParam String tableName,
            @RequestParam String privilege,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "Object privilege revoked successfully",
                () -> userManagermentService.revokeObjPriv(session, username, owner, tableName, privilege), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/grant-col-priv")
    public String grantColPriv(@PathVariable String username,
            @RequestParam(value = "table", required = false) String table,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "tableName", required = false) String tableName,
            @RequestParam("columnName") java.util.List<String> columnName,
            @RequestParam String privilege,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        final String finalOwner;
        final String finalTableName;
        if (table != null && table.contains(".")) {
            String[] parts = table.split("\\.");
            finalOwner = parts[0];
            finalTableName = parts[1];
        } else {
            finalOwner = owner;
            finalTableName = tableName;
        }

        return runAction(redirectAttributes, "Column privilege granted successfully",
                () -> userManagermentService.grantColPriv(session, username, finalOwner, finalTableName, columnName, privilege), "/admin/users/" + username);
    }

    @PostMapping("/users/{username}/revoke-col-priv")
    public String revokeColPriv(@PathVariable String username,
            @RequestParam String owner,
            @RequestParam String tableName,
            @RequestParam String privilege,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        return runAction(redirectAttributes, "Column privilege revoked successfully",
                () -> userManagermentService.revokeColPriv(session, username, owner, tableName, privilege), "/admin/users/" + username);
    }

    @GetMapping("/roles")
    public String roles(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        model.addAttribute("roles", roleService.getAllRoles(session));
        model.addAttribute("newRole", new AdminRoleDTO());
        return "admin/roles";
    }

    @GetMapping("/roles/{roleName}")
    public String roleDetail(@PathVariable String roleName, HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        try {
            model.addAttribute("detail", roleService.getRoleDetail(session, roleName));
            model.addAttribute("roleName", roleName);
            model.addAttribute("users", userManagermentService.getAllUsers(session));
            model.addAttribute("roles", roleService.getAllRoles(session));
            return "admin/role_detail";
        } catch (OracleException e) {
            model.addAttribute("error", e.getMessage());
            return roles(session, model);
        }
    }

    @PostMapping("/roles/create")
    public String createRole(@ModelAttribute AdminRoleDTO roleDTO, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Role created successfully", () -> roleService.createRole(session, roleDTO), "/admin/roles");
    }

    @PostMapping("/roles/{roleName}/delete")
    public String deleteRole(@PathVariable("roleName") String roleName, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Role deleted successfully", () -> roleService.deleteRole(session, roleName), "/admin/roles");
    }

    @PostMapping("/roles/{roleName}/update-password")
    public String updateRolePassword(@PathVariable("roleName") String roleName,
                                     @RequestParam(value = "password", required = false) String password,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Cập nhật mật khẩu role thành công", () -> roleService.updateRolePassword(session, roleName, password), "/admin/roles/" + roleName);
    }

    @PostMapping("/roles/{roleName}/grant-sys")
    public String grantSysPrivilege(@PathVariable("roleName") String roleName,
                                    @RequestParam String privilege,
                                    @RequestParam(value = "withAdminOption", defaultValue = "false") boolean withAdminOption,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Privilege granted successfully", () -> roleService.grantSysPrivilege(session, roleName, privilege, withAdminOption), "/admin/roles/" + roleName);
    }

    @PostMapping("/roles/{roleName}/revoke-sys")
    public String revokeSysPrivilege(@PathVariable("roleName") String roleName, @RequestParam String privilege, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Privilege revoked successfully", () -> roleService.revokeSysPrivilege(session, roleName, privilege), "/admin/roles/" + roleName);
    }

    @PostMapping("/roles/{granteeRole}/grant-role")
    public String grantRoleToRole(@PathVariable("granteeRole") String granteeRole,
                                  @RequestParam String grantedRole,
                                  @RequestParam(value = "withAdminOption", defaultValue = "false") boolean withAdminOption,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Role granted successfully", () -> roleService.grantRoleToRole(session, granteeRole, grantedRole, withAdminOption), "/admin/roles/" + granteeRole);
    }

    @PostMapping("/roles/{granteeRole}/revoke-role")
    public String revokeRoleFromRole(@PathVariable("granteeRole") String granteeRole, @RequestParam String grantedRole, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Role revoked successfully", () -> roleService.revokeRoleFromRole(session, granteeRole, grantedRole), "/admin/roles/" + granteeRole);
    }

    @PostMapping("/roles/{roleName}/grant-user")
    public String grantRoleToUser(@PathVariable("roleName") String roleName,
                                  @RequestParam String granteeUser,
                                  @RequestParam(value = "withAdminOption", defaultValue = "false") boolean withAdminOption,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Role granted to user successfully", () -> roleService.grantRoleToUser(session, granteeUser, roleName, withAdminOption), "/admin/roles/" + roleName);
    }

    @GetMapping("/api/tables")
    @ResponseBody
    public java.util.List<String> getTablesByOwner(@RequestParam String owner, HttpSession session) {
        if (!isLoggedIn(session)) return java.util.List.of();
        return roleService.getTablesByOwner(session, owner);
    }

    @GetMapping("/api/columns")
    @ResponseBody
    public java.util.List<String> getColumnsByTable(@RequestParam String owner, @RequestParam String tableName, HttpSession session) {
        if (!isLoggedIn(session)) return java.util.List.of();
        return roleService.getColumnsByTable(session, owner, tableName);
    }

    @GetMapping("/api/all-tables")
    @ResponseBody
    public java.util.List<String> getAllAppTables(HttpSession session) {
        if (!isLoggedIn(session)) return java.util.List.of();
        return roleService.getAllAppTables(session);
    }

    @PostMapping("/roles/{roleName}/revoke-user")
    public String revokeRoleFromUser(@PathVariable("roleName") String roleName, @RequestParam String granteeUser, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Role revoked from user successfully", () -> roleService.revokeRoleFromUser(session, granteeUser, roleName), "/admin/roles/" + roleName);
    }

    @PostMapping("/roles/{roleName}/grant-tab")
    public String grantTabPrivilege(@PathVariable("roleName") String roleName,
                                    @RequestParam String owner,
                                    @RequestParam String tableName,
                                    @RequestParam("privilege") java.util.List<String> privilege,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        String cleanTableName = tableName.contains(" ") ? tableName.split(" ")[0] : tableName;
        return runAction(redirectAttributes, "Object privilege granted successfully", () -> roleService.grantTabPrivilege(session, roleName, owner, cleanTableName, privilege), "/admin/roles/" + roleName);
    }

    @PostMapping("/roles/{roleName}/revoke-tab")
    public String revokeTabPrivilege(@PathVariable("roleName") String roleName, @RequestParam String owner, @RequestParam String tableName, @RequestParam String privilege, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Object privilege revoked successfully", () -> roleService.revokeTabPrivilege(session, roleName, owner, tableName, privilege), "/admin/roles/" + roleName);
    }

    @PostMapping("/roles/{roleName}/grant-col")
    public String grantColPrivilege(@PathVariable("roleName") String roleName,
                                    @RequestParam(value = "table", required = false) String table,
                                    @RequestParam(value = "owner", required = false) String owner,
                                    @RequestParam(value = "tableName", required = false) String tableName,
                                    @RequestParam("columnName") java.util.List<String> columnName,
                                    @RequestParam String privilege,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        
        final String finalOwner;
        final String finalTableName;
        if (table != null && table.contains(".")) {
            String[] parts = table.split("\\.");
            finalOwner = parts[0];
            finalTableName = parts[1];
        } else {
            finalOwner = owner;
            finalTableName = tableName;
        }

        return runAction(redirectAttributes, "Column privilege granted successfully",
                () -> roleService.grantColPrivilege(session, roleName, finalOwner, finalTableName, columnName, privilege),
                "/admin/roles/" + roleName);
    }

    @PostMapping("/roles/{roleName}/revoke-col")
    public String revokeColPrivilege(@PathVariable("roleName") String roleName, @RequestParam String owner, @RequestParam String tableName, @RequestParam String privilege, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) return "redirect:/";
        return runAction(redirectAttributes, "Column privilege revoked successfully", () -> roleService.revokeColPrivilege(session, roleName, owner, tableName, privilege), "/admin/roles/" + roleName);
    }

    @GetMapping("/profiles")
    public String profiles(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        try {
            model.addAttribute("profiles", profileService.getAllProfiles(session));
            model.addAttribute("adminProfileDTO", new AdminProfileDTO());
        } catch (Exception e) {
            model.addAttribute("profiles", java.util.List.of());
            model.addAttribute("adminProfileDTO", new AdminProfileDTO());
            model.addAttribute("error", e.getMessage());
        }
        return "admin/profiles";
    }

    @PostMapping("/profiles/create")
    public String createProfile(@ModelAttribute AdminProfileDTO profileDTO,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        return runAction(redirectAttributes, "Profile " + profileDTO.getProfileName() + " created successfully!",
                () -> profileService.createProfile(session, profileDTO), "/admin/profiles");
    }

    @GetMapping("/profiles/{profileName}")
    public String profileDetail(@PathVariable String profileName,
                                HttpSession session,
                                Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        try {
            AdminProfileDTO profile = profileService.getProfileDetail(session, profileName);
            model.addAttribute("profile", profile);
            return "admin/profile_detail";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "redirect:/admin/profiles";
        }
    }

    @PostMapping("/profiles/update")
    public String updateProfile(@ModelAttribute AdminProfileDTO profileDTO,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        return runAction(redirectAttributes, "Profile " + profileDTO.getProfileName() + " updated successfully!",
                () -> profileService.updateProfile(session, profileDTO), "/admin/profiles");
    }

    @PostMapping("/profiles/{profileName}/delete")
    public String deleteProfile(@PathVariable String profileName,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        return runAction(redirectAttributes, "Profile " + profileName + " deleted successfully!",
                () -> profileService.deleteProfile(session, profileName), "/admin/profiles");
    }

    @GetMapping("/audit-logs")
    public String auditLogs(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "50") int pageSize,
            HttpSession session,
            Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        String activeTab = (tab != null && tab.equals("fga")) ? "fga" : "unified";
        int safePage = Math.max(1, page);
        int safePageSize = (pageSize == 20 || pageSize == 50 || pageSize == 100) ? pageSize : 50;

        model.addAttribute("activeTab", activeTab);
        model.addAttribute("selectedUsername", username);
        model.addAttribute("selectedDateFrom", dateFrom);
        model.addAttribute("selectedDateTo", dateTo);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safePageSize);

        try {
            model.addAttribute("auditUsernames", auditService.getAuditedUsernames(session));
        } catch (Exception e) {
            model.addAttribute("auditUsernames", java.util.List.of());
        }
        try {
            model.addAttribute("fgaUsernames", auditService.getFgaAuditedUsernames(session));
        } catch (Exception e) {
            model.addAttribute("fgaUsernames", java.util.List.of());
        }
        try {
            int totalCount;
            if ("fga".equals(activeTab)) {
                totalCount = auditService.countFgaAudit(session, username, dateFrom, dateTo);
                model.addAttribute("auditLogs", auditService.getFgaAudit(session, username, dateFrom, dateTo, safePage, safePageSize));
            } else {
                totalCount = auditService.countUnifiedAudit(session, username, dateFrom, dateTo);
                model.addAttribute("auditLogs", auditService.getUnifiedAudit(session, username, dateFrom, dateTo, safePage, safePageSize));
            }
            int totalPages = (int) Math.ceil((double) totalCount / safePageSize);
            model.addAttribute("totalCount", totalCount);
            model.addAttribute("totalPages", Math.max(1, totalPages));
        } catch (OracleException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errorCode", e.getErrorCode());
            model.addAttribute("auditLogs", java.util.List.of());
            model.addAttribute("totalCount", 0);
            model.addAttribute("totalPages", 1);
        }
        return "admin/audit_logs";
    }

    @ExceptionHandler(OracleException.class)
    public String handleOracleException(OracleException exception,
            Model model,
            HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }

        model.addAttribute("error", exception.getMessage());
        model.addAttribute("errorCode", exception.getErrorCode());
        loadDashboardDataSilently(session, model);
        return "admin/users";
    }

    private void loadDashboardData(HttpSession session, Model model) {
        model.addAttribute("users", userManagermentService.getAllUsers(session));
        model.addAttribute("roles", userManagermentService.getAppRoles(session));
        model.addAttribute("profiles", userManagermentService.getAppProfiles(session));
        model.addAttribute("adminUserDTO", new AdminUserDTO());
    }

    private void loadDashboardDataSilently(HttpSession session, Model model) {
        try {
            loadDashboardData(session, model);
        } catch (OracleException ignored) {
            model.addAttribute("users", java.util.List.of());
            model.addAttribute("roles", java.util.List.of());
            model.addAttribute("profiles", java.util.List.of());
            model.addAttribute("adminUserDTO", new AdminUserDTO());
        }
    }

    private String runAction(RedirectAttributes redirectAttributes, String successMessage, Runnable action) {
        return runAction(redirectAttributes, successMessage, action, "/admin/dashboard");
    }

    private String runAction(RedirectAttributes redirectAttributes, String successMessage, Runnable action, String redirectUrl) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("success", successMessage);
        } catch (OracleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("errorCode", e.getErrorCode());
        }
        return "redirect:" + redirectUrl;
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute("username") != null;
    }
}