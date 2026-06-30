/*
================================================================================
04_Audit_Policies.sql - BAN GOM GON / CLEAN
================================================================================
PHAN: Unified Auditing + Fine-Grained Auditing

CHAY SAU:
1. 01_RBAC_Admin.sql
2. 02_VPD_BaoMat_UserProfile.sql
3. 03_OLS_BaoMat_UserProfile.sql

CHUC NANG:
- Unified Auditing:
  + session_audit_policy: logon/logoff
  + user_mgmt_audit_policy: grant/revoke
  + audit_user_profile_all: SELECT/INSERT/UPDATE/DELETE tren APP_TABLE.USER_PROFILE
- FGA:
  + FGA_SELECT_PHONE: SELECT cot PHONE_NUMBER voi PHONE_NUMBER LIKE '090%'
  + FGA_UPDATE_OTHER_USER: UPDATE dong co USERNAME khac SESSION_USER
================================================================================
*/

SET SERVEROUTPUT ON SIZE UNLIMITED;
SET DEFINE ON;
SET SQLBLANKLINES ON;

DEFINE SYS_CONN           = "SYS/1234567@//localhost:1521/FREEPDB1 AS SYSDBA"
DEFINE APP_DBA_ADMIN_CONN = "APP_DBA_ADMIN/123456@//localhost:1521/FREEPDB1"
DEFINE APP_USER_1_CONN    = "APP_USER_1/123456@//localhost:1521/FREEPDB1"

PROMPT ========================================================================
PROMPT PHASE 1 - CONNECT SYS: DROP AUDIT/FGA POLICY CU NEU CO
PROMPT ========================================================================
CONNECT &SYS_CONN

BEGIN
  FOR p IN (
    SELECT 'SESSION_AUDIT_POLICY' policy_name FROM dual UNION ALL
    SELECT 'USER_MGMT_AUDIT_POLICY' FROM dual UNION ALL
    SELECT 'AUDIT_USER_PROFILE_ALL' FROM dual
  ) LOOP
    BEGIN
      EXECUTE IMMEDIATE 'NOAUDIT POLICY ' || p.policy_name;
      DBMS_OUTPUT.PUT_LINE('Da NOAUDIT POLICY ' || p.policy_name);
    EXCEPTION WHEN OTHERS THEN
      DBMS_OUTPUT.PUT_LINE('Bo qua NOAUDIT ' || p.policy_name || ': ' || SQLERRM);
    END;

    BEGIN
      EXECUTE IMMEDIATE 'DROP AUDIT POLICY ' || p.policy_name;
      DBMS_OUTPUT.PUT_LINE('Da DROP AUDIT POLICY ' || p.policy_name);
    EXCEPTION WHEN OTHERS THEN
      DBMS_OUTPUT.PUT_LINE('Bo qua DROP AUDIT POLICY ' || p.policy_name || ': ' || SQLERRM);
    END;
  END LOOP;
END;
/

BEGIN
  FOR p IN (
    SELECT 'FGA_SELECT_PHONE' policy_name FROM dual UNION ALL
    SELECT 'FGA_UPDATE_OTHER_USER' FROM dual
  ) LOOP
    BEGIN
      DBMS_FGA.DROP_POLICY(
        object_schema => 'APP_TABLE',
        object_name   => 'USER_PROFILE',
        policy_name   => p.policy_name
      );
      DBMS_OUTPUT.PUT_LINE('Da drop FGA policy: ' || p.policy_name);
    EXCEPTION WHEN OTHERS THEN
      DBMS_OUTPUT.PUT_LINE('Bo qua drop FGA ' || p.policy_name || ': ' || SQLERRM);
    END;
  END LOOP;
END;
/

PROMPT ========================================================================
PROMPT PHASE 2 - CONNECT SYS: TAO UNIFIED AUDIT POLICIES
PROMPT ========================================================================

PROMPT --- 2.1 Audit logon/logoff
CREATE AUDIT POLICY session_audit_policy
  ACTIONS LOGON, LOGOFF;
AUDIT POLICY session_audit_policy;

PROMPT --- 2.2 Audit GRANT/REVOKE
CREATE AUDIT POLICY user_mgmt_audit_policy
  ACTIONS GRANT, REVOKE;
AUDIT POLICY user_mgmt_audit_policy;

PROMPT --- 2.3 Audit DML tren APP_TABLE.USER_PROFILE
CREATE AUDIT POLICY audit_user_profile_all
  ACTIONS SELECT, INSERT, UPDATE, DELETE ON APP_TABLE.USER_PROFILE;
