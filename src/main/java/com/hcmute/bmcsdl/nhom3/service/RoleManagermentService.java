package com.hcmute.bmcsdl.nhom3.service;

import com.hcmute.bmcsdl.nhom3.dto.AdminRoleDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.repository.RoleManagermentRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoleManagermentService {

    private final RoleManagermentRepository roleRepository;

    public RoleManagermentService(RoleManagermentRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<AdminRoleDTO> getAllRoles(HttpSession session) {
        return execute(session, (username, password) -> roleRepository.findAllRoles(username, password));
    }

    public Map<String, Object> getRoleDetail(HttpSession session, String roleName) {
        return execute(session, (username, password) -> {
            AdminRoleDTO role = roleRepository.findRoleByName(username, password, roleName)
                    .orElseThrow(() -> new OracleException("Role khong ton tai", "404"));

            Map<String, Object> detail = new HashMap<>();
            detail.put("role", role);
            detail.put("sysPrivs", roleRepository.findSysPrivileges(username, password, roleName));
            detail.put("tabPrivs", roleRepository.findTabPrivileges(username, password, roleName));
            detail.put("colPrivs", roleRepository.findColPrivileges(username, password, roleName));
            detail.put("rolePrivs", roleRepository.findRolePrivileges(username, password, roleName));
            detail.put("grantees", roleRepository.findGrantees(username, password, roleName));

            return detail;
        });
    }

    public void createRole(HttpSession session, AdminRoleDTO roleDTO) {
        executeVoid(session, (username, password) -> {
            roleRepository.createRole(username, password, roleDTO.getRole(), roleDTO.getPasswordRequired());
        });
    }

    public void deleteRole(HttpSession session, String roleName) {
        executeVoid(session, (username, password) -> {
            roleRepository.deleteRole(username, password, roleName);
        });
    }

    public void grantSysPrivilege(HttpSession session, String roleName, String privilege, boolean withAdminOption) {
        executeVoid(session, (username, password) -> {
            roleRepository.grantSysPrivilege(username, password, roleName, privilege, withAdminOption);
        });
    }

    public void revokeSysPrivilege(HttpSession session, String roleName, String privilege) {
        executeVoid(session, (username, password) -> {
            roleRepository.revokeSysPrivilege(username, password, roleName, privilege);
        });
    }

    public void grantRoleToRole(HttpSession session, String granteeRole, String grantedRole, boolean withAdminOption) {
        executeVoid(session, (username, password) -> {
            roleRepository.grantRoleToRole(username, password, granteeRole, grantedRole, withAdminOption);
        });
    }

    public void revokeRoleFromRole(HttpSession session, String granteeRole, String grantedRole) {
        executeVoid(session, (username, password) -> {
            roleRepository.revokeRoleFromRole(username, password, granteeRole, grantedRole);
        });
    }

    public void grantRoleToUser(HttpSession session, String granteeUser, String grantedRole, boolean withAdminOption) {
        executeVoid(session, (username, password) -> {
            roleRepository.grantRoleToUser(username, password, granteeUser, grantedRole, withAdminOption);
        });
    }

    public void revokeRoleFromUser(HttpSession session, String granteeUser, String grantedRole) {
        executeVoid(session, (username, password) -> {
            roleRepository.revokeRoleFromUser(username, password, granteeUser, grantedRole);
        });
    }

    public void grantTabPrivilege(HttpSession session, String roleName, String owner, String tableName, List<String> privileges) {
        executeVoid(session, (username, password) -> {
            roleRepository.grantTabPrivilege(username, password, roleName, owner, tableName, privileges);
        });
    }

    public void revokeTabPrivilege(HttpSession session, String roleName, String owner, String tableName, String privilege) {
        executeVoid(session, (username, password) -> {
            roleRepository.revokeTabPrivilege(username, password, roleName, owner, tableName, privilege);
        });
    }

    public void grantColPrivilege(HttpSession session, String roleName, String owner, String tableName, List<String> columnNames, String privilege) {
        executeVoid(session, (username, password) -> {
            roleRepository.grantColPrivilege(username, password, roleName, owner, tableName, columnNames, privilege);
        });
    }

    public void updateRolePassword(HttpSession session, String roleName, String password) {
        executeVoid(session, (username, dbPassword) -> {
            roleRepository.updateRolePassword(username, dbPassword, roleName, password);
        });
    }

    public List<String> getAllAppTables(HttpSession session) {
        return execute(session, (username, password) -> roleRepository.getAllAppTables(username, password));
    }

    public void revokeColPrivilege(HttpSession session, String roleName, String owner, String tableName, String privilege) {
        executeVoid(session, (username, password) -> {
            roleRepository.revokeColPrivilege(username, password, roleName, owner, tableName, privilege);
        });
    }

    public List<String> getTablesByOwner(HttpSession session, String owner) {
        return execute(session, (username, password) -> roleRepository.getTablesByOwner(username, password, owner));
    }

    public List<String> getColumnsByTable(HttpSession session, String owner, String tableName) {
        return execute(session, (username, password) -> roleRepository.getColumnsByTable(username, password, owner, tableName));
    }

    private <T> T execute(HttpSession session, DatabaseAction<T> action) {
        String username = (String) session.getAttribute("dbUser");
        String password = (String) session.getAttribute("dbPass");

        if (username == null || password == null) {
            throw new OracleException("Chua dang nhap", "401");
        }

        try {
            return action.execute(username, password);
        } catch (Exception e) {
            throw new OracleException("Loi thao tac CSDL: " + e.getMessage(), "500", e);
        }
    }

    private void executeVoid(HttpSession session, VoidDatabaseAction action) {
        execute(session, (u, p) -> {
            action.execute(u, p);
            return null;
        });
    }

    @FunctionalInterface
    private interface DatabaseAction<T> {
        T execute(String username, String password) throws Exception;
    }

    @FunctionalInterface
    private interface VoidDatabaseAction {
        void execute(String username, String password) throws Exception;
    }
}
