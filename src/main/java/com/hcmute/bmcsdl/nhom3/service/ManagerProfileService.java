package com.hcmute.bmcsdl.nhom3.service;

import com.hcmute.bmcsdl.nhom3.dto.ManagerProfileDTO;
import com.hcmute.bmcsdl.nhom3.dto.ManagerSelfInfoDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.repository.ManagerProfileRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Service cho Manager. Lay credential cua chinh Manager tu session de moi cau lenh
 * chay duoi quyen Manager -> VPD/OLS/object-privilege phat huy.
 */
@Service
public class ManagerProfileService {

    private final ManagerProfileRepository repository;

    public ManagerProfileService(ManagerProfileRepository repository) {
        this.repository = repository;
    }

    // ---- Phan 1: USER_PROFILE ----

    public List<ManagerProfileDTO> getVisibleProfiles(HttpSession session) {
        Credential c = credential(session);
        return callOracle("Khong the lay danh sach ho so", () ->
                repository.findVisibleProfiles(c.username(), c.password()));
    }

    public ManagerProfileDTO getProfile(HttpSession session, long userId) {
        Credential c = credential(session);
        return callOracle("Khong the lay thong tin ho so", () ->
                repository.findById(c.username(), c.password(), userId));
    }

    public int updateBasicInfo(HttpSession session, ManagerProfileDTO dto) {
        Credential c = credential(session);
        return callOracle("Khong the cap nhat ho so", () ->
                repository.updateBasicInfo(c.username(), c.password(), dto));
    }

    public int deleteProfile(HttpSession session, long userId) {
        Credential c = credential(session);
        return callOracle("Khong the xoa ho so", () ->
                repository.deleteProfile(c.username(), c.password(), userId));
    }

    public void upgradeLabel(HttpSession session, long userId) {
        Credential c = credential(session);
        runOracle("Khong the nang nhan OLS", () ->
                repository.upgradeLabel(c.username(), c.password(), userId));
    }

    public void downgradeLabel(HttpSession session, long userId) {
        Credential c = credential(session);
        runOracle("Khong the ha nhan OLS", () ->
                repository.downgradeLabel(c.username(), c.password(), userId));
    }

    // ---- Phan 2: thong tin cua chinh minh ----

    public String getMyDepartment(HttpSession session) {
        Credential c = credential(session);
        return callOracle("Khong the lay phong ban", () ->
                repository.findMyDepartment(c.username(), c.password()));
    }

    public ManagerSelfInfoDTO getMyAccountInfo(HttpSession session) {
        Credential c = credential(session);
        return callOracle("Khong the lay thong tin tai khoan", () -> {
            ManagerSelfInfoDTO dto = new ManagerSelfInfoDTO();
            dto.setAccount(repository.findMyAccount(c.username(), c.password()));
            dto.setDepartment(repository.findMyDepartment(c.username(), c.password()));
            return dto;
        });
    }

    public ManagerSelfInfoDTO getMyRolesInfo(HttpSession session) {
        Credential c = credential(session);
        return callOracle("Khong the lay thong tin role/quyen", () -> {
            ManagerSelfInfoDTO dto = new ManagerSelfInfoDTO();
            dto.setSessionRoles(repository.findMySessionRoles(c.username(), c.password()));
            dto.setRolePrivs(repository.findMyRolePrivs(c.username(), c.password()));
            dto.setSysPrivs(repository.findMySysPrivs(c.username(), c.password()));
            dto.setTabPrivs(repository.findMyTabPrivs(c.username(), c.password()));
            dto.setColPrivs(repository.findMyColPrivs(c.username(), c.password()));
            return dto;
        });
    }

    public ManagerSelfInfoDTO getMyProfileInfo(HttpSession session) {
        Credential c = credential(session);
        return callOracle("Khong the lay thong tin profile", () -> {
            ManagerSelfInfoDTO dto = new ManagerSelfInfoDTO();
            dto.setProfileLimits(repository.findMyProfileLimits(c.username(), c.password()));
            return dto;
        });
    }

    public ManagerSelfInfoDTO getMyOlsInfo(HttpSession session) {
        Credential c = credential(session);
        return callOracle("Khong the lay thong tin nhan OLS", () -> {
            ManagerSelfInfoDTO dto = new ManagerSelfInfoDTO();
            dto.setOlsLabel(repository.findMyOlsLabel(c.username(), c.password()));
            return dto;
        });
    }

    // ---- helpers ----

    private Credential credential(HttpSession session) {
        if (session == null) {
            throw new OracleException("Phien dang nhap khong hop le", "SESSION_INVALID");
        }
        Object dbUser = session.getAttribute("dbUser");
        Object dbPass = session.getAttribute("dbPass");
        if (dbUser == null || dbPass == null) {
            throw new OracleException("Chua dang nhap hoac phien da het han", "SESSION_EXPIRED");
        }
        return new Credential(dbUser.toString(), dbPass.toString());
    }

    private void runOracle(String message, Runnable action) {
        callOracle(message, () -> { action.run(); return null; });
    }

    private <T> T callOracle(String message, Supplier<T> action) {
        try {
            return action.get();
        } catch (OracleException e) {
            throw e;
        } catch (DataAccessException e) {
            SQLException sqle = findSQLException(e);
            if (sqle != null) {
                String code = "ORA-" + String.format("%05d", sqle.getErrorCode());
                throw new OracleException(message + ": " + sqle.getMessage(), code);
            }
            throw new OracleException(message + ": " + e.getMostSpecificCause().getMessage(), "ORACLE_ERROR");
        }
    }

    private SQLException findSQLException(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException s) return s;
            t = t.getCause();
        }
        return null;
    }

    private record Credential(String username, String password) {}
}
