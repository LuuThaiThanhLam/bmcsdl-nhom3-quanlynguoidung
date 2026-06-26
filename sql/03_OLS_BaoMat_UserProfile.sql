/*
================================================================================
LAB 03 - OLS / ORACLE LABEL SECURITY
================================================================================
DO AN: Xay dung ung dung web co chuc nang quan ly nguoi dung
PHAN BAO MAT: Oracle Label Security tren bang APP_TABLE.USER_PROFILE

PHU THUOC
  - Da chay 01_RBAC_Admin_Functions.sql.
  - Nen chay 02_VPD_BaoMat_UserProfile.sql truoc de du quy trinh RBAC -> VPD -> OLS.
  - Database da cai va enable Oracle Label Security.

Y TUONG OLS
  OLS gan nhan bao mat vao tung dong du lieu. User chi doc/ghi duoc dong co nhan
  nam trong mien nhan ma user duoc cap.

  Policy         : USER_PROFILE_OLS
  Cot nhan        : OLS_LABEL
  Level           : PUB = public, PRI = private
  Group           : ALL, SALES, HR, IT

  Luu y cu phap label:
    - Label co dang: LEVEL:COMPARTMENT:GROUP
    - Lab nay khong dung compartment, chi dung group phong ban.
    - Vi vay phai viet 2 dau hai cham khi gan group:
        PUB::SALES
        PRI::HR
      Khong viet PUB:SALES, vi Oracle se hieu SALES la compartment.

  Quyen demo:
    - APP_USER_1          : doc/ghi du lieu PUBLIC trong group ALL va cac subgroup.
    - APP_MANAGER_PROFILE : doc/ghi PRIVATE cua phong HR.
    - APP_DBA_ADMIN       : co dac quyen READ, xem tat ca label.
    - APP_TABLE           : co dac quyen FULL de owner/procedure gan nhan du lieu.

HUONG DAN CHAY
  1. Sua DEFINE *_CONN cho dung password/service.
  2. Chay file bang F5.
  3. Sau khi SET_USER_PRIVS/SET_USER_LABELS, nen reconnect user demo de nhan quyen OLS moi.

NEU OLS CHUA ENABLE
  Chay kiem tra trong PHASE 1. Neu DBA_REGISTRY khong co OLS = VALID hoac DBA_OLS_STATUS
  bao FALSE, can enable OLS trong PDB truoc roi chay lai file nay.
================================================================================
*/

SET SERVEROUTPUT ON;
SET DEFINE ON;

DEFINE SYS_CONN                 = "SYS/123@//localhost:1521/FREEPDB1 AS SYSDBA"
DEFINE LBACSYS_CONN             = "LBACSYS/lbacsys@//localhost:1521/FREEPDB1"
DEFINE APP_OLS_MGR_CONN         = "APP_OLS_MGR/123456@//localhost:1521/FREEPDB1"
DEFINE APP_TABLE_CONN           = "APP_TABLE/123456@//localhost:1521/FREEPDB1"
DEFINE APP_DBA_ADMIN_CONN       = "APP_DBA_ADMIN/123456@//localhost:1521/FREEPDB1"
DEFINE APP_USER_1_CONN          = "APP_USER_1/123456@//localhost:1521/FREEPDB1"
DEFINE APP_MANAGER_PROFILE_CONN = "APP_MANAGER_PROFILE/123456@//localhost:1521/FREEPDB1"

PROMPT ========================================================================
PROMPT PHASE 1 - CONNECT SYS: kiem tra OLS va cap quyen goi package OLS
PROMPT ========================================================================
CONNECT &SYS_CONN

-- ----------------------------------------------------------------------------
-- 1. KIEM TRA OLS DA CAI VA ENABLE CHUA
-- ----------------------------------------------------------------------------
PROMPT ===== KIEM TRA COMPONENT OLS =====
SELECT COMP_ID, VERSION, STATUS
FROM DBA_REGISTRY
WHERE COMP_ID = 'OLS';

PROMPT ===== KIEM TRA TRANG THAI OLS =====
SELECT NAME, STATUS
FROM DBA_OLS_STATUS;

-- ----------------------------------------------------------------------------
-- 2. CAP QUYEN OLS CO BAN
--    APP_OLS_MGR se tao level/group/label va apply policy.
--    APP_DBA_ADMIN se gan label/privilege cho user.
-- ----------------------------------------------------------------------------
GRANT LBAC_DBA TO APP_ROLE_OLS_MGR;
GRANT LBAC_DBA TO APP_OLS_MGR;

