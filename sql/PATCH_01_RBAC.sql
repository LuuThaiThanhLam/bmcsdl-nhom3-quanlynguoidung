/*
================================================================================
 PATCH FILE 01 (RBAC) - Bo sung cho nang cap Manager
================================================================================
 MUC DICH:
  - Tao them user APP_MANAGER_SALES (manager phong SALES) de demo VPD "cung phong ban".
  - Them du lieu mau (manager + nhan vien, cap bac khac nhau) cho ca HR va SALES.
  - Tao bang phu APP_TABLE.USER_DEPT_MAP de VPD tra phong ban cua session user
    MA KHONG bi de quy (Cach A da chot o Q2).

 CACH CHEN:
  Chay file nay SAU khi da chay 01_RBAC_Admin.sql (cac user/role/bang da ton tai).
  Co the chay rieng nhu mot file bo sung. Cac PHASE ghi ro CONNECTION can dung.
================================================================================
*/

SET SERVEROUTPUT ON
SET DEFINE ON

DEFINE SYS_CONN       = "SYS/1234567@localhost:1521/FREEPDB1 AS SYSDBA"
DEFINE APP_TABLE_CONN = "APP_TABLE/123456@localhost:1521/FREEPDB1"

PROMPT ========================================================================
PROMPT PHASE A1 - [CONNECTION: SYS @ FREEPDB1 AS SYSDBA]
PROMPT          Tao user APP_MANAGER_SALES + gan role
PROMPT ========================================================================
CONNECT &SYS_CONN

-- Tao manager phong SALES (tao lai duoc: bo qua neu da ton tai)
DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM dba_users WHERE username = 'APP_MANAGER_SALES';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE q'[
      CREATE USER APP_MANAGER_SALES IDENTIFIED BY "123456"
        DEFAULT TABLESPACE TS_ADMIN TEMPORARY TABLESPACE TEMP
        QUOTA 200M ON TS_ADMIN PROFILE APP_PROFILE_USER ]';
    DBMS_OUTPUT.PUT_LINE('Da tao user APP_MANAGER_SALES');
  ELSE
    DBMS_OUTPUT.PUT_LINE('Bo qua: APP_MANAGER_SALES da ton tai');
  END IF;
END;
/
GRANT APP_ROLE_PROFILE_MGR TO APP_MANAGER_SALES;

PROMPT ========================================================================
PROMPT PHASE A2 - [CONNECTION: APP_TABLE @ FREEPDB1]
PROMPT          Them du lieu mau + tao bang phu USER_DEPT_MAP
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN

-- 1) Them du lieu mau cho 2 phong (HR, SALES) voi cap bac khac nhau.
--    ROLE_LEVEL: 2 = quan ly (MGR), 1 = nhan vien (EMP).
--    (Xoa truoc cac dong moi neu chay lai de tranh trung PK.)
DELETE FROM USER_PROFILE WHERE USER_ID IN (7, 8);

INSERT INTO USER_PROFILE (USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL, DEPARTMENT, ROLE_LEVEL, USERNAME)
VALUES (7, 'Vo Thi Sales MGR', '12 Nguyen Trai, Q1, HCMC', '0931111222', 'sales.mgr@company.com', 'SALES', 2, 'APP_MANAGER_SALES');

INSERT INTO USER_PROFILE (USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL, DEPARTMENT, ROLE_LEVEL, USERNAME)
VALUES (8, 'Ngo Van Sales NV', '34 Cach Mang Thang 8, Q3, HCMC', '0933333444', 'sales.staff@company.com', 'SALES', 1, 'APP_USER_1');

-- Cap nhat ROLE_LEVEL cho du lieu HR san co de phan biet MGR/EMP:
--   USER_ID=2 (Tran Thi B) la quan ly HR  -> level 2 (MGR)
--   USER_ID=5,6 la nhan vien HR           -> level 1 (EMP)
UPDATE USER_PROFILE SET ROLE_LEVEL = 2 WHERE USER_ID = 2;
UPDATE USER_PROFILE SET ROLE_LEVEL = 1 WHERE USER_ID IN (5, 6);

