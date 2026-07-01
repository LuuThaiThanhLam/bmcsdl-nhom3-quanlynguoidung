package com.hcmute.bmcsdl.nhom3.service;

import com.hcmute.bmcsdl.nhom3.dto.AdminUserDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.repository.UserManagermentRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Supplier;

@Service
public class UserManagermentService {

    private final UserManagermentRepository userManagermentRepository;

    public UserManagermentService(UserManagermentRepository userManagermentRepository) {
        this.userManagermentRepository = userManagermentRepository;
    }

    public List<AdminUserDTO> getAllUsers(HttpSession session) {
        AdminCredential credential = getAdminCredential(session);

        return callOracle("Khong the lay danh sach nguoi dung", () ->
                userManagermentRepository.findAllUsers(credential.username(), credential.password()));
    }

    public AdminUserDTO getUserDetail(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);

        AdminUserDTO user = callOracle("Khong the lay thong tin nguoi dung", () ->
                userManagermentRepository.findUserByUsername(credential.username(), credential.password(), username)
                        .orElseThrow(() -> new OracleException("Khong tim thay user: " + username, "USER_NOT_FOUND")));

        user.setRoles(getUserRoles(session, username));
        return user;
    }

    public List<String> getUserRoles(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);

        return callOracle("Khong the lay danh sach role cua nguoi dung", () ->
                userManagermentRepository.findUserRoles(credential.username(), credential.password(), username));
    }

    public List<String> getAppRoles(HttpSession session) {
        AdminCredential credential = getAdminCredential(session);

        return callOracle("Khong the lay danh sach role ung dung", () ->
                userManagermentRepository.findAppRoles(credential.username(), credential.password()));
    }

    public List<String> getAppProfiles(HttpSession session) {
        AdminCredential credential = getAdminCredential(session);

        return callOracle("Khong the lay danh sach profile ung dung", () ->
                userManagermentRepository.findAppProfiles(credential.username(), credential.password()));
    }

    public List<String> getPermanentTablespaces(HttpSession session) {
        AdminCredential credential = getAdminCredential(session);

        return callOracle("Khong the lay danh sach permanent tablespace", () ->
                userManagermentRepository.findPermanentTablespaces(credential.username(), credential.password()));
    }

    public List<String> getTemporaryTablespaces(HttpSession session) {
        AdminCredential credential = getAdminCredential(session);

        return callOracle("Khong the lay danh sach temporary tablespace", () ->
                userManagermentRepository.findTemporaryTablespaces(credential.username(), credential.password()));
    }

    public void createUser(HttpSession session, AdminUserDTO userDTO) {
        AdminCredential credential = getAdminCredential(session);

        runOracle("Khong the tao nguoi dung", () ->
                userManagermentRepository.createUser(credential.username(), credential.password(), userDTO));
    }

    public void updateUser(HttpSession session, AdminUserDTO userDTO) {
        AdminCredential credential = getAdminCredential(session);

        runOracle("Khong the cap nhat nguoi dung", () ->
                userManagermentRepository.updateUser(credential.username(), credential.password(), userDTO));
    }

    public void deleteUser(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);

        runOracle("Khong the xoa nguoi dung", () ->
                userManagermentRepository.deleteUser(credential.username(), credential.password(), username));
    }

    public void deleteUser(HttpSession session, AdminUserDTO userDTO) {
        requireUserDTO(userDTO);
        deleteUser(session, userDTO.getUsername());
    }

    public void lockUser(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);

        runOracle("Khong the khoa nguoi dung", () ->
                userManagermentRepository.lockUser(credential.username(), credential.password(), username));
    }

    public void lockUser(HttpSession session, AdminUserDTO userDTO) {
        requireUserDTO(userDTO);
        lockUser(session, userDTO.getUsername());
    }

    public void unlockUser(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);

        runOracle("Khong the mo khoa nguoi dung", () ->
                userManagermentRepository.unlockUser(credential.username(), credential.password(), username));
    }

    public void unlockUser(HttpSession session, AdminUserDTO userDTO) {
        requireUserDTO(userDTO);
        unlockUser(session, userDTO.getUsername());
    }

    public void resetPassword(HttpSession session, AdminUserDTO userDTO) {
        requireUserDTO(userDTO);
        AdminCredential credential = getAdminCredential(session);
        String newPassword = firstNotBlank(userDTO.getNewPassword(), userDTO.getPassword());

        runOracle("Khong the dat lai mat khau", () ->
                userManagermentRepository.resetPassword(credential.username(), credential.password(),
                        userDTO.getUsername(), newPassword));
    }

    public void grantRole(HttpSession session, AdminUserDTO userDTO) {
        requireUserDTO(userDTO);
        String role = firstNotBlank(userDTO.getRoleToGrant(), userDTO.getGrantedRole());
        grantRole(session, userDTO.getUsername(), role, false);
    }

    public void grantRole(HttpSession session, String username, String role, boolean adminOption) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the gan role cho nguoi dung", () ->
                userManagermentRepository.grantRole(credential.username(), credential.password(), username, role, adminOption));
    }

    public List<java.util.Map<String, Object>> getUserQuotas(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay danh sach quota", () ->
                userManagermentRepository.findUserQuotas(credential.username(), credential.password(), username));
    }

    public List<java.util.Map<String, Object>> getUserRolesWithOptions(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay danh sach role cua user", () ->
                userManagermentRepository.findUserRolesWithOptions(credential.username(), credential.password(), username));
    }

    public List<java.util.Map<String, Object>> getUserSysPrivs(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay danh sach system privilege", () ->
                userManagermentRepository.findUserSysPrivs(credential.username(), credential.password(), username));
    }

    public List<java.util.Map<String, Object>> getUserTabPrivs(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay danh sach object privilege", () ->
                userManagermentRepository.findUserTabPrivs(credential.username(), credential.password(), username));
    }

    public List<java.util.Map<String, Object>> getUserColPrivs(HttpSession session, String username) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay danh sach column privilege", () ->
                userManagermentRepository.findUserColPrivs(credential.username(), credential.password(), username));
    }

    public void setUserQuota(HttpSession session, String username, String tablespace, String quota) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the cap nhat quota", () ->
                userManagermentRepository.setUserQuota(credential.username(), credential.password(), username, tablespace, quota));
    }

    public void revokeUserQuota(HttpSession session, String username, String tablespace) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the thu hoi quota", () ->
                userManagermentRepository.revokeUserQuota(credential.username(), credential.password(), username, tablespace));
    }

    public void grantSysPriv(HttpSession session, String username, String privilege, boolean adminOption) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the cap quyen he thong", () ->
                userManagermentRepository.grantSysPriv(credential.username(), credential.password(), username, privilege, adminOption));
    }

    public void revokeSysPriv(HttpSession session, String username, String privilege) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the thu hoi quyen he thong", () ->
                userManagermentRepository.revokeSysPriv(credential.username(), credential.password(), username, privilege));
    }

    public void grantObjPriv(HttpSession session, String username, String owner, String tableName, List<String> privileges, boolean grantOption) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the cap quyen doi tuong", () ->
                userManagermentRepository.grantObjPriv(credential.username(), credential.password(), username, owner, tableName, privileges, grantOption));
    }

    public void revokeObjPriv(HttpSession session, String username, String owner, String tableName, String privilege) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the thu hoi quyen doi tuong", () ->
                userManagermentRepository.revokeObjPriv(credential.username(), credential.password(), username, owner, tableName, privilege));
    }

    public void grantColPriv(HttpSession session, String username, String owner, String tableName, List<String> columnNames, String privilege) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the cap quyen cot", () ->
                userManagermentRepository.grantColPriv(credential.username(), credential.password(), username, owner, tableName, columnNames, privilege));
    }

    public void revokeColPriv(HttpSession session, String username, String owner, String tableName, String columnName, String privilege) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the thu hoi quyen cot", () ->
                userManagermentRepository.revokeColPriv(credential.username(), credential.password(), username, owner, tableName, columnName, privilege));
    }

    public void revokeRole(HttpSession session, AdminUserDTO userDTO) {
        requireUserDTO(userDTO);
        AdminCredential credential = getAdminCredential(session);

        runOracle("Khong the thu hoi role cua nguoi dung", () ->
                userManagermentRepository.revokeRole(credential.username(), credential.password(),
                        userDTO.getUsername(), userDTO.getRoleToRevoke()));
    }

    public void changeProfile(HttpSession session, AdminUserDTO userDTO) {
        requireUserDTO(userDTO);
        AdminCredential credential = getAdminCredential(session);

        runOracle("Khong the doi profile cua nguoi dung", () ->
                userManagermentRepository.changeProfile(credential.username(), credential.password(),
                        userDTO.getUsername(), userDTO.getProfile()));
    }

    private AdminCredential getAdminCredential(HttpSession session) {
        if (session == null) {
            throw new OracleException("Phien dang nhap khong hop le", "SESSION_INVALID");
        }

        Object dbUser = session.getAttribute("dbUser");
        Object dbPass = session.getAttribute("dbPass");

        if (dbUser == null || dbPass == null) {
            throw new OracleException("Chua dang nhap hoac phien dang nhap da het han", "SESSION_EXPIRED");
        }

        return new AdminCredential(dbUser.toString(), dbPass.toString());
    }

    private void requireUserDTO(AdminUserDTO userDTO) {
        if (userDTO == null) {
            throw new OracleException("Du lieu nguoi dung khong duoc de trong", "INVALID_USER_DATA");
        }
    }

    private void runOracle(String message, Runnable action) {
        callOracle(message, () -> {
            action.run();
            return null;
        });
    }

    private <T> T callOracle(String message, Supplier<T> action) {
        try {
            return action.get();
        } catch (OracleException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new OracleException(e.getMessage(), "INVALID_INPUT");
        } catch (DataAccessException e) {
            throw toOracleException(message, e);
        }
    }

    private OracleException toOracleException(String message, DataAccessException exception) {
        SQLException sqlException = findSQLException(exception);
        if (sqlException == null) {
            return new OracleException(message + ": " + exception.getMostSpecificCause().getMessage(), "ORACLE_ERROR");
        }

        String errorCode = "ORA-" + String.format("%05d", sqlException.getErrorCode());
        return new OracleException(message + ": " + sqlException.getMessage(), errorCode);
    }

    private SQLException findSQLException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
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

    public List<String> getAllSchemas(HttpSession session) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay danh sach schema", () ->
                userManagermentRepository.findAllSchemas(credential.username(), credential.password()));
    }

    public List<java.util.Map<String, Object>> getAllTables(HttpSession session) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay danh sach table", () ->
                userManagermentRepository.findAllTables(credential.username(), credential.password()));
    }

    public List<java.util.Map<String, Object>> getAllColumns(HttpSession session) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay danh sach column", () ->
                userManagermentRepository.findAllColumns(credential.username(), credential.password()));
    }

    private record AdminCredential(String username, String password) {
    }
}