GRANT EXECUTE ON LBACSYS.SA_COMPONENTS   TO APP_ROLE_OLS_MGR;
GRANT EXECUTE ON LBACSYS.SA_LABEL_ADMIN  TO APP_ROLE_OLS_MGR;
GRANT EXECUTE ON LBACSYS.SA_POLICY_ADMIN TO APP_ROLE_OLS_MGR;

GRANT EXECUTE ON LBACSYS.SA_COMPONENTS   TO APP_OLS_MGR;
GRANT EXECUTE ON LBACSYS.SA_LABEL_ADMIN  TO APP_OLS_MGR;
GRANT EXECUTE ON LBACSYS.SA_POLICY_ADMIN TO APP_OLS_MGR;

GRANT EXECUTE ON LBACSYS.SA_USER_ADMIN TO APP_ROLE_DB_ADMIN;
GRANT EXECUTE ON LBACSYS.SA_USER_ADMIN TO APP_DBA_ADMIN;

-- APP_TABLE can dung CHAR_TO_LABEL/LABEL_TO_CHAR trong update/procedure demo.
-- Neu DB bao object khong ton tai, co the bo qua vi mot so ban Oracle da public synonym san.
BEGIN
  EXECUTE IMMEDIATE 'GRANT EXECUTE ON LBACSYS.CHAR_TO_LABEL TO APP_TABLE';
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua grant CHAR_TO_LABEL: ' || SQLERRM);
END;
/
BEGIN
  EXECUTE IMMEDIATE 'GRANT EXECUTE ON LBACSYS.LABEL_TO_CHAR TO APP_TABLE';
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua grant LABEL_TO_CHAR: ' || SQLERRM);
END;
/

PROMPT ========================================================================
PROMPT PHASE 2 - CONNECT LBACSYS: tao policy container USER_PROFILE_OLS
PROMPT ========================================================================
CONNECT &LBACSYS_CONN

-- ----------------------------------------------------------------------------
-- 3. TAO POLICY CONTAINER
--    default_options = NO_CONTROL de ban dau gan cot nhan nhung chua chan truy cap.
--    Sau khi gan nhan du lieu xong moi apply READ/WRITE/CHECK_CONTROL.
-- ----------------------------------------------------------------------------
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count
  FROM DBA_SA_POLICIES
  WHERE POLICY_NAME = 'USER_PROFILE_OLS';

  IF v_count = 0 THEN
    SA_SYSDBA.CREATE_POLICY(
      policy_name     => 'USER_PROFILE_OLS',
      column_name     => 'OLS_LABEL',
      default_options => 'NO_CONTROL'
    );
    SA_SYSDBA.ENABLE_POLICY('USER_PROFILE_OLS');
    DBMS_OUTPUT.PUT_LINE('Da tao va enable policy USER_PROFILE_OLS');
  ELSE
    DBMS_OUTPUT.PUT_LINE('Bo qua CREATE_POLICY vi USER_PROFILE_OLS da ton tai');
    SA_SYSDBA.ENABLE_POLICY('USER_PROFILE_OLS');
  END IF;
END;
/

PROMPT ========================================================================
PROMPT PHASE 3 - CONNECT SYS: gan role quan tri policy USER_PROFILE_OLS_DBA
PROMPT ========================================================================
CONNECT &SYS_CONN

-- Sau CREATE_POLICY, Oracle tao role quan tri rieng: USER_PROFILE_OLS_DBA.
-- Role nay can di kem EXECUTE package thi user moi quan tri policy duoc.
GRANT USER_PROFILE_OLS_DBA TO APP_OLS_MGR;
GRANT USER_PROFILE_OLS_DBA TO APP_DBA_ADMIN;

PROMPT ========================================================================
PROMPT PHASE 4 - CONNECT APP_OLS_MGR: tao Level, Group, Label
PROMPT ========================================================================
CONNECT &APP_OLS_MGR_CONN

-- ----------------------------------------------------------------------------
-- 4. TAO LEVEL
--    PUB co do nhay cam thap hon PRI.
-- ----------------------------------------------------------------------------
BEGIN
  SA_COMPONENTS.CREATE_LEVEL(
    policy_name => 'USER_PROFILE_OLS',
    level_num   => 1000,
    short_name  => 'PUB',
    long_name   => 'PUBLIC'
  );

  SA_COMPONENTS.CREATE_LEVEL(
    policy_name => 'USER_PROFILE_OLS',
    level_num   => 2000,
    short_name  => 'PRI',
    long_name   => 'PRIVATE'
  );
