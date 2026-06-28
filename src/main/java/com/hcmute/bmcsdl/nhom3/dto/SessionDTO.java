package com.hcmute.bmcsdl.nhom3.dto;

import java.time.LocalDateTime;

public class SessionDTO {
    private String sid;
    private String serialNum;
    private String username;
    private String status;
    private String osUser;
    private String machine;
    private String program;
    private LocalDateTime logonTime;

    public SessionDTO() {}

    public SessionDTO(String sid, String serialNum, String username, String status, String osUser, String machine, String program, LocalDateTime logonTime) {
        this.sid = sid;
        this.serialNum = serialNum;
        this.username = username;
        this.status = status;
        this.osUser = osUser;
        this.machine = machine;
        this.program = program;
        this.logonTime = logonTime;
    }

    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }

    public String getSerialNum() { return serialNum; }
    public void setSerialNum(String serialNum) { this.serialNum = serialNum; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOsUser() { return osUser; }
    public void setOsUser(String osUser) { this.osUser = osUser; }

    public String getMachine() { return machine; }
    public void setMachine(String machine) { this.machine = machine; }

    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }

    public LocalDateTime getLogonTime() { return logonTime; }
    public void setLogonTime(LocalDateTime logonTime) { this.logonTime = logonTime; }
}
