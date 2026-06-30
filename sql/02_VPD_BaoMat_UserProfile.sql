/*
================================================================================
LAB 02 - VPD / VIRTUAL PRIVATE DATABASE
================================================================================
DO AN: Xay dung ung dung web co chuc nang quan ly nguoi dung
PHAN BAO MAT: VPD tren bang APP_TABLE.USER_PROFILE

PHU THUOC
  - Da chay 01_RBAC_Admin.sql
  - Bang APP_TABLE.USER_PROFILE da co cac cot:
      USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL, DEPARTMENT, ROLE_LEVEL, USERNAME
  - Da co bang APP_TABLE.USER_DEPT_MAP de tra phong ban session user

Y TUONG BAO MAT VPD
  - Oracle VPD tu dong gan predicate WHERE vao SELECT/UPDATE/DELETE
  - Manager xem du lieu theo PHONG BAN cua chinh minh
  - User thuong xem du lieu cung phong ban, nhung chi sua du lieu cua chinh minh
  - User thuong bi che EMAIL / PHONE_NUMBER cua nguoi khac trong cung phong ban

HUONG DAN CHAY
  - Sua DEFINE *_CONN neu can
  - Chay bang F5 trong SQL Developer
================================================================================
*/

SET SERVEROUTPUT ON;
SET DEFINE ON;

DEFINE SYS_CONN                 = "SYS/123@//localhost:1521/FREEPDB1 AS SYSDBA"
DEFINE APP_TABLE_CONN           = "APP_TABLE/123456@//localhost:1521/FREEPDB1"
DEFINE APP_DBA_ADMIN_CONN       = "APP_DBA_ADMIN/123456@//localhost:1521/FREEPDB1"
DEFINE APP_USER_1_CONN          = "APP_USER_1/123456@//localhost:1521/FREEPDB1"
DEFINE APP_USER_3_CONN          = "APP_USER_3/123456@//localhost:1521/FREEPDB1"
DEFINE APP_MANAGER_PROFILE_CONN = "APP_MANAGER_PROFILE/123456@//localhost:1521/FREEPDB1"
DEFINE APP_MANAGER_SALES_CONN   = "APP_MANAGER_SALES/123456@//localhost:1521/FREEPDB1"

PROMPT ========================================================================
PROMPT PHASE 1 - CONNECT SYS: dam bao APP_TABLE co quyen DBMS_RLS
PROMPT ========================================================================

CONNECT &SYS_CONN

GRANT EXECUTE ON SYS.DBMS_RLS TO APP_TABLE;

PROMPT ========================================================================
PROMPT PHASE 2 - CONNECT APP_TABLE: cap quyen object can thiet
PROMPT ========================================================================

CONNECT &APP_TABLE_CONN

-- ----------------------------------------------------------------------------
-- 1. OBJECT PRIVILEGES
-- ----------------------------------------------------------------------------

-- User thuong
GRANT SELECT ON USER_PROFILE TO APP_ROLE_USER;
GRANT UPDATE (FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL) ON USER_PROFILE TO APP_ROLE_USER;
GRANT SELECT ON USER_DEPT_MAP TO APP_ROLE_USER;

-- Manager
GRANT SELECT ON USER_PROFILE TO APP_ROLE_PROFILE_MGR;
GRANT UPDATE (FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL) ON USER_PROFILE TO APP_ROLE_PROFILE_MGR;
GRANT DELETE ON USER_PROFILE TO APP_ROLE_PROFILE_MGR;
GRANT SELECT ON USER_DEPT_MAP TO APP_ROLE_PROFILE_MGR;

-- Admin
GRANT SELECT, INSERT, UPDATE, DELETE ON USER_PROFILE TO APP_ROLE_DB_ADMIN;
GRANT SELECT ON USER_PROFILE TO APP_ROLE_SYSTEM_ADMIN;
GRANT SELECT ON USER_DEPT_MAP TO APP_DBA_ADMIN;

PROMPT ========================================================================
PROMPT PHASE 3 - CONNECT APP_TABLE: xoa VPD policy cu neu co
PROMPT ========================================================================

-- ----------------------------------------------------------------------------
-- 2. XOA POLICY CU NEU CO
-- ----------------------------------------------------------------------------

BEGIN
  DBMS_RLS.DROP_POLICY(
    object_schema => 'APP_TABLE',
    object_name   => 'USER_PROFILE',
    policy_name   => 'VPD_UPDATE_MYINFO'
  );
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