END;
/

-- ----------------------------------------------------------------------------
-- 5. TAO GROUP THEO PHONG BAN
--    ALL la group cha. SALES/HR/IT la group con.
-- ----------------------------------------------------------------------------
BEGIN
  SA_COMPONENTS.CREATE_GROUP(
    policy_name => 'USER_PROFILE_OLS',
    group_num   => 10,
    short_name  => 'ALL',
    long_name   => 'ALL_DEPARTMENTS'
  );

  SA_COMPONENTS.CREATE_GROUP(
    policy_name => 'USER_PROFILE_OLS',
    group_num   => 100,
    short_name  => 'SALES',
    long_name   => 'SALES_DEPARTMENT',
    parent_name => 'ALL'
  );

  SA_COMPONENTS.CREATE_GROUP(
    policy_name => 'USER_PROFILE_OLS',
    group_num   => 110,
    short_name  => 'HR',
    long_name   => 'HR_DEPARTMENT',
    parent_name => 'ALL'
  );

  SA_COMPONENTS.CREATE_GROUP(
    policy_name => 'USER_PROFILE_OLS',
    group_num   => 120,
    short_name  => 'IT',
    long_name   => 'IT_DEPARTMENT',
    parent_name => 'ALL'
  );
END;
/

-- ----------------------------------------------------------------------------
-- 6. TAO DATA LABEL
--    Do chi dung GROUP, label phong ban phai viet PUB::SALES, PRI::HR, ...
-- ----------------------------------------------------------------------------
BEGIN
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 50000, 'PUB',        TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 50010, 'PUB::ALL',   TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 50100, 'PUB::SALES', TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 50200, 'PUB::HR',    TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 50300, 'PUB::IT',    TRUE);

  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 60000, 'PRI',        TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 60100, 'PRI::SALES', TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 60200, 'PRI::HR',    TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 60300, 'PRI::IT',    TRUE);
END;
/

PROMPT ===== KIEM TRA COMPONENT/LABEL =====
SELECT POLICY_NAME, LEVEL_NUM, SHORT_NAME, LONG_NAME
FROM DBA_SA_LEVELS
WHERE POLICY_NAME = 'USER_PROFILE_OLS'
ORDER BY LEVEL_NUM;

SELECT POLICY_NAME, GROUP_NUM, SHORT_NAME, LONG_NAME
FROM DBA_SA_GROUPS
WHERE POLICY_NAME = 'USER_PROFILE_OLS'
ORDER BY GROUP_NUM;

SELECT POLICY_NAME, HIERARCHY_LEVEL, GROUP_NAME
FROM DBA_SA_GROUP_HIERARCHY
WHERE POLICY_NAME = 'USER_PROFILE_OLS'
ORDER BY HIERARCHY_LEVEL, GROUP_NAME;

SELECT POLICY_NAME, LABEL_TAG, LABEL
FROM DBA_SA_LABELS
WHERE POLICY_NAME = 'USER_PROFILE_OLS'
ORDER BY LABEL_TAG;

-- ----------------------------------------------------------------------------
-- 7. APPLY POLICY LAN 1 VOI NO_CONTROL
--    Muc dich: them cot OLS_LABEL vao bang nhung chua chan doc/ghi,
--    de APP_TABLE gan label cho du lieu hien co.
-- ----------------------------------------------------------------------------
BEGIN
  SA_POLICY_ADMIN.REMOVE_TABLE_POLICY(
    policy_name => 'USER_PROFILE_OLS',
    schema_name => 'APP_TABLE',
    table_name  => 'USER_PROFILE'
  );
  DBMS_OUTPUT.PUT_LINE('Da remove policy cu tren APP_TABLE.USER_PROFILE');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Bo qua REMOVE_TABLE_POLICY: ' || SQLERRM);
END;
/

BEGIN
  SA_POLICY_ADMIN.APPLY_TABLE_POLICY(
    policy_name   => 'USER_PROFILE_OLS',
    schema_name   => 'APP_TABLE',
    table_name    => 'USER_PROFILE',
    table_options => 'NO_CONTROL'
  );
  DBMS_OUTPUT.PUT_LINE('Da apply USER_PROFILE_OLS voi NO_CONTROL');
END;
/

