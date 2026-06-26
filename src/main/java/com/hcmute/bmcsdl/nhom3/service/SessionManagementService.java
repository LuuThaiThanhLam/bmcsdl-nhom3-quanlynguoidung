package com.hcmute.bmcsdl.nhom3.service;

import com.hcmute.bmcsdl.nhom3.dto.SessionDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.repository.SessionRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SessionManagementService {

    private final SessionRepository sessionRepository;

    public SessionManagementService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public List<SessionDTO> getAllSessions(HttpSession session) {
        String dbUser = (String) session.getAttribute("dbUser");
        String dbPass = (String) session.getAttribute("dbPass");
        try {
            return sessionRepository.getAllSessions(dbUser, dbPass);
        } catch (Exception e) {
            throw new OracleException(e.getMessage(), e);
        }
    }

    public void killSession(HttpSession session, String sid, String serialNum) {
        String dbUser = (String) session.getAttribute("dbUser");
        String dbPass = (String) session.getAttribute("dbPass");
        try {
            sessionRepository.killSession(dbUser, dbPass, sid, serialNum);
        } catch (Exception e) {
            throw new OracleException(e.getMessage(), e);
        }
    }
}
