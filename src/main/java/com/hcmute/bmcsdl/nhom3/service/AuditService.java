package com.hcmute.bmcsdl.nhom3.service;

import com.hcmute.bmcsdl.nhom3.dto.AuditLogDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.repository.AuditRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Supplier;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public List<String> getAuditedUsernames(HttpSession session) {
        AdminCredential c = getAdminCredential(session);
        return callOracle("Khong the lay danh sach username tu audit trail",
                () -> auditRepository.findAuditedUsernames(c.username(), c.password()));
    }

    public List<String> getFgaAuditedUsernames(HttpSession session) {
        AdminCredential c = getAdminCredential(session);
        return callOracle("Khong the lay danh sach username tu FGA audit trail",
                () -> auditRepository.findFgaAuditedUsernames(c.username(), c.password()));
    }

    public List<AuditLogDTO> getUnifiedAudit(HttpSession session, String usernameFilter,
                                              String dateFrom, String dateTo, int page, int pageSize) {
        AdminCredential c = getAdminCredential(session);
        return callOracle("Khong the lay Unified Audit Trail",
                () -> auditRepository.findUnifiedAudit(c.username(), c.password(), usernameFilter, dateFrom, dateTo, page, pageSize));
    }

    public int countUnifiedAudit(HttpSession session, String usernameFilter, String dateFrom, String dateTo) {
        AdminCredential c = getAdminCredential(session);
        return callOracle("Khong the dem Unified Audit Trail",
                () -> auditRepository.countUnifiedAudit(c.username(), c.password(), usernameFilter, dateFrom, dateTo));
    }

    public List<AuditLogDTO> getFgaAudit(HttpSession session, String usernameFilter,
                                          String dateFrom, String dateTo, int page, int pageSize) {
        AdminCredential c = getAdminCredential(session);
        return callOracle("Khong the lay FGA Audit Trail",
                () -> auditRepository.findFgaAudit(c.username(), c.password(), usernameFilter, dateFrom, dateTo, page, pageSize));
    }

    public int countFgaAudit(HttpSession session, String usernameFilter, String dateFrom, String dateTo) {
        AdminCredential c = getAdminCredential(session);
        return callOracle("Khong the dem FGA Audit Trail",
                () -> auditRepository.countFgaAudit(c.username(), c.password(), usernameFilter, dateFrom, dateTo));
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
        } catch (OracleException e) {
            throw e;
        } catch (DataAccessException e) {
            Throwable root = e.getRootCause();
            if (root instanceof SQLException sqle) {
                throw new OracleException(errorMessage + ": " + sqle.getMessage(),
                        String.valueOf(sqle.getErrorCode()));
            }
            throw new OracleException(errorMessage + ": " + e.getMessage(), "UNKNOWN");
        }
    }

    private record AdminCredential(String username, String password) {}
}