PROMPT ========================================================================
PROMPT PHASE 5 - CONNECT APP_DBA_ADMIN: gan label/privilege cho user OLS
PROMPT ========================================================================
CONNECT &APP_DBA_ADMIN_CONN

-- ----------------------------------------------------------------------------
-- 8. GAN QUYEN DAC BIET THEO POLICY
--    READ: bo qua kiem tra doc, van bi kiem tra ghi.
--    FULL: bo qua hau het kiem tra OLS, dung cho schema owner/procedure lab.
-- ----------------------------------------------------------------------------
BEGIN
  SA_USER_ADMIN.SET_USER_PRIVS(
    policy_name => 'USER_PROFILE_OLS',
    user_name   => 'APP_DBA_ADMIN',
    privileges  => 'READ'
  );

  SA_USER_ADMIN.SET_USER_PRIVS(
    policy_name => 'USER_PROFILE_OLS',
    user_name   => 'APP_TABLE',
    privileges  => 'FULL'
  );

  SA_USER_ADMIN.SET_USER_PRIVS(
    policy_name => 'USER_PROFILE_OLS',
    user_name   => 'APP_OLS_MGR',
    privileges  => 'FULL'
  );
END;
/

-- ----------------------------------------------------------------------------
-- 9. GAN LABEL CHO USER
--    Sau khi chay block nay, user can reconnect de nhan label/privilege moi.
-- ----------------------------------------------------------------------------
BEGIN
  -- User thuong: doc/ghi du lieu PUBLIC trong group ALL va cac subgroup.
  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_USER_1',
    max_read_label  => 'PUB::ALL',
    max_write_label => 'PUB::ALL',
    min_write_label => 'PUB',
    def_label       => 'PUB::ALL',
    row_label       => 'PUB::ALL'
  );

  -- User thuong APP_USER_3: cung cap nhan PUBLIC group ALL nhu APP_USER_1.
  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_USER_3',
    max_read_label  => 'PUB::ALL',
    max_write_label => 'PUB::ALL',
    min_write_label => 'PUB',
    def_label       => 'PUB::ALL',
    row_label       => 'PUB::ALL'
  );

  -- Manager profile trong du lieu mau thuoc HR: doc/ghi PRIVATE cua HR.
  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_MANAGER_PROFILE',
    max_read_label  => 'PRI::HR',
    max_write_label => 'PRI::HR',
    min_write_label => 'PUB',
    def_label       => 'PRI::HR',
    row_label       => 'PRI::HR'
  );
END;
/

PROMPT ===== KIEM TRA USER LABEL/PRIVILEGE =====
SELECT USER_NAME, POLICY_NAME, USER_PRIVILEGES, MAX_READ_LABEL, MAX_WRITE_LABEL, MIN_WRITE_LABEL, DEFAULT_READ_LABEL, DEFAULT_ROW_LABEL
FROM DBA_SA_USERS
WHERE POLICY_NAME = 'USER_PROFILE_OLS'
ORDER BY USER_NAME;

PROMPT ========================================================================
PROMPT PHASE 6 - CONNECT APP_TABLE: gan label cho du lieu hien co + tao procedure
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN

-- ----------------------------------------------------------------------------
-- 10. GAN LABEL CHO CAC DONG HIEN CO
--     Cot OLS_LABEL la NUMBER, nen dung CHAR_TO_LABEL de chuyen chuoi label thanh tag so.
-- ----------------------------------------------------------------------------
UPDATE USER_PROFILE
SET OLS_LABEL = CHAR_TO_LABEL('USER_PROFILE_OLS', 'PUB::SALES')
WHERE DEPARTMENT = 'SALES';

UPDATE USER_PROFILE
SET OLS_LABEL = CHAR_TO_LABEL('USER_PROFILE_OLS', 'PUB::HR')
WHERE DEPARTMENT = 'HR';

UPDATE USER_PROFILE
SET OLS_LABEL = CHAR_TO_LABEL('USER_PROFILE_OLS', 'PUB::IT')
WHERE DEPARTMENT = 'IT';

COMMIT;

PROMPT ===== KIEM TRA LABEL DU LIEU SAU KHI GAN NHAN =====
SELECT USER_ID, FULL_NAME, DEPARTMENT, LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
FROM USER_PROFILE
ORDER BY USER_ID;

-- ----------------------------------------------------------------------------
-- 11. PROCEDURE NANG/HA LABEL
--     Manager HR chi duoc nang/ha dong phong HR.
--     APP_DBA_ADMIN/APP_TABLE co the thao tac tat ca trong demo.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE UPGRADE_PROFILE_LABEL(p_user_id IN NUMBER)
AUTHID DEFINER
AS
  v_dept         USER_PROFILE.DEPARTMENT%TYPE;
  v_session_user VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
