package com.hcmute.bmcsdl.nhom3.service;

import com.hcmute.bmcsdl.nhom3.dto.AuditLogDTO;
import com.hcmute.bmcsdl.nhom3.repository.AuditRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

@Service
public class AuditJobService {

    private final AuditRepository auditRepository;

    public AuditJobService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public File exportOnly(String dbUser, String dbPass, String auditType, String username, String dateFrom, String dateTo) throws Exception {
        System.out.println("Bắt đầu Xuất Excel (CSV) cho loại audit: " + auditType);
        return exportToCsv(dbUser, dbPass, auditType, username, dateFrom, dateTo);
    }



    private File exportToCsv(String dbUser, String dbPass, String auditType,
                               String username, String dateFrom, String dateTo) throws Exception {
        
        // Demo export top 1000 records
        List<AuditLogDTO> logs;
        if ("fga".equalsIgnoreCase(auditType)) {
            logs = auditRepository.findFgaAudit(dbUser, dbPass, username, dateFrom, dateTo, 1, 1000);
        } else {
            logs = auditRepository.findUnifiedAudit(dbUser, dbPass, username, dateFrom, dateTo, 1, 1000);
        }

        File exportDir = new File("exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        String fileName = "audit_export_" + System.currentTimeMillis() + ".csv";
        File file = new File(exportDir, fileName);

        try (FileWriter out = new FileWriter(file);
             CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(
                     "Timestamp", "DB User", "OS User", "Action", "Schema", "Object", "Return Code", "SQL Text"))) {
            
            for (AuditLogDTO log : logs) {
                printer.printRecord(
                        log.getEventTimestamp(),
                        log.getDbUsername(),
                        log.getOsUsername(),
                        log.getActionName(),
                        log.getObjectSchema(),
                        log.getObjectName(),
                        log.getReturnCode(),
                        log.getSqlText()
                );
            }
        }
        
        return file;
    }
}
