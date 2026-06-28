/*
================================================================================
LAB 02 - VPD / VIRTUAL PRIVATE DATABASE
================================================================================
DO AN: Xay dung ung dung web co chuc nang quan ly nguoi dung
PHAN BAO MAT: VPD tren bang APP_TABLE.USER_PROFILE

PHU THUOC
  - Da chay 01_RBAC_Admin_Functions.sql.
  - Bang APP_TABLE.USER_PROFILE da co cac cot:
      USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL, DEPARTMENT, ROLE_LEVEL, USERNAME

Y TUONG BAO MAT VPD
  Oracle VPD se tu dong gan predicate WHERE vao cau SELECT/UPDATE/DELETE.
  Nguoi dung khong thay predicate nay trong SQL cua minh.

  Chinh sach trong lab:
    - APP_TABLE, APP_DBA_ADMIN: xem/sua tat ca dong.
    - APP_MANAGER_PROFILE    : chi xem/sua/xoa ho so phong HR.
    - APP_USER_1, APP_USER_3 : chi xem/sua ho so co USERNAME = SESSION_USER.
    - User khac              : khong thay dong nao.

  Vi du:
    SELECT * FROM APP_TABLE.USER_PROFILE;
  Khi APP_USER_1 chay se duoc Oracle bien thanh logic tuong duong:
    SELECT * FROM APP_TABLE.USER_PROFILE
    WHERE USERNAME = SYS_CONTEXT('USERENV','SESSION_USER');

HUONG DAN CHAY
  - Sua DEFINE *_CONN neu can.
  - Bam F5 Run Script trong SQL Developer hoac chay bang SQL*Plus/SQLcl.
================================================================================
*/

SET SERVEROUTPUT ON;
SET DEFINE ON;

DEFINE SYS_CONN                  = "SYS/123@//localhost:1521/FREEPDB1 AS SYSDBA"
DEFINE APP_DBA_ADMIN_CONN        = "APP_DBA_ADMIN/123456@//localhost:1521/FREEPDB1"
DEFINE APP_TABLE_CONN            = "APP_TABLE/123456@//localhost:1521/FREEPDB1"
DEFINE APP_USER_1_CONN           = "APP_USER_1/123456@//localhost:1521/FREEPDB1"
DEFINE APP_USER_3_CONN           = "APP_USER_3/123456@//localhost:1521/FREEPDB1"
DEFINE APP_MANAGER_PROFILE_CONN  = "APP_MANAGER_PROFILE/123456@//localhost:1521/FREEPDB1"

PROMPT ========================================================================
PROMPT PHASE 1 - CONNECT SYS: dam bao APP_TABLE co quyen DBMS_RLS truc tiep
PROMPT ========================================================================
CONNECT &SYS_CONN

-- Quyen truc tiep de APP_TABLE goi DBMS_RLS.ADD_POLICY/DROP_POLICY.
GRANT EXECUTE ON SYS.DBMS_RLS TO APP_TABLE;

PROMPT ========================================================================
PROMPT PHASE 2 - CONNECT APP_TABLE: grant object + tao function VPD + add policy
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN

-- ----------------------------------------------------------------------------
-- 1. CAP QUYEN OBJECT CAN THIET CHO DEMO VPD
--    Luu y: VPD khong thay the GRANT. User van can co object privilege truoc,
--    sau do VPD moi loc dong du lieu.
-- ----------------------------------------------------------------------------
GRANT SELECT ON USER_PROFILE TO APP_ROLE_USER;
GRANT UPDATE (FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL) ON USER_PROFILE TO APP_ROLE_USER;

REVOKE DELETE ON USER_PROFILE FROM APP_ROLE_PROFILE_MGR;
REVOKE UPDATE ON USER_PROFILE FROM APP_ROLE_PROFILE_MGR;
GRANT UPDATE (FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL)
ON USER_PROFILE TO APP_ROLE_PROFILE_MGR;
-- GRANT SELECT ON USER_PROFILE TO APP_ROLE_PROFILE_MGR;
-- GRANT UPDATE (FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL, DEPARTMENT, ROLE_LEVEL) ON USER_PROFILE TO APP_ROLE_PROFILE_MGR;
-- GRANT DELETE ON USER_PROFILE TO APP_ROLE_PROFILE_MGR;

