package com.hcmute.bmcsdl.nhom3.exception;

public class OracleException extends RuntimeException {
    private String errorCode;
    
    public OracleException(String message) {
        super(message);
    }
    
    public OracleException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public OracleException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public OracleException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
