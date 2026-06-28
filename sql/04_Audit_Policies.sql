/*
================================================================================
LAB 04 - AUDITING (UNIFIED AUDITING & FINE-GRAINED AUDITING)
================================================================================
YÊU CẦU:
1. Unified Auditing:
   - session_audit_policy: Giám sát Logon/Logoff
   - user_mgmt_audit_policy: Giám sát GRANT, REVOKE
   - audit_user_profile_all: Giám sát toàn bộ thao tác DML trên bảng APP_TABLE.USER_PROFILE
2. Fine-Grained Auditing (FGA):
   - FGA_SELECT_PHONE: SELECT cột PHONE_NUMBER khi bắt đầu bằng '090'
   - FGA_UPDATE_OTHER_USER: UPDATE dữ liệu của user khác (USERNAME != SESSION_USER)
================================================================================
*/

SET SERVEROUTPUT ON;
-- Kết nối bằng tài khoản SYS để cấu hình Audit
-- DEFINE SYS_CONN = "SYS/your_sys_password AS SYSDBA"
-- CONNECT &SYS_CONN

PROMPT ========================================================================
PROMPT PHAN 1: UNIFIED AUDITING
PROMPT ========================================================================

-- 1. Policy: session_audit_policy (Giám sát Login/Logout)
CREATE AUDIT POLICY session_audit_policy ACTIONS LOGON, LOGOFF;
AUDIT POLICY session_audit_policy;

-- 2. Policy: user_mgmt_audit_policy (Giám sát Cáp/Thu quyền)
CREATE AUDIT POLICY user_mgmt_audit_policy ACTIONS GRANT, REVOKE;
AUDIT POLICY user_mgmt_audit_policy;

-- 3. Policy: audit_user_profile_all (Giám sát DML trên bảng USER_PROFILE theo yêu cầu phụ)
CREATE AUDIT POLICY audit_user_profile_all ACTIONS ALL ON APP_TABLE.USER_PROFILE;
AUDIT POLICY audit_user_profile_all;

PROMPT ========================================================================
PROMPT PHAN 2: FINE-GRAINED AUDITING (FGA)
PROMPT ========================================================================

BEGIN
  -- 1. FGA_SELECT_PHONE: Giám sát SELECT cột PHONE_NUMBER khi số bắt đầu bằng '090'
  DBMS_FGA.ADD_POLICY(
    object_schema   => 'APP_TABLE',
    object_name     => 'USER_PROFILE',
    policy_name     => 'FGA_SELECT_PHONE',
    audit_condition => 'PHONE_NUMBER LIKE ''090%''',
    audit_column    => 'PHONE_NUMBER',
    statement_types => 'SELECT'
  );

  -- 2. FGA_UPDATE_OTHER_USER: Giám sát UPDATE khi cập nhật dữ liệu của user khác
  -- Condition: Cột USERNAME trong bảng khác với tài khoản đang Login (SESSION_USER)
  DBMS_FGA.ADD_POLICY(
    object_schema   => 'APP_TABLE',
    object_name     => 'USER_PROFILE',
    policy_name     => 'FGA_UPDATE_OTHER_USER',
    audit_condition => 'USERNAME != SYS_CONTEXT(''USERENV'', ''SESSION_USER'')',
    audit_column    => NULL, -- NULL có nghĩa là áp dụng khi UPDATE bất kỳ cột nào
    statement_types => 'UPDATE'
  );
END;
/

PROMPT ========================================================================
PROMPT PHAN 3: CAP QUYEN QUAN LY AUDIT CHO ADMIN
PROMPT ========================================================================
-- Cấp quyền cho APP_DBA_ADMIN để có thể thực thi lệnh xóa (Purge) Audit Logs từ ứng dụng
GRANT EXECUTE ON DBMS_AUDIT_MGMT TO APP_DBA_ADMIN;

PROMPT ========================================================================
PROMPT KET THUC CAU HINH AUDITING
PROMPT ========================================================================