GRANT SELECT, INSERT, UPDATE, DELETE ON USER_PROFILE TO APP_ROLE_DB_ADMIN;
GRANT SELECT ON USER_PROFILE TO APP_ROLE_SYSTEM_ADMIN;

-- ----------------------------------------------------------------------------
-- 2. XOA POLICY CU NEU CO
--    - VPD_UPDATE_MYINFO la policy cu trong script tong hop ban dau.
--    - Hai policy moi cua file nay la VPD_USER_PROFILE_SELECT va VPD_USER_PROFILE_DML.
-- ----------------------------------------------------------------------------
BEGIN
  DBMS_RLS.DROP_POLICY(
    object_schema => 'APP_TABLE',
    object_name   => 'USER_PROFILE',
    policy_name   => 'VPD_UPDATE_MYINFO'
  );
  DBMS_OUTPUT.PUT_LINE('Da xoa policy cu VPD_UPDATE_MYINFO');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Bo qua VPD_UPDATE_MYINFO: ' || SQLERRM);
END;
/

BEGIN
  DBMS_RLS.DROP_POLICY(
    object_schema => 'APP_TABLE',
    object_name   => 'USER_PROFILE',
    policy_name   => 'VPD_USER_PROFILE_SELECT'
  );
  DBMS_OUTPUT.PUT_LINE('Da xoa policy cu VPD_USER_PROFILE_SELECT');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Bo qua VPD_USER_PROFILE_SELECT: ' || SQLERRM);
END;
/

BEGIN
  DBMS_RLS.DROP_POLICY(
    object_schema => 'APP_TABLE',
    object_name   => 'USER_PROFILE',
    policy_name   => 'VPD_USER_PROFILE_DML'
  );
  DBMS_OUTPUT.PUT_LINE('Da xoa policy cu VPD_USER_PROFILE_DML');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Bo qua VPD_USER_PROFILE_DML: ' || SQLERRM);
END;
/