BEGIN
  SELECT DEPARTMENT INTO v_dept
  FROM USER_PROFILE
  WHERE USER_ID = p_user_id
  FOR UPDATE;

  IF v_session_user = 'APP_MANAGER_PROFILE' AND v_dept <> 'HR' THEN
    RAISE_APPLICATION_ERROR(-20101, 'APP_MANAGER_PROFILE chi duoc nang label phong HR');
  ELSIF v_session_user NOT IN ('APP_MANAGER_PROFILE', 'APP_DBA_ADMIN', 'APP_TABLE') THEN
    RAISE_APPLICATION_ERROR(-20102, 'User hien tai khong duoc nang label');
  END IF;

  UPDATE USER_PROFILE
  SET OLS_LABEL = CHAR_TO_LABEL('USER_PROFILE_OLS', 'PRI::' || v_dept)
  WHERE USER_ID = p_user_id;

  COMMIT;
END;
/

CREATE OR REPLACE PROCEDURE DOWNGRADE_PROFILE_LABEL(p_user_id IN NUMBER)
AUTHID DEFINER
AS
  v_dept         USER_PROFILE.DEPARTMENT%TYPE;
  v_session_user VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
BEGIN
  SELECT DEPARTMENT INTO v_dept
  FROM USER_PROFILE
  WHERE USER_ID = p_user_id
  FOR UPDATE;

  IF v_session_user = 'APP_MANAGER_PROFILE' AND v_dept <> 'HR' THEN
    RAISE_APPLICATION_ERROR(-20103, 'APP_MANAGER_PROFILE chi duoc ha label phong HR');
  ELSIF v_session_user NOT IN ('APP_MANAGER_PROFILE', 'APP_DBA_ADMIN', 'APP_TABLE') THEN
    RAISE_APPLICATION_ERROR(-20104, 'User hien tai khong duoc ha label');
  END IF;

  UPDATE USER_PROFILE
  SET OLS_LABEL = CHAR_TO_LABEL('USER_PROFILE_OLS', 'PUB::' || v_dept)
  WHERE USER_ID = p_user_id;

  COMMIT;
END;
/

SHOW ERRORS PROCEDURE UPGRADE_PROFILE_LABEL;
SHOW ERRORS PROCEDURE DOWNGRADE_PROFILE_LABEL;

GRANT EXECUTE ON UPGRADE_PROFILE_LABEL TO APP_ROLE_PROFILE_MGR;
GRANT EXECUTE ON DOWNGRADE_PROFILE_LABEL TO APP_ROLE_PROFILE_MGR;
GRANT EXECUTE ON UPGRADE_PROFILE_LABEL TO APP_ROLE_DB_ADMIN;
GRANT EXECUTE ON DOWNGRADE_PROFILE_LABEL TO APP_ROLE_DB_ADMIN;

PROMPT ========================================================================
PROMPT PHASE 7 - CONNECT APP_OLS_MGR: bat READ/WRITE/CHECK_CONTROL
PROMPT ========================================================================
CONNECT &APP_OLS_MGR_CONN

-- ----------------------------------------------------------------------------
-- 12. REMOVE POLICY NO_CONTROL ROI APPLY LAI VOI CONTROL THAT
--     READ_CONTROL : loc dong khi SELECT/UPDATE/DELETE.
--     WRITE_CONTROL: kiem tra quyen ghi tren label cua dong.
--     CHECK_CONTROL: kiem tra label cua dong sau DML co hop le voi user khong.
-- ----------------------------------------------------------------------------
BEGIN
  SA_POLICY_ADMIN.REMOVE_TABLE_POLICY(
    policy_name => 'USER_PROFILE_OLS',
    schema_name => 'APP_TABLE',
    table_name  => 'USER_PROFILE'
  );
END;
/

BEGIN
  SA_POLICY_ADMIN.APPLY_TABLE_POLICY(
    policy_name   => 'USER_PROFILE_OLS',
    schema_name   => 'APP_TABLE',
    table_name    => 'USER_PROFILE',
    table_options => 'READ_CONTROL,WRITE_CONTROL,CHECK_CONTROL'
  );
  DBMS_OUTPUT.PUT_LINE('Da bat READ_CONTROL, WRITE_CONTROL, CHECK_CONTROL');
END;
/

PROMPT ===== KIEM TRA TABLE POLICY =====
SELECT POLICY_NAME, SCHEMA_NAME, TABLE_NAME, STATUS, TABLE_OPTIONS
FROM DBA_SA_TABLE_POLICIES
WHERE POLICY_NAME = 'USER_PROFILE_OLS'
ORDER BY SCHEMA_NAME, TABLE_NAME;

PROMPT ========================================================================
PROMPT PHASE 8 - TAM TAT VPD DE DEMO RIENG OLS (SE BAT LAI O CUOI FILE)
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN

-- Neu file 02 chua chay, hai lenh nay se bao loi va duoc bo qua.
BEGIN
  DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_SELECT', FALSE);
  DBMS_OUTPUT.PUT_LINE('Da tam tat VPD_USER_PROFILE_SELECT de demo OLS doc lap');
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua tat VPD SELECT: ' || SQLERRM);
END;
/
BEGIN
  DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_DML', FALSE);
  DBMS_OUTPUT.PUT_LINE('Da tam tat VPD_USER_PROFILE_DML de demo OLS doc lap');
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua tat VPD DML: ' || SQLERRM);
END;
/

PROMPT ========================================================================
PROMPT PHASE 9 - DEMO OLS VOI APP_USER_1 KHI TAT VPD
PROMPT ========================================================================
CONNECT &APP_USER_1_CONN

-- Ky vong ban dau: APP_USER_1 thay cac dong PUBLIC ma label nam trong quyen PUB::ALL.
SELECT USER_ID, FULL_NAME, DEPARTMENT, LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT PHASE 10 - DEMO MANAGER NANG LABEL HR LEN PRIVATE
PROMPT ========================================================================
CONNECT &APP_MANAGER_PROFILE_CONN

-- Manager HR nang dong USER_ID = 2 tu PUB::HR len PRI::HR.
EXEC APP_TABLE.UPGRADE_PROFILE_LABEL(2);

-- Ky vong: manager van thay dong HR da private.
SELECT USER_ID, FULL_NAME, DEPARTMENT, LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
FROM APP_TABLE.USER_PROFILE
WHERE USER_ID = 2;

-- Ky vong: loi -20101 neu thu nang SALES vi manager chi duoc quan ly HR.
-- EXEC APP_TABLE.UPGRADE_PROFILE_LABEL(1);

PROMPT ========================================================================
PROMPT PHASE 11 - APP_USER_1 KHONG THAY DONG PRIVATE HR
PROMPT ========================================================================
CONNECT &APP_USER_1_CONN

-- Ky vong: USER_ID = 2 khong xuat hien nua vi da la PRI::HR.
SELECT USER_ID, FULL_NAME, DEPARTMENT, LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

-- Ky vong: 0 row.
SELECT USER_ID, FULL_NAME, DEPARTMENT, LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
FROM APP_TABLE.USER_PROFILE
WHERE USER_ID = 2;

PROMPT ========================================================================
PROMPT PHASE 12 - APP_DBA_ADMIN CO READ NEN THAY TAT CA
PROMPT ========================================================================
CONNECT &APP_DBA_ADMIN_CONN

SELECT USER_ID, FULL_NAME, DEPARTMENT, LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
FROM APP_TABLE.USER_PROFILE
ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT PHASE 13 - HA LABEL HR VE PUBLIC VA BAT LAI VPD
PROMPT ========================================================================
CONNECT &APP_MANAGER_PROFILE_CONN

EXEC APP_TABLE.DOWNGRADE_PROFILE_LABEL(2);

CONNECT &APP_TABLE_CONN
BEGIN
  DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_SELECT', TRUE);
  DBMS_OUTPUT.PUT_LINE('Da bat lai VPD_USER_PROFILE_SELECT');
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua bat lai VPD SELECT: ' || SQLERRM);
END;
/
BEGIN
  DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_DML', TRUE);
  DBMS_OUTPUT.PUT_LINE('Da bat lai VPD_USER_PROFILE_DML');
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua bat lai VPD DML: ' || SQLERRM);
END;
/

PROMPT ===== KIEM TRA CUOI: LABEL DU LIEU =====
SELECT USER_ID, FULL_NAME, DEPARTMENT, LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
FROM USER_PROFILE
ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT KET THUC LAB 03 - DA HOAN TAT 3 PHAN BAO MAT RBAC, VPD, OLS
PROMPT ========================================================================
