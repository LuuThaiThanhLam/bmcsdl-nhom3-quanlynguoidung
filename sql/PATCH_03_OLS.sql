/*
================================================================================
 PATCH FILE 03 (OLS) - Nang cap Manager
================================================================================
 MUC DICH:
  1. Them COMPARTMENT theo cap bac: MGR (quan ly) / EMP (nhan vien).
  2. Cu phap nhan moi: LEVEL:COMPARTMENT:GROUP  vd PUB:MGR:HR , PUB:EMP:SALES , PRI:MGR:HR ...
  3. HAM GAN NHAN TU DONG (labeling function) gen_profile_label: tu sinh nhan theo
     ROLE_LEVEL (>=2 -> MGR, else EMP) va DEPARTMENT -> group.
  4. Gan user label cho 2 manager (doc duoc ca MGR va EMP cung phong) va user thuong (chi EMP).
  5. Grant nho de manager xem duoc nhan OLS cua chinh minh (Q5).

 PHU THUOC: da chay 01,02,03 goc + PATCH_01 + PATCH_02.
 LUU Y: Patch nay TAO LAI cac thanh phan compartment/label moi va apply lai policy
        voi label_function. Cac buoc tao co boc exception de chay lai duoc.

 CACH CHEN: chay sau cac file tren. PHASE ghi ro CONNECTION + nhac RECONNECT.
================================================================================
*/

SET SERVEROUTPUT ON
SET DEFINE ON

DEFINE SYS_CONN                 = "SYS/1234567@localhost:1521/FREEPDB1 AS SYSDBA"
DEFINE LBACSYS_CONN             = "LBACSYS/123456@localhost:1521/FREEPDB1"
DEFINE APP_OLS_MGR_CONN         = "APP_OLS_MGR/123456@localhost:1521/FREEPDB1"
DEFINE APP_TABLE_CONN           = "APP_TABLE/123456@localhost:1521/FREEPDB1"
DEFINE APP_DBA_ADMIN_CONN       = "APP_DBA_ADMIN/123456@localhost:1521/FREEPDB1"

PROMPT ========================================================================
PROMPT PHASE C1 - [CONNECTION: LBACSYS @ FREEPDB1]
PROMPT          Cap quyen TO_LBAC_DATA_LABEL cho APP_TABLE (de viet ham gan nhan)
PROMPT ========================================================================
CONNECT &LBACSYS_CONN
-- Owner cua ham gan nhan (APP_TABLE) can EXECUTE tren TO_LBAC_DATA_LABEL WITH GRANT OPTION.
BEGIN EXECUTE IMMEDIATE 'GRANT EXECUTE ON TO_LBAC_DATA_LABEL TO APP_TABLE WITH GRANT OPTION';
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua grant TO_LBAC_DATA_LABEL: '||SQLERRM); END;
/
-- APP_TABLE can SA_UTL de procedure nang/ha set session label (sua ORA-28115).
-- CHAR_TO_LABEL/LABEL_TO_CHAR thuong da public; van grant cho chac.
BEGIN EXECUTE IMMEDIATE 'GRANT EXECUTE ON SA_UTL TO APP_TABLE';
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua grant SA_UTL: '||SQLERRM); END;
/
BEGIN EXECUTE IMMEDIATE 'GRANT EXECUTE ON SA_SESSION TO APP_TABLE';
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua grant SA_SESSION: '||SQLERRM); END;
/

PROMPT ========================================================================
PROMPT PHASE C2 - [CONNECTION: APP_OLS_MGR @ FREEPDB1]   (RECONNECT)
PROMPT          Tao compartment MGR/EMP + tao cac label moi co compartment
PROMPT ========================================================================
CONNECT &APP_OLS_MGR_CONN

-- 1) Tao compartment (so cang nho hien thi cang truoc trong nhan).
BEGIN
  SA_COMPONENTS.CREATE_COMPARTMENT('USER_PROFILE_OLS', 100, 'MGR', 'MANAGER_LEVEL');
  SA_COMPONENTS.CREATE_COMPARTMENT('USER_PROFILE_OLS', 200, 'EMP', 'EMPLOYEE_LEVEL');
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Compartment co the da ton tai: '||SQLERRM);
END;
/

