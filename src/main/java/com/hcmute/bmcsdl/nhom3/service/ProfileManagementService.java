package com.hcmute.bmcsdl.nhom3.service;

import com.hcmute.bmcsdl.nhom3.dto.AdminProfileDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.repository.ProfileManagementRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Supplier;

@Service
public class ProfileManagementService {

    private final ProfileManagementRepository profileRepository;

    public ProfileManagementService(ProfileManagementRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public List<AdminProfileDTO> getAllProfiles(HttpSession session) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay danh sach profile", () ->
                profileRepository.findAllProfiles(credential.username(), credential.password()));
    }

    public AdminProfileDTO getProfileDetail(HttpSession session, String profileName) {
        AdminCredential credential = getAdminCredential(session);
        return callOracle("Khong the lay thong tin profile", () ->
                profileRepository.findProfileByName(credential.username(), credential.password(), profileName)
                        .orElseThrow(() -> new OracleException("Khong tim thay profile: " + profileName, "PROFILE_NOT_FOUND")));
    }

    public void createProfile(HttpSession session, AdminProfileDTO dto) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the tao profile", () ->
                profileRepository.createProfile(credential.username(), credential.password(), dto));
    }

    public void updateProfile(HttpSession session, AdminProfileDTO dto) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the cap nhat profile", () ->
                profileRepository.updateProfile(credential.username(), credential.password(), dto));
    }

    public void deleteProfile(HttpSession session, String profileName) {
        AdminCredential credential = getAdminCredential(session);
        runOracle("Khong the xoa profile", () ->
                profileRepository.deleteProfile(credential.username(), credential.password(), profileName));
    }

    private AdminCredential getAdminCredential(HttpSession session) {
        String dbUser = (String) session.getAttribute("dbUser");
        String dbPass = (String) session.getAttribute("dbPass");
        if (dbUser == null || dbPass == null) {
            throw new OracleException("Phien lam viec het han, vui long dang nhap lai!", "SESSION_EXPIRED");
        }
        return new AdminCredential(dbUser, dbPass);
    }

    private <T> T callOracle(String errorMessage, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (DataAccessException e) {
            throw parseException(errorMessage, e);
        }
    }

    private void runOracle(String errorMessage, Runnable runnable) {
        try {
            runnable.run();
        } catch (DataAccessException e) {
            throw parseException(errorMessage, e);
        }
    }

    private OracleException parseException(String actionMessage, DataAccessException e) {
        Throwable root = e.getRootCause();
        if (root instanceof SQLException sqle) {
            return new OracleException(actionMessage + ": " + sqle.getMessage(), String.valueOf(sqle.getErrorCode()));
        }
        return new OracleException(actionMessage + ": " + e.getMessage(), "UNKNOWN");
    }

    private record AdminCredential(String username, String password) {
    }
}
