/*
================================================================================
 PATCH FILE 02 (VPD) - Nang cap Manager
================================================================================
 MUC DICH:
  1. Sua VPD SELECT/DML: Manager xem theo PHONG BAN CUA CHINH MINH (tra bang USER_DEPT_MAP).
  2. Them VPD COLUMN MASKING: che EMAIL/PHONE_NUMBER cua nguoi khac (chi thay cua chinh minh).

 CACH CHEN:
  - Chay file nay SAU 02_VPD_BaoMat_UserProfile.sql goc va SAU PATCH_01_RBAC.sql.
  - Tat ca khoi tao function/policy deu CREATE OR REPLACE / drop-truoc nen chay lai duoc.
  - PHASE ghi ro CONNECTION.
================================================================================
*/

SET SERVEROUTPUT ON
SET DEFINE ON

DEFINE SYS_CONN       = "SYS/1234567@localhost:1521/FREEPDB1 AS SYSDBA"
DEFINE APP_TABLE_CONN = "APP_TABLE/123456@localhost:1521/FREEPDB1"

PROMPT ========================================================================
PROMPT PHASE B1 - [CONNECTION: APP_TABLE @ FREEPDB1]
PROMPT          Cap quyen object cho 2 manager (object privilege theo cot)
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN

-- Manager duoc: SELECT, UPDATE 4 cot co ban, DELETE (theo dung ban da sua o file 02 goc).
-- Cac quyen nay da grant cho APP_ROLE_PROFILE_MGR o file 02 -> APP_MANAGER_SALES tu co (cung role).
-- Khong can grant lai.

-- ============================================================================
-- 1. SUA FUNCTION VPD SELECT: Manager xem theo phong ban cua chinh minh
--    Tra bang phu USER_DEPT_MAP de tranh de quy (Cach A).
-- ============================================================================
CREATE OR REPLACE FUNCTION VPD_USER_PROFILE_SELECT_FN(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2
AS
  v_username VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
BEGIN
  -- Admin/owner: xem tat ca.
  IF v_username IN ('SYS', 'SYSTEM', 'APP_TABLE', 'APP_DBA_ADMIN') THEN
    RETURN NULL;
  END IF;

  -- Manager (bat ky): xem nguoi CUNG PHONG BAN voi chinh minh.
  -- Tra phong ban tu bang phu USER_DEPT_MAP (khong bi VPD -> khong de quy).
  IF v_username IN ('APP_MANAGER_PROFILE', 'APP_MANAGER_SALES') THEN
    RETURN 'DEPARTMENT = (SELECT DEPARTMENT FROM APP_TABLE.USER_DEPT_MAP '
        || 'WHERE USERNAME = SYS_CONTEXT(''USERENV'',''SESSION_USER''))';
  END IF;

  -- User thuong: chi xem dong gan voi chinh minh.
  IF v_username = 'APP_USER_1' THEN
    RETURN 'USERNAME = SYS_CONTEXT(''USERENV'', ''SESSION_USER'')';
  END IF;

  -- Mac dinh: an het.
  RETURN '1 = 0';
END;
/
SHOW ERRORS FUNCTION VPD_USER_PROFILE_SELECT_FN

-- ============================================================================
-- 2. SUA FUNCTION VPD DML: cung logic "cung phong ban"
-- ============================================================================
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
    RETURN 'DEPARTMENT = (SELECT DEPARTMENT FROM APP_TABLE.USER_DEPT_MAP '
        || 'WHERE USERNAME = SYS_CONTEXT(''USERENV'',''SESSION_USER''))';
  END IF;
  IF v_username = 'APP_USER_1' THEN
    RETURN 'USERNAME = SYS_CONTEXT(''USERENV'', ''SESSION_USER'')';
  END IF;
  RETURN '1 = 0';
END;
/
SHOW ERRORS FUNCTION VPD_USER_PROFILE_DML_FN

-- ============================================================================
-- 3. FUNCTION COLUMN MASKING: che EMAIL/PHONE cua nguoi KHAC trong cung phong.
--    Quy tac: chi thay EMAIL/PHONE cua DONG la chinh minh (USERNAME = session user).
--    - Admin/owner: RETURN NULL (xem het, khong che).
--    - Con lai: 'USERNAME = session_user' -> chi cac dong cua chinh minh moi hien
--      EMAIL/PHONE; dong cua nguoi khac thi 2 cot do bi NULL (vi dung ALL_ROWS).
-- ============================================================================
CREATE OR REPLACE FUNCTION VPD_COLMASK_FN(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2
AS
  v_username VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
BEGIN
  IF v_username IN ('SYS', 'SYSTEM', 'APP_TABLE', 'APP_DBA_ADMIN') THEN
    RETURN NULL;  -- admin xem het, khong che cot
  END IF;
  -- Chi dong cua chinh minh moi duoc xem EMAIL/PHONE.
  RETURN 'USERNAME = SYS_CONTEXT(''USERENV'', ''SESSION_USER'')';
END;
/
SHOW ERRORS FUNCTION VPD_COLMASK_FN

-- ============================================================================
-- 4. DROP policy col-mask cu (neu co) roi ADD lai
-- ============================================================================
BEGIN
  DBMS_RLS.DROP_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_COLMASK');
  DBMS_OUTPUT.PUT_LINE('Da xoa policy colmask cu');
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua drop colmask: ' || SQLERRM);
END;
/

BEGIN
  DBMS_RLS.ADD_POLICY(
    object_schema         => 'APP_TABLE',
    object_name           => 'USER_PROFILE',
    policy_name           => 'VPD_USER_PROFILE_COLMASK',
    function_schema       => 'APP_TABLE',
    policy_function       => 'VPD_COLMASK_FN',
    statement_types       => 'SELECT',
    sec_relevant_cols     => 'EMAIL,PHONE_NUMBER',
    sec_relevant_cols_opt => DBMS_RLS.ALL_ROWS,  -- tra du dong, che gia tri 2 cot
    policy_type           => DBMS_RLS.DYNAMIC,
    enable                => TRUE
  );
  DBMS_OUTPUT.PUT_LINE('Da tao policy VPD_USER_PROFILE_COLMASK (column masking)');
END;
/

PROMPT ===== KIEM TRA CAC VPD POLICY TREN USER_PROFILE =====
SELECT POLICY_NAME, SEL, INS, UPD, DEL, ENABLE
FROM ALL_POLICIES
WHERE OBJECT_OWNER='APP_TABLE' AND OBJECT_NAME='USER_PROFILE'
ORDER BY POLICY_NAME;

PROMPT ========================================================================
PROMPT KET THUC PATCH 02 - TIEP THEO CHAY PATCH_03_OLS.sql
PROMPT ========================================================================