-- ----------------------------------------------------------------------------
-- 3. FUNCTION VPD CHO SELECT
--    Function tra ve chuoi predicate. Oracle gan predicate nay vao WHERE.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION VPD_USER_PROFILE_SELECT_FN(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2
AS
  v_username VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
BEGIN
  -- Admin/schema owner duoc xem tat ca nen RETURN NULL nghia la khong them WHERE.
  IF v_username IN ('SYS', 'SYSTEM', 'APP_TABLE', 'APP_DBA_ADMIN') THEN
    RETURN NULL;
  END IF;

  -- Manager demo quan ly phong HR theo du lieu mau trong file 01.
  IF v_username = 'APP_MANAGER_PROFILE' THEN
    RETURN 'DEPARTMENT = ''HR''';
  END IF;

  -- User thuong chi xem dung cac dong gan voi username cua chinh session.
  IF v_username IN ('APP_USER_1', 'APP_USER_3') THEN
    RETURN 'USERNAME = SYS_CONTEXT(''USERENV'', ''SESSION_USER'')';
  END IF;

  -- Mac dinh an het du lieu voi cac user khong nam trong quy tac.
  RETURN '1 = 0';
END;
/

SHOW ERRORS FUNCTION VPD_USER_PROFILE_SELECT_FN;

-- ----------------------------------------------------------------------------
-- 4. FUNCTION VPD CHO UPDATE/DELETE
--    Tach function DML de sau nay co the thay doi rule ghi du lieu doc lap rule doc.
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

  IF v_username = 'APP_MANAGER_PROFILE' THEN
    RETURN 'DEPARTMENT = ''HR''';
  END IF;

  IF v_username IN ('APP_USER_1', 'APP_USER_3') THEN
    RETURN 'USERNAME = SYS_CONTEXT(''USERENV'', ''SESSION_USER'')';
  END IF;

  RETURN '1 = 0';
END;
/

SHOW ERRORS FUNCTION VPD_USER_PROFILE_DML_FN;

-- ----------------------------------------------------------------------------
-- 5. ADD POLICY SELECT
-- ----------------------------------------------------------------------------
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
  DBMS_OUTPUT.PUT_LINE('Da tao policy VPD_USER_PROFILE_SELECT');
END;
/

-- ----------------------------------------------------------------------------
-- 6. ADD POLICY DML
--    update_check => TRUE de Oracle kiem tra ca gia tri sau UPDATE.
--    Vi du APP_USER_1 khong duoc doi USERNAME cua dong minh thanh user khac.
-- ----------------------------------------------------------------------------
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
  DBMS_OUTPUT.PUT_LINE('Da tao policy VPD_USER_PROFILE_DML');
END;
/

-- ----------------------------------------------------------------------------
-- 7. KIEM TRA POLICY DA GAN
-- ----------------------------------------------------------------------------
PROMPT ===== DANH SACH VPD POLICY TREN APP_TABLE.USER_PROFILE =====
SELECT OBJECT_OWNER, OBJECT_NAME, POLICY_NAME, PF_OWNER, "FUNCTION", SEL, INS, UPD, DEL, ENABLE
FROM ALL_POLICIES
WHERE OBJECT_OWNER = 'APP_TABLE'
  AND OBJECT_NAME = 'USER_PROFILE'
ORDER BY POLICY_NAME;

PROMPT ========================================================================
PROMPT PHASE 3 - DEMO VPD VOI APP_USER_1
PROMPT ========================================================================
CONNECT &APP_USER_1_CONN

-- Ky vong: chi thay USER_ID 1 vi USERNAME = APP_USER_1.
SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

-- Ky vong: thanh cong vi USER_ID = 1 thuoc APP_USER_1.
UPDATE APP_TABLE.USER_PROFILE
SET EMAIL = 'app_user_1.updated@company.com'
WHERE USER_ID = 1;
COMMIT;

-- Ky vong: 0 rows updated vi USER_ID = 2 bi VPD loc ra khoi tap du lieu cua APP_USER_1.
UPDATE APP_TABLE.USER_PROFILE
SET EMAIL = 'hack.hr@company.com'
WHERE USER_ID = 2;
COMMIT;

SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT PHASE 3B - DEMO VPD VOI APP_USER_3
PROMPT ========================================================================
CONNECT &APP_USER_3_CONN

-- Ky vong: chi thay USER_ID 4 vi USERNAME = APP_USER_3 (du lieu mau file 01).
SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

-- Ky vong: thanh cong vi USER_ID = 4 thuoc APP_USER_3.
UPDATE APP_TABLE.USER_PROFILE
SET EMAIL = 'app_user_3.updated@company.com'
WHERE USER_ID = 4;
COMMIT;

-- Ky vong: 0 rows updated vi USER_ID = 1 thuoc APP_USER_1, bi VPD loc khoi APP_USER_3.
UPDATE APP_TABLE.USER_PROFILE
SET EMAIL = 'hack.user1@company.com'
WHERE USER_ID = 1;
COMMIT;

SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT PHASE 4 - DEMO VPD VOI APP_MANAGER_PROFILE
PROMPT ========================================================================
CONNECT &APP_MANAGER_PROFILE_CONN

-- Ky vong: chi thay cac dong phong HR.
SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

-- Ky vong: thanh cong vi USER_ID = 2 thuoc phong HR.
UPDATE APP_TABLE.USER_PROFILE
SET PHONE_NUMBER = '0911111111'
WHERE USER_ID = 2;
COMMIT;

-- Ky vong: 0 rows updated vi USER_ID = 1 thuoc SALES, khong phai HR.
UPDATE APP_TABLE.USER_PROFILE
SET PHONE_NUMBER = '0900000000'
WHERE USER_ID = 1;
COMMIT;

SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, PHONE_NUMBER
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT PHASE 5 - DEMO VPD VOI APP_DBA_ADMIN
PROMPT ========================================================================
CONNECT &APP_DBA_ADMIN_CONN

-- Ky vong: thay tat ca dong vi function VPD RETURN NULL cho APP_DBA_ADMIN.
SELECT USER_ID, FULL_NAME, DEPARTMENT, USERNAME, EMAIL, PHONE_NUMBER
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT PHASE 6 - LENH BAT/TAT VPD DE HO TRO DEMO OLS FILE 03
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN

-- Mac dinh de ENABLE = TRUE.
-- Neu muon demo OLS doc lap o file 03, co the tam tat 2 policy nay va bat lai sau.
-- Lenh TAT VPD:
-- BEGIN
--   DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_SELECT', FALSE);
--   DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_DML', FALSE);
-- END;
-- /

-- Lenh BAT LAI VPD:
-- BEGIN
--   DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_SELECT', TRUE);
--   DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_DML', TRUE);
-- END;
-- /

PROMPT ========================================================================
PROMPT KET THUC LAB 02 - TIEP THEO CHAY FILE 03_OLS_BaoMat_UserProfile.sql
PROMPT ========================================================================