-- 2) Tao cac data label moi co dang LEVEL:COMPARTMENT:GROUP.
--    Quy uoc label_tag: 5 chu so. (chu so dau: 5=PUB, 6=PRI ; ... ; 2 chu so cuoi: group)
BEGIN
  -- PUBLIC
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 51110, 'PUB:MGR:HR',    TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 51210, 'PUB:EMP:HR',    TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 51120, 'PUB:MGR:SALES', TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 51220, 'PUB:EMP:SALES', TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 51130, 'PUB:MGR:IT',    TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 51230, 'PUB:EMP:IT',    TRUE);
  -- PRIVATE
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 61110, 'PRI:MGR:HR',    TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 61210, 'PRI:EMP:HR',    TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 61120, 'PRI:MGR:SALES', TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 61220, 'PRI:EMP:SALES', TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 61130, 'PRI:MGR:IT',    TRUE);
  SA_LABEL_ADMIN.CREATE_LABEL('USER_PROFILE_OLS', 61230, 'PRI:EMP:IT',    TRUE);
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Label co the da ton tai: '||SQLERRM);
END;
/

PROMPT ===== KIEM TRA COMPARTMENT / LABEL MOI (chay bang LBACSYS neu can) =====
-- (DBA_SA_* chi xem duoc boi LBACSYS/DBA; o day chi de tham khao)

PROMPT ========================================================================
PROMPT PHASE C3 - [CONNECTION: APP_TABLE @ FREEPDB1]
PROMPT          Viet HAM GAN NHAN TU DONG gen_profile_label
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN

-- Ham gan nhan: tra ve LBACSYS.LBAC_LABEL.
--  - Level: GIU theo nhan hien tai cua dong (neu dang PRI -> giu PRI), mac dinh PUB.
--           => Nho vay nut nang/ha (UPGRADE/DOWNGRADE) hoat dong dung, khong bi
--              label function keo nhan ve PUB sau moi UPDATE.
--  - Compartment: ROLE_LEVEL >= 2 -> MGR, nguoc lai -> EMP.
--  - Group: theo DEPARTMENT (HR/SALES/IT).
-- LUU Y QUAN TRONG:
--  (1) Sua ORA-12433: label function CHI nhan tham so :new.<cot> don gian
--      (KHONG dung ham long nhu LABEL_TO_CHAR(:new.OLS_LABEL) trong CHUOI apply).
--      Vi vay ta truyen :new.OLS_LABEL (kieu NUMBER = tag nhan) va tu chuyen
--      sang chuoi BEN TRONG function bang LABEL_TO_CHAR.
--  (2) Khi UPDATE thong thuong, :new.OLS_LABEL = tag nhan hien tai cua dong
--      -> doc duoc level cu de giu PRI.
CREATE OR REPLACE FUNCTION gen_profile_label(
  p_role_level    IN NUMBER,
  p_department    IN VARCHAR2,
  p_cur_label_tag IN NUMBER       -- tag nhan hien tai cua dong (:new.OLS_LABEL)
) RETURN LBACSYS.LBAC_LABEL
AS
  v_level   VARCHAR2(10) := 'PUB';
  v_comp    VARCHAR2(10);
  v_grp     VARCHAR2(20);
  v_cur_txt VARCHAR2(80);
BEGIN
  -- Doc nhan hien tai (neu co) de GIU level PRI.
  IF p_cur_label_tag IS NOT NULL THEN
    BEGIN
      v_cur_txt := LABEL_TO_CHAR(p_cur_label_tag);   -- vd 'PRI:MGR:HR'
      IF v_cur_txt LIKE 'PRI%' THEN
        v_level := 'PRI';
      END IF;
    EXCEPTION WHEN OTHERS THEN
      v_level := 'PUB';
    END;
  END IF;

  -- Compartment theo cap bac.
  IF p_role_level >= 2 THEN
    v_comp := 'MGR';
  ELSE
    v_comp := 'EMP';
  END IF;

  -- Group theo phong ban.
  v_grp := UPPER(p_department);

  RETURN TO_LBAC_DATA_LABEL('USER_PROFILE_OLS', v_level || ':' || v_comp || ':' || v_grp);
END;
/
SHOW ERRORS FUNCTION gen_profile_label

-- QUAN TRONG (sua loi ORA-12433): phai grant EXECUTE cho CA LBACSYS LAN LBAC_TRIGGER.
-- LBAC_TRIGGER la schema/role tao DML trigger cua OLS; thieu quyen nay se loi ORA-12433.
GRANT EXECUTE ON gen_profile_label TO LBACSYS;
GRANT EXECUTE ON gen_profile_label TO LBAC_TRIGGER;

PROMPT ========================================================================
PROMPT PHASE C4 - [CONNECTION: APP_TABLE @ FREEPDB1]
PROMPT          Khoi tao nhan cho du lieu hien co (theo cap bac + phong ban)
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN
-- Gan nhan ban dau: tat ca PUB, compartment theo ROLE_LEVEL, group theo DEPARTMENT.
-- (Lam trong giai doan policy con NO_CONTROL hoac APP_TABLE co FULL nen ghi duoc.)
UPDATE USER_PROFILE
SET OLS_LABEL = CHAR_TO_LABEL('USER_PROFILE_OLS',
      'PUB:' || CASE WHEN NVL(ROLE_LEVEL,1) >= 2 THEN 'MGR' ELSE 'EMP' END
            || ':' || UPPER(DEPARTMENT));
COMMIT;

PROMPT ===== KIEM TRA NHAN DU LIEU SAU KHI GAN =====
SELECT USER_ID, FULL_NAME, DEPARTMENT, ROLE_LEVEL,
       LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
FROM USER_PROFILE ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT PHASE C4b - [CONNECTION: APP_TABLE @ FREEPDB1]
PROMPT          Viet lai PROCEDURE nang/ha nhan cho khop cu phap moi PUB:COMP:GROUP
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN
-- Cu phap nhan moi co compartment: PUB:MGR:HR / PRI:EMP:SALES ...
-- Procedure nang/ha chi doi phan LEVEL (PUB<->PRI), GIU NGUYEN compartment va group
-- dua tren ROLE_LEVEL va DEPARTMENT cua chinh dong do.
CREATE OR REPLACE PROCEDURE UPGRADE_PROFILE_LABEL(p_user_id IN NUMBER)
  AUTHID DEFINER
AS
  v_dept  USER_PROFILE.DEPARTMENT%TYPE;
  v_level NUMBER;
  v_comp  VARCHAR2(10);
  v_session_user VARCHAR2(128) := SYS_CONTEXT('USERENV','SESSION_USER');
  v_my_dept VARCHAR2(50);
BEGIN
  SELECT DEPARTMENT, NVL(ROLE_LEVEL,1) INTO v_dept, v_level
  FROM USER_PROFILE WHERE USER_ID = p_user_id FOR UPDATE;

  -- Manager chi duoc thao tac trong phong ban cua chinh minh
  IF v_session_user IN ('APP_MANAGER_PROFILE','APP_MANAGER_SALES') THEN
    SELECT DEPARTMENT INTO v_my_dept FROM APP_TABLE.USER_DEPT_MAP
    WHERE USERNAME = v_session_user;
    IF v_dept <> v_my_dept THEN
      RAISE_APPLICATION_ERROR(-20101, 'Chi duoc nang label trong phong ban cua ban: ' || v_my_dept);
    END IF;
  ELSIF v_session_user NOT IN ('APP_DBA_ADMIN','APP_TABLE') THEN
    RAISE_APPLICATION_ERROR(-20102, 'User hien tai khong duoc nang label');
  END IF;

  v_comp := CASE WHEN v_level >= 2 THEN 'MGR' ELSE 'EMP' END;

  -- SUA ORA-28115: nang SESSION LABEL len muc cao nhat (PRI + ca 2 compartment)
  -- cua dung phong ban truoc khi ghi, de session DOC LAI duoc dong PRI vua ghi
  -- (CHECK_CONTROL yeu cau nhan moi phai nam trong vung doc/ghi cua session).
  -- Chi ap dung cho manager (admin/owner co quyen cao nen bo qua).
  IF v_session_user IN ('APP_MANAGER_PROFILE','APP_MANAGER_SALES') THEN
    SA_UTL.SET_LABEL('USER_PROFILE_OLS',
        CHAR_TO_LABEL('USER_PROFILE_OLS', 'PRI:MGR,EMP:' || UPPER(v_dept)));
  END IF;

  UPDATE USER_PROFILE
  SET OLS_LABEL = CHAR_TO_LABEL('USER_PROFILE_OLS', 'PRI:' || v_comp || ':' || UPPER(v_dept))
  WHERE USER_ID = p_user_id;
  COMMIT;
END;
/
SHOW ERRORS PROCEDURE UPGRADE_PROFILE_LABEL

CREATE OR REPLACE PROCEDURE DOWNGRADE_PROFILE_LABEL(p_user_id IN NUMBER)
  AUTHID DEFINER
AS
  v_dept  USER_PROFILE.DEPARTMENT%TYPE;
  v_level NUMBER;
  v_comp  VARCHAR2(10);
  v_session_user VARCHAR2(128) := SYS_CONTEXT('USERENV','SESSION_USER');
  v_my_dept VARCHAR2(50);
BEGIN
  SELECT DEPARTMENT, NVL(ROLE_LEVEL,1) INTO v_dept, v_level
  FROM USER_PROFILE WHERE USER_ID = p_user_id FOR UPDATE;

  IF v_session_user IN ('APP_MANAGER_PROFILE','APP_MANAGER_SALES') THEN
    SELECT DEPARTMENT INTO v_my_dept FROM APP_TABLE.USER_DEPT_MAP
    WHERE USERNAME = v_session_user;
    IF v_dept <> v_my_dept THEN
      RAISE_APPLICATION_ERROR(-20103, 'Chi duoc ha label trong phong ban cua ban: ' || v_my_dept);
    END IF;
  ELSIF v_session_user NOT IN ('APP_DBA_ADMIN','APP_TABLE') THEN
    RAISE_APPLICATION_ERROR(-20104, 'User hien tai khong duoc ha label');
  END IF;

  v_comp := CASE WHEN v_level >= 2 THEN 'MGR' ELSE 'EMP' END;

  -- Nang session label de doc/ghi duoc dong (giong UPGRADE).
  IF v_session_user IN ('APP_MANAGER_PROFILE','APP_MANAGER_SALES') THEN
    SA_UTL.SET_LABEL('USER_PROFILE_OLS',
        CHAR_TO_LABEL('USER_PROFILE_OLS', 'PRI:MGR,EMP:' || UPPER(v_dept)));
  END IF;

  UPDATE USER_PROFILE
  SET OLS_LABEL = CHAR_TO_LABEL('USER_PROFILE_OLS', 'PUB:' || v_comp || ':' || UPPER(v_dept))
  WHERE USER_ID = p_user_id;
  COMMIT;
END;
/
SHOW ERRORS PROCEDURE DOWNGRADE_PROFILE_LABEL

GRANT EXECUTE ON UPGRADE_PROFILE_LABEL   TO APP_ROLE_PROFILE_MGR;
GRANT EXECUTE ON DOWNGRADE_PROFILE_LABEL TO APP_ROLE_PROFILE_MGR;
GRANT EXECUTE ON UPGRADE_PROFILE_LABEL   TO APP_ROLE_DB_ADMIN;
GRANT EXECUTE ON DOWNGRADE_PROFILE_LABEL TO APP_ROLE_DB_ADMIN;

PROMPT ========================================================================
PROMPT PHASE C5 - [CONNECTION: APP_DBA_ADMIN @ FREEPDB1]   (RECONNECT)
PROMPT          Gan lai USER LABEL co compartment cho cac user
PROMPT ========================================================================
CONNECT &APP_DBA_ADMIN_CONN

BEGIN
  -- Manager HR: doc/ghi MGR+EMP cua phong HR (thay ca quan ly va nhan vien HR).
  -- def_label dat o PRI de session mac dinh da o muc PRI -> doc/ghi duoc ca dong PRI
  -- ma KHONG bi ORA-28115 (CHECK_CONTROL). Day la cach gon nhat de nut nang/ha chay.
  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_MANAGER_PROFILE',
    max_read_label  => 'PRI:MGR,EMP:HR',
    max_write_label => 'PRI:MGR,EMP:HR',
    min_write_label => 'PUB',
    def_label       => 'PRI:MGR,EMP:HR',
    row_label       => 'PRI:MGR:HR');

  -- Manager SALES: tuong tu cho phong SALES.
  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_MANAGER_SALES',
    max_read_label  => 'PRI:MGR,EMP:SALES',
    max_write_label => 'PRI:MGR,EMP:SALES',
    min_write_label => 'PUB',
    def_label       => 'PRI:MGR,EMP:SALES',
    row_label       => 'PRI:MGR:SALES');

  -- User thuong: chi doc EMP, tat ca group (PUB::ALL cu) -> gio chi compartment EMP.
  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_USER_1',
    max_read_label  => 'PUB:EMP:HR,SALES,IT',
    max_write_label => 'PUB:EMP:HR,SALES,IT',
    min_write_label => 'PUB',
    def_label       => 'PUB:EMP:HR,SALES,IT',
    row_label       => 'PUB:EMP:HR,SALES,IT');
END;
/

PROMPT ===== KIEM TRA USER LABEL =====
SELECT USER_NAME, MAX_READ_LABEL, MAX_WRITE_LABEL, DEFAULT_ROW_LABEL
FROM DBA_SA_USERS WHERE POLICY_NAME='USER_PROFILE_OLS' ORDER BY USER_NAME;

PROMPT ========================================================================
PROMPT PHASE C6 - [CONNECTION: APP_OLS_MGR @ FREEPDB1]
PROMPT          Apply lai policy voi LABEL_FUNCTION (gan nhan tu dong)
PROMPT ========================================================================
CONNECT &APP_OLS_MGR_CONN
-- Remove (giu cot, KHONG drop_column) roi apply lai co label_function.
BEGIN
  SA_POLICY_ADMIN.REMOVE_TABLE_POLICY('USER_PROFILE_OLS','APP_TABLE','USER_PROFILE');
EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('Bo qua REMOVE: '||SQLERRM);
END;
/
BEGIN
  SA_POLICY_ADMIN.APPLY_TABLE_POLICY(
    policy_name    => 'USER_PROFILE_OLS',
    schema_name    => 'APP_TABLE',
    table_name     => 'USER_PROFILE',
    table_options  => 'READ_CONTROL,WRITE_CONTROL,CHECK_CONTROL',
    -- Truyen :new.<cot> don gian (tag NUMBER :new.OLS_LABEL de function GIU level PRI).
    -- KHONG dung ham long trong chuoi nay -> tranh ORA-12433.
    label_function => 'APP_TABLE.gen_profile_label(:new.ROLE_LEVEL, :new.DEPARTMENT, :new.OLS_LABEL)',
    predicate      => NULL);
  DBMS_OUTPUT.PUT_LINE('Da apply policy voi ham gan nhan tu dong gen_profile_label');
END;
/

PROMPT ========================================================================
PROMPT PHASE C7 - [CONNECTION: SYS @ FREEPDB1 AS SYSDBA]
PROMPT          Grant nho de manager xem nhan OLS cua chinh minh (Q5)
PROMPT ========================================================================
CONNECT &SYS_CONN
-- Cach an toan: tao VIEW chi ra nhan OLS cua rieng session user, roi grant view.
-- DBA_SA_USERS chi LBACSYS/DBA xem duoc; ta tao view loc theo USER_NAME = session user.
CREATE OR REPLACE VIEW LBACSYS.V_MY_OLS_LABEL AS
SELECT USER_NAME, POLICY_NAME, USER_PRIVILEGES,
       MAX_READ_LABEL, MAX_WRITE_LABEL, MIN_WRITE_LABEL,
       DEFAULT_READ_LABEL, DEFAULT_ROW_LABEL
FROM   DBA_SA_USERS
WHERE  USER_NAME = SYS_CONTEXT('USERENV','SESSION_USER');

GRANT SELECT ON LBACSYS.V_MY_OLS_LABEL TO APP_ROLE_PROFILE_MGR;
-- (tuy chon) cho user thuong xem nhan cua minh luon:
GRANT SELECT ON LBACSYS.V_MY_OLS_LABEL TO APP_ROLE_USER;

PROMPT ========================================================================
PROMPT KET THUC PATCH 03 - OLS da co compartment MGR/EMP + ham gan nhan tu dong
PROMPT ========================================================================

/*
 ******************************************************************************
 GHI CHU VE LABEL FUNCTION (DA SUA DE GIU LEVEL PRI)
 ******************************************************************************
 - Label function gen_profile_label gio DOC nhan hien tai cua dong (qua
   :new.OLS_LABEL) -> neu dong dang PRI thi GIU PRI, neu PUB thi giu PUB.
 - Nho vay:
     + Procedure UPGRADE_PROFILE_LABEL set PRI  -> function thay PRI -> giu PRI. OK
     + Procedure DOWNGRADE_PROFILE_LABEL set PUB -> function thay PUB -> giu PUB. OK
     + Sua email khi dong dang PRI -> function van thay PRI -> giu PRI (khong tu ve PUB).
 - Compartment (MGR/EMP) va group (phong ban) van duoc tinh tu dong theo
   ROLE_LEVEL/DEPARTMENT moi lan INSERT/UPDATE.

 => Nut nang/ha nhan tren giao dien hoat dong dung; cot "Nhan OLS" doi mau dung
    (PUB xanh / PRI do). Khong con phai lo trinh tu "sua truoc nang sau" nhu ban dau.
 ******************************************************************************
*/