-- ----------------------------------------------------------------------------
-- GAN LAI USERNAME CHO TUNG DONG (QUAN TRONG cho COLUMN MASKING)
-- ----------------------------------------------------------------------------
-- Moi dong la ho so cua 1 NGUOI rieng -> USERNAME cua dong = chu nhan ho so.
-- Nho vay column-mask "USERNAME = session_user" chi mo email/sdt cho dung chu nhan.
-- (USERNAME chi la chuoi de demo, KHONG can tao user Oracle that.
--  VPD loc theo DEPARTMENT chu khong theo USERNAME nen viec nay khong anh huong
--  "cung phong ban", chi anh huong column masking.)
UPDATE USER_PROFILE SET USERNAME = 'APP_MANAGER_PROFILE' WHERE USER_ID = 2;  -- mgr HR
UPDATE USER_PROFILE SET USERNAME = 'APP_HR_E'            WHERE USER_ID = 5;  -- nv HR
UPDATE USER_PROFILE SET USERNAME = 'APP_HR_F'            WHERE USER_ID = 6;  -- nv HR
UPDATE USER_PROFILE SET USERNAME = 'APP_MANAGER_SALES'   WHERE USER_ID = 7;  -- mgr SALES
UPDATE USER_PROFILE SET USERNAME = 'APP_SALES_A'         WHERE USER_ID = 1;  -- nv SALES
UPDATE USER_PROFILE SET USERNAME = 'APP_SALES_D'         WHERE USER_ID = 4;  -- nv SALES
UPDATE USER_PROFILE SET USERNAME = 'APP_SALES_NV'        WHERE USER_ID = 8;  -- nv SALES
-- USER_ID 3 (IT) giu nguyen
COMMIT;

-- 2) Tao bang phu USER_DEPT_MAP: anh xa USERNAME -> DEPARTMENT.
--    Bang nay KHONG bi VPD bao ve nen VPD function tra cuu o day se khong de quy.
DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM user_tables WHERE table_name = 'USER_DEPT_MAP';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE q'[
      CREATE TABLE USER_DEPT_MAP (
        USERNAME    VARCHAR2(128) PRIMARY KEY,
        DEPARTMENT  VARCHAR2(50) NOT NULL
      )]';
    DBMS_OUTPUT.PUT_LINE('Da tao bang USER_DEPT_MAP');
  ELSE
    DBMS_OUTPUT.PUT_LINE('Bo qua: USER_DEPT_MAP da ton tai');
  END IF;
END;
/

-- 3) Nap du lieu vao USER_DEPT_MAP: moi manager thuoc phong nao.
DELETE FROM USER_DEPT_MAP;
INSERT INTO USER_DEPT_MAP (USERNAME, DEPARTMENT) VALUES ('APP_MANAGER_PROFILE', 'HR');
INSERT INTO USER_DEPT_MAP (USERNAME, DEPARTMENT) VALUES ('APP_MANAGER_SALES',   'SALES');
COMMIT;

-- 4) Trigger tu dong dong bo USER_DEPT_MAP khi them manager moi vao USER_PROFILE
--    (tuy chon - giup bang phu luon dung neu sau nay them manager). Co the bo qua.
--    O day giu don gian: chi nap tay nhu tren.

-- 5) Cap quyen doc bang phu cho role manager (de function VPD chay duoi quyen manager
--    van doc duoc; tuy nhien function thuoc schema APP_TABLE nen thuong da du quyen.
--    Van grant cho chac trong truong hop function dung definer rights):
GRANT SELECT ON USER_DEPT_MAP TO APP_ROLE_PROFILE_MGR;

PROMPT ========================================================================
PROMPT KET THUC PATCH 01 - TIEP THEO CHAY PATCH_02_VPD.sql
PROMPT ========================================================================