BEGIN
  DBMS_RLS.DROP_POLICY(
    object_schema => 'APP_TABLE',
    object_name   => 'USER_PROFILE',
    policy_name   => 'VPD_USER_PROFILE_SELECT'
  );
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

BEGIN
  DBMS_RLS.DROP_POLICY(
    object_schema => 'APP_TABLE',
    object_name   => 'USER_PROFILE',
    policy_name   => 'VPD_USER_PROFILE_DML'
  );
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

BEGIN
  DBMS_RLS.DROP_POLICY(
    object_schema => 'APP_TABLE',
    object_name   => 'USER_PROFILE',
    policy_name   => 'VPD_USER_PROFILE_COLMASK'
  );
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

PROMPT ========================================================================
PROMPT PHASE 4 - CONNECT APP_TABLE: tao function VPD SELECT
PROMPT ========================================================================

-- ----------------------------------------------------------------------------
-- 3. FUNCTION VPD CHO SELECT
--    Admin/owner: thay tat ca
--    Manager: thay nguoi cung phong ban cua chinh minh
--    User thuong: thay nguoi cung phong ban cua chinh minh
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION VPD_USER_PROFILE_SELECT_FN(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2
AS
  v_username VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
BEGIN
  IF v_username IN ('SYS', 'SYSTEM', 'APP_TABLE', 'APP_DBA_ADMIN') THEN
    RETURN NULL;
  END IF;

  IF v_username IN ('APP_MANAGER_PROFILE', 'APP_MANAGER_SALES') THEN
    RETURN 'DEPARTMENT = (SELECT DEPARTMENT FROM APP_TABLE.USER_DEPT_MAP ' ||
           'WHERE USERNAME = SYS_CONTEXT(''USERENV'',''SESSION_USER''))';
  END IF;

  IF v_username IN ('APP_USER_1', 'APP_USER_3') THEN
    RETURN 'DEPARTMENT = (SELECT DEPARTMENT FROM APP_TABLE.USER_DEPT_MAP ' ||
           'WHERE USERNAME = SYS_CONTEXT(''USERENV'',''SESSION_USER''))';
  END IF;

  RETURN '1 = 0';
END;
/

SHOW ERRORS FUNCTION VPD_USER_PROFILE_SELECT_FN;

PROMPT ========================================================================
PROMPT PHASE 5 - CONNECT APP_TABLE: tao function VPD DML
PROMPT ========================================================================

-- ----------------------------------------------------------------------------
-- 4. FUNCTION VPD CHO UPDATE / DELETE
--    Manager: thao tac trong cung phong ban
--    User thuong: chi sua dong cua chinh minh
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION VPD_USER_PROFILE_DML_FN(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2
AS
  v_username VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
BEGIN
  IF v_username IN ('SYS', 'SYSTEM', 'APP_TABLE', 'APP_DBA_ADMIN') THEN
    RETURN NULL;
  END IF;

  IF v_username IN ('APP_MANAGER_PROFILE', 'APP_MANAGER_SALES') THEN
    RETURN 'DEPARTMENT = (SELECT DEPARTMENT FROM APP_TABLE.USER_DEPT_MAP ' ||
           'WHERE USERNAME = SYS_CONTEXT(''USERENV'',''SESSION_USER''))';
  END IF;

  IF v_username IN ('APP_USER_1', 'APP_USER_3') THEN
    RETURN 'USERNAME = SYS_CONTEXT(''USERENV'', ''SESSION_USER'')';
  END IF;

  RETURN '1 = 0';
END;
/

SHOW ERRORS FUNCTION VPD_USER_PROFILE_DML_FN;

PROMPT ========================================================================
PROMPT PHASE 6 - CONNECT APP_TABLE: tao function VPD COLUMN MASKING
PROMPT ========================================================================

-- ----------------------------------------------------------------------------
-- 5. FUNCTION COLUMN MASKING
--    Admin + Manager: xem het EMAIL / PHONE_NUMBER
--    User thuong: chi thay EMAIL / PHONE_NUMBER cua chinh minh
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION VPD_COLMASK_FN(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2
AS
  v_username VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
BEGIN
  IF v_username IN ('SYS', 'SYSTEM', 'APP_TABLE', 'APP_DBA_ADMIN', 'APP_MANAGER_PROFILE', 'APP_MANAGER_SALES') THEN
    RETURN NULL;
  END IF;

  RETURN 'USERNAME = SYS_CONTEXT(''USERENV'', ''SESSION_USER'')';
END;
/

SHOW ERRORS FUNCTION VPD_COLMASK_FN;

PROMPT ========================================================================
PROMPT PHASE 7 - CONNECT APP_TABLE: kiem tra status cac function
PROMPT ========================================================================

SELECT OBJECT_NAME, STATUS
FROM USER_OBJECTS
WHERE OBJECT_NAME IN (
  'VPD_USER_PROFILE_SELECT_FN',
  'VPD_USER_PROFILE_DML_FN',
  'VPD_COLMASK_FN'
)
ORDER BY OBJECT_NAME;

PROMPT ========================================================================
PROMPT PHASE 8 - CONNECT APP_TABLE: gan policy SELECT
PROMPT ========================================================================

BEGIN
  DBMS_RLS.ADD_POLICY(
    object_schema   => 'APP_TABLE',
    object_name     => 'USER_PROFILE',
    policy_name     => 'VPD_USER_PROFILE_SELECT',
    function_schema => 'APP_TABLE',
    policy_function => 'VPD_USER_PROFILE_SELECT_FN',
    statement_types => 'SELECT',
    policy_type     => DBMS_RLS.DYNAMIC,
    enable          => TRUE
  );
END;
/

PROMPT ========================================================================
PROMPT PHASE 9 - CONNECT APP_TABLE: gan policy UPDATE / DELETE
PROMPT ========================================================================

BEGIN
  DBMS_RLS.ADD_POLICY(
    object_schema   => 'APP_TABLE',
    object_name     => 'USER_PROFILE',
    policy_name     => 'VPD_USER_PROFILE_DML',
    function_schema => 'APP_TABLE',
    policy_function => 'VPD_USER_PROFILE_DML_FN',
    statement_types => 'UPDATE,DELETE',
    update_check    => TRUE,
    policy_type     => DBMS_RLS.DYNAMIC,
    enable          => TRUE
  );
END;
/

PROMPT ========================================================================
PROMPT PHASE 10 - CONNECT APP_TABLE: gan policy COLUMN MASKING
PROMPT ========================================================================

BEGIN
  DBMS_RLS.ADD_POLICY(
    object_schema         => 'APP_TABLE',
    object_name           => 'USER_PROFILE',
    policy_name           => 'VPD_USER_PROFILE_COLMASK',
    function_schema       => 'APP_TABLE',
    policy_function       => 'VPD_COLMASK_FN',
    statement_types       => 'SELECT',
    sec_relevant_cols     => 'EMAIL,PHONE_NUMBER',
    sec_relevant_cols_opt => DBMS_RLS.ALL_ROWS,
    policy_type           => DBMS_RLS.DYNAMIC,
    enable                => TRUE
  );
END;
/

PROMPT ========================================================================
PROMPT PHASE 11 - KIEM TRA CAC VPD POLICY
PROMPT ========================================================================

SELECT POLICY_NAME, SEL, INS, UPD, DEL, ENABLE
FROM ALL_POLICIES
WHERE OBJECT_OWNER = 'APP_TABLE'
  AND OBJECT_NAME  = 'USER_PROFILE'
ORDER BY POLICY_NAME;

PROMPT ========================================================================
PROMPT PHASE 12 - DEMO KIEM TRA NHANH
PROMPT ========================================================================

CONNECT &APP_MANAGER_PROFILE_CONN

PROMPT ===== MANAGER HR NHIN THAY HO SO NAO =====
SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL, PHONE_NUMBER
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

CONNECT &APP_MANAGER_SALES_CONN

PROMPT ===== MANAGER SALES NHIN THAY HO SO NAO =====
SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL, PHONE_NUMBER
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

CONNECT &APP_USER_1_CONN

PROMPT ===== APP_USER_1 NHIN THAY CUNG PHONG, NHUNG EMAIL/PHONE CUA NGUOI KHAC BI AN =====
SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL, PHONE_NUMBER
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

CONNECT &APP_USER_3_CONN

PROMPT ===== APP_USER_3 NHIN THAY CUNG PHONG, NHUNG CHI SUA DUOC DONG CUA CHINH MINH =====
SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL, PHONE_NUMBER
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

CONNECT &APP_DBA_ADMIN_CONN

PROMPT ===== APP_DBA_ADMIN THAY TAT CA =====
SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL, PHONE_NUMBER
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT KET THUC 02_VPD_BaoMat_UserProfile.sql
PROMPT ========================================================================