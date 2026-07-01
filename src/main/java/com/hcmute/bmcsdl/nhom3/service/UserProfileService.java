package com.hcmute.bmcsdl.nhom3.service;

import com.hcmute.bmcsdl.nhom3.dto.DirectoryEntryDTO;
import com.hcmute.bmcsdl.nhom3.dto.UserProfileDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.repository.UserProfileRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Supplier;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public List<UserProfileDTO> getMyProfiles(HttpSession session) {
        Credential credential = getCredential(session);
        try {
            // Uu tien ban co nhan OLS (file 03) de demo tren giao dien.
            return userProfileRepository.findMyProfilesWithOls(credential.username(), credential.password());
        } catch (DataAccessException olsNotReady) {
            // OLS chua kich hoat (cot OLS_LABEL chua ton tai) -> fallback khong nhan.
            return callOracle("Khong the lay thong tin ho so cua ban", () ->
                    userProfileRepository.findMyProfiles(credential.username(), credential.password()));
        }
    }

    /**
     * Nhan READ cua phien user dang dang nhap (OLS). Tra null neu OLS chua kich hoat,
     * de giao dien tu hieu la "khong co OLS".
     */
    public String getMySessionReadLabel(HttpSession session) {
        Credential credential = getCredential(session);
        try {
            return userProfileRepository.findMySessionReadLabel(credential.username(), credential.password());
        } catch (DataAccessException olsNotReady) {
            return null;
        }
    }

    public UserProfileDTO getMyProfile(HttpSession session, long userId) {
        Credential credential = getCredential(session);
        return callOracle("Khong the lay ho so", () ->
                userProfileRepository.findMyProfileById(credential.username(), credential.password(), userId)
                        .orElseThrow(() -> new OracleException(
                                "Khong tim thay ho so hoac ban khong co quyen xem ho so nay", "PROFILE_NOT_VISIBLE")));
    }

    /** VPD DML chi cho sua dong co USERNAME trung voi user dang dang nhap. */
    public boolean canEditProfile(HttpSession session, UserProfileDTO profile) {
        if (profile == null || profile.getUsername() == null) {
            return false;
        }
        Credential credential = getCredential(session);
        return profile.getUsername().equalsIgnoreCase(credential.username());
    }

    public void updateMyProfile(HttpSession session, UserProfileDTO profile) {
        if (profile == null || profile.getUserId() == null) {
            throw new OracleException("Thieu ma ho so can cap nhat", "INVALID_PROFILE_DATA");
        }
        Credential credential = getCredential(session);
        int affected = callOracle("Khong the cap nhat ho so", () ->
                userProfileRepository.updateMyProfile(credential.username(), credential.password(), profile));

        if (affected == 0) {
            // VPD da chan: dong khong thuoc ve user dang nhap.
            throw new OracleException(
                    "Khong cap nhat duoc dong nao. Ban chi duoc sua ho so thuoc ve chinh minh (VPD).",
                    "VPD_NO_ROWS");
        }
    }

    public List<DirectoryEntryDTO> getDirectory(HttpSession session) {
        Credential credential = getCredential(session);
        return callOracle("Khong the lay danh ba", () ->
                userProfileRepository.findDirectory(credential.username(), credential.password()));
    }

    private Credential getCredential(HttpSession session) {
        if (session == null) {
            throw new OracleException("Phien dang nhap khong hop le", "SESSION_INVALID");
        }
        Object dbUser = session.getAttribute("dbUser");
        Object dbPass = session.getAttribute("dbPass");
        if (dbUser == null || dbPass == null) {
            throw new OracleException("Chua dang nhap hoac phien dang nhap da het han", "SESSION_EXPIRED");
        }
        return new Credential(dbUser.toString(), dbPass.toString());
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

    private record Credential(String username, String password) {
    }
}
