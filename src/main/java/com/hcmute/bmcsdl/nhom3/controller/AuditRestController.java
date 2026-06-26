package com.hcmute.bmcsdl.nhom3.controller;

import com.hcmute.bmcsdl.nhom3.service.AuditJobService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditRestController {

    private final AuditJobService auditJobService;

    public AuditRestController(AuditJobService auditJobService) {
        this.auditJobService = auditJobService;
    }

    @PostMapping("/export-job")
    public ResponseEntity<?> exportJob(
            @RequestParam(required = false) String auditType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            HttpSession session) {

        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        String dbUser = (String) session.getAttribute("dbUser");
        String dbPass = (String) session.getAttribute("dbPass");

        try {
            File file = auditJobService.exportOnly(dbUser, dbPass, auditType, username, dateFrom, dateTo);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }



    private boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute("username") != null;
    }
}