AUDIT POLICY audit_user_profile_all;

PROMPT ========================================================================
PROMPT PHASE 3 - CONNECT SYS: TAO FGA POLICIES
PROMPT ========================================================================
BEGIN
  DBMS_FGA.ADD_POLICY(
    object_schema   => 'APP_TABLE',
    object_name     => 'USER_PROFILE',
    policy_name     => 'FGA_SELECT_PHONE',
    audit_condition => 'PHONE_NUMBER LIKE ''090%''',
    audit_column    => 'PHONE_NUMBER',
    statement_types => 'SELECT',
    enable          => TRUE
  );

  DBMS_FGA.ADD_POLICY(
    object_schema   => 'APP_TABLE',
    object_name     => 'USER_PROFILE',
    policy_name     => 'FGA_UPDATE_OTHER_USER',
    audit_condition => 'USERNAME <> SYS_CONTEXT(''USERENV'', ''SESSION_USER'')',
    audit_column    => NULL,
    statement_types => 'UPDATE',
    enable          => TRUE
  );
END;
/

PROMPT ========================================================================
PROMPT PHASE 4 - CONNECT SYS: CAP QUYEN XEM/QUAN LY AUDIT CHO APP_DBA_ADMIN
PROMPT ========================================================================
GRANT AUDIT_VIEWER TO APP_DBA_ADMIN;
GRANT AUDIT_ADMIN TO APP_DBA_ADMIN;
GRANT EXECUTE ON SYS.DBMS_AUDIT_MGMT TO APP_DBA_ADMIN;
GRANT SELECT ANY DICTIONARY TO APP_DBA_ADMIN;

PROMPT ===== KIEM TRA UNIFIED AUDIT POLICIES =====
SELECT POLICY_NAME, AUDIT_OPTION, OBJECT_SCHEMA, OBJECT_NAME
FROM AUDIT_UNIFIED_POLICIES
WHERE POLICY_NAME IN ('SESSION_AUDIT_POLICY','USER_MGMT_AUDIT_POLICY','AUDIT_USER_PROFILE_ALL')
ORDER BY POLICY_NAME, AUDIT_OPTION;

PROMPT ===== KIEM TRA FGA POLICIES =====
SELECT OBJECT_SCHEMA, OBJECT_NAME, POLICY_NAME, ENABLED, SEL, INS, UPD, DEL
FROM DBA_AUDIT_POLICIES
WHERE OBJECT_SCHEMA = 'APP_TABLE'
  AND OBJECT_NAME = 'USER_PROFILE'
ORDER BY POLICY_NAME;

PROMPT ========================================================================
PROMPT PHASE 5 - DEMO TAO AUDIT RECORD
PROMPT ========================================================================
CONNECT &APP_USER_1_CONN

PROMPT -- SELECT PHONE_NUMBER de kich hoat FGA_SELECT_PHONE neu co so 090 trong tap thay duoc
SELECT USER_ID, FULL_NAME, PHONE_NUMBER
FROM APP_TABLE.USER_PROFILE
WHERE PHONE_NUMBER LIKE '090%';

PROMPT -- UPDATE dong cua minh de tao record audit DML
UPDATE APP_TABLE.USER_PROFILE
SET ADDRESS = 'Demo audit address - APP_USER_1'
WHERE USER_ID = 1;
COMMIT;

PROMPT ========================================================================
PROMPT PHASE 6 - CONNECT APP_DBA_ADMIN: XEM LOG AUDIT GAN NHAT
PROMPT ========================================================================
CONNECT &APP_DBA_ADMIN_CONN

PROMPT -- Neu vua chay xong ma chua thay log, doi vai giay hoac reconnect lai roi query lai.
SELECT EVENT_TIMESTAMP,
       DBUSERNAME,
       ACTION_NAME,
       OBJECT_SCHEMA,
       OBJECT_NAME,
       RETURN_CODE,
       UNIFIED_AUDIT_POLICIES
FROM UNIFIED_AUDIT_TRAIL
WHERE DBUSERNAME LIKE 'APP_%'
ORDER BY EVENT_TIMESTAMP DESC
FETCH FIRST 30 ROWS ONLY;

PROMPT ========================================================================
PROMPT KET THUC 04_Audit_Policies.sql - HOAN TAT BO 4 FILE CLEAN
PROMPT ========================================================================