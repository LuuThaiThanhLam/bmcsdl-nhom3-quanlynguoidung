/*
================================================================================
LAB 01 - RBAC / CHUC NANG ADMIN
================================================================================
DO AN: Xay dung ung dung web co chuc nang quan ly nguoi dung
PHAN BAO MAT: RBAC + chuc nang Admin

YEU CAU DA CHINH THEO GHI CHU CUA NHOM
  - KHONG tao package/procedure rieng cho chuc nang Admin.
  - Phan Admin demo bang code SQL binh thuong:
      CREATE USER, ALTER USER, GRANT, REVOKE, DROP USER, SELECT kiem tra.
  - Cac lenh duoc viet theo kieu bai lab, co comment huong dan ngay trong code.

MUC TIEU LAB
  1. Tao tablespace cho admin, du lieu ung dung va user thuong.
  2. Tao ROLE theo mo hinh RBAC.
  3. Tao PROFILE gioi han tai nguyen / chinh sach dang nhap.
  4. Tao cac USER demo cua he thong.
  5. Tao bang APP_TABLE.USER_PROFILE va du lieu mau.
  6. Demo cac chuc nang Admin bang lenh SQL truc tiep.

CAC FILE CHAY SAU FILE NAY
  - 02_VPD_BaoMat_UserProfile.sql  : bao mat VPD tren APP_TABLE.USER_PROFILE
  - 03_OLS_BaoMat_UserProfile.sql  : bao mat OLS tren APP_TABLE.USER_PROFILE

HUONG DAN CHAY
  Cach 1 - SQL Developer:
    - Mo file bang SQL Worksheet.
    - Sua cac DEFINE *_CONN o ben duoi cho dung user/password/service cua may ban.
    - Bam F5 - Run Script.

  Cach 2 - Chay tung doan:
    - Neu khong muon dung CONNECT, hay comment cac dong CONNECT.
    - Mo dung connection theo PROMPT dau moi PHASE roi chay tung khoi.

LUU Y KHI CHAY LAI
  - Neu da tung chay file nay, cac lenh CREATE USER/ROLE/TABLESPACE co the bao object da ton tai.
  - Khi do co the xoa object cu truoc, hoac bo qua loi da ton tai neu object da dung.
  - File nay uu tien code de doc/de demo, khong uu tien viet thanh procedure an ben trong DB.
================================================================================
*/

SET SERVEROUTPUT ON;
SET DEFINE ON;

-- ============================================================================
-- SUA CAC BIEN KET NOI O DAY NEU DUNG SQL*Plus/SQLcl HOAC SQL Developer F5
-- Vi du: DEFINE SYS_CONN = "SYS/oracle@FREEPDB1 AS SYSDBA"
-- ============================================================================
DEFINE SYS_CONN              = "SYS/your_sys_password AS SYSDBA"
DEFINE APP_DBA_ADMIN_CONN    = "APP_DBA_ADMIN/123456"
DEFINE APP_TABLE_CONN        = "APP_TABLE/123456"
DEFINE APP_USER_1_CONN       = "APP_USER_1/123456"

PROMPT ========================================================================
PROMPT PHASE 1 - CONNECT SYS: tao tablespace, role, profile, user, grant quyen
PROMPT ========================================================================
CONNECT &SYS_CONN

-- ----------------------------------------------------------------------------
-- 1. BAT RESOURCE_LIMIT
--    Can bat de cac gioi han trong PROFILE nhu SESSIONS_PER_USER, CONNECT_TIME,
--    IDLE_TIME co hieu luc.
-- ----------------------------------------------------------------------------
ALTER SYSTEM SET RESOURCE_LIMIT = TRUE;

-- ----------------------------------------------------------------------------
-- 2. TAO TABLESPACE
--    TS_ADMIN : luu schema/user quan tri.
--    TS_DATA  : luu bang du lieu ung dung.
--    TS_USERS : luu user thuong/demo.
--
--    Neu chay lai va bao tablespace da ton tai, co the bo qua neu tablespace da dung.
-- ----------------------------------------------------------------------------
CREATE TABLESPACE TS_ADMIN
  DATAFILE 'ts_admin.dbf'
  SIZE 100M AUTOEXTEND ON NEXT 50M MAXSIZE 500M;

CREATE TABLESPACE TS_DATA
  DATAFILE 'ts_data.dbf'
  SIZE 500M AUTOEXTEND ON NEXT 100M MAXSIZE 2G;

CREATE TABLESPACE TS_USERS
  DATAFILE 'ts_users.dbf'
  SIZE 200M AUTOEXTEND ON NEXT 50M MAXSIZE 1G;

-- ----------------------------------------------------------------------------
-- 3. TAO ROLE RBAC
--    Mỗi role dai dien cho mot nhom chuc nang trong ung dung web.
-- ----------------------------------------------------------------------------
CREATE ROLE APP_ROLE_DB_ADMIN;
CREATE ROLE APP_ROLE_SYSTEM_ADMIN;
CREATE ROLE APP_ROLE_PROFILE_MGR;
CREATE ROLE APP_ROLE_TABLE_MGR;
CREATE ROLE APP_ROLE_OLS_MGR;
CREATE ROLE APP_ROLE_USER;

-- Role co password de demo tinh nang role bao ve bang password.
CREATE ROLE APP_ROLE_SECURE IDENTIFIED BY rolepass123;

-- ----------------------------------------------------------------------------
-- 4. GAN SYSTEM PRIVILEGE CHO ROLE
-- ----------------------------------------------------------------------------
-- DB Admin: co quyen quan tri user/role/profile va xem metadata can thiet.
GRANT CREATE SESSION TO APP_ROLE_DB_ADMIN;
GRANT CREATE USER, ALTER USER, DROP USER TO APP_ROLE_DB_ADMIN WITH ADMIN OPTION;
GRANT CREATE ROLE, ALTER ANY ROLE, DROP ANY ROLE, GRANT ANY ROLE TO APP_ROLE_DB_ADMIN WITH ADMIN OPTION;
GRANT CREATE PROFILE, ALTER PROFILE, DROP PROFILE TO APP_ROLE_DB_ADMIN WITH ADMIN OPTION;
GRANT CREATE ANY TABLE, ALTER ANY TABLE, DROP ANY TABLE TO APP_ROLE_DB_ADMIN;
GRANT SELECT ANY TABLE, INSERT ANY TABLE, UPDATE ANY TABLE, DELETE ANY TABLE TO APP_ROLE_DB_ADMIN;
GRANT GRANT ANY PRIVILEGE TO APP_ROLE_DB_ADMIN WITH ADMIN OPTION;
GRANT GRANT ANY OBJECT PRIVILEGE TO APP_ROLE_DB_ADMIN WITH ADMIN OPTION;
GRANT SELECT_CATALOG_ROLE TO APP_ROLE_DB_ADMIN;

-- System Admin: user quan tri he thong ung dung, chu yeu xem thong tin.
GRANT CREATE SESSION TO APP_ROLE_SYSTEM_ADMIN;
GRANT SELECT ANY TABLE TO APP_ROLE_SYSTEM_ADMIN;

-- Table Manager: schema APP_TABLE tao bang/view/trigger/function cho VPD/OLS.
-- Oracle dung privilege CREATE PROCEDURE cho ca procedure, function va package.
-- File Admin nay khong tao stored procedure admin, nhung APP_TABLE can privilege nay
-- de file 02 tao function VPD va file 03 tao function/logic OLS neu can.
GRANT CREATE SESSION TO APP_ROLE_TABLE_MGR;
GRANT CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER, CREATE VIEW, CREATE PROCEDURE TO APP_ROLE_TABLE_MGR;
GRANT ALTER ANY TABLE, DROP ANY TABLE TO APP_ROLE_TABLE_MGR;
GRANT SELECT ANY TABLE, INSERT ANY TABLE, UPDATE ANY TABLE, DELETE ANY TABLE TO APP_ROLE_TABLE_MGR;
GRANT EXECUTE ON SYS.DBMS_RLS TO APP_ROLE_TABLE_MGR;
GRANT EXEMPT ACCESS POLICY TO APP_ROLE_TABLE_MGR;

-- Profile Manager va user thuong.
GRANT CREATE SESSION TO APP_ROLE_PROFILE_MGR;
GRANT CREATE SESSION TO APP_ROLE_OLS_MGR;
GRANT CREATE SESSION TO APP_ROLE_USER;

-- ----------------------------------------------------------------------------
-- 5. TAO PROFILE
-- ----------------------------------------------------------------------------
CREATE PROFILE APP_PROFILE_USER LIMIT
  SESSIONS_PER_USER 2
  CONNECT_TIME 60
  IDLE_TIME 15
  FAILED_LOGIN_ATTEMPTS 5
  PASSWORD_LIFE_TIME 180;

CREATE PROFILE APP_PROFILE_ADMIN LIMIT
  SESSIONS_PER_USER UNLIMITED
  CONNECT_TIME UNLIMITED
  IDLE_TIME UNLIMITED
  FAILED_LOGIN_ATTEMPTS UNLIMITED
  PASSWORD_LIFE_TIME UNLIMITED;

CREATE PROFILE APP_PROFILE_DEFAULT LIMIT
  SESSIONS_PER_USER DEFAULT
  CONNECT_TIME DEFAULT
  IDLE_TIME DEFAULT
  FAILED_LOGIN_ATTEMPTS DEFAULT
  PASSWORD_LIFE_TIME DEFAULT;

-- ----------------------------------------------------------------------------
-- 6. TAO USER DEMO
-- ----------------------------------------------------------------------------
CREATE USER APP_DBA_ADMIN IDENTIFIED BY "123456"
  DEFAULT TABLESPACE TS_ADMIN
  TEMPORARY TABLESPACE TEMP
  QUOTA UNLIMITED ON TS_ADMIN
  PROFILE APP_PROFILE_ADMIN;

CREATE USER APP_TABLE IDENTIFIED BY "123456"
  DEFAULT TABLESPACE TS_DATA
  TEMPORARY TABLESPACE TEMP
  QUOTA UNLIMITED ON TS_DATA
  PROFILE APP_PROFILE_ADMIN;

CREATE USER APP_SYSTEM_ADMIN IDENTIFIED BY "123456"
  DEFAULT TABLESPACE TS_ADMIN
  TEMPORARY TABLESPACE TEMP
  QUOTA 100M ON TS_ADMIN
  PROFILE APP_PROFILE_USER;

CREATE USER APP_MANAGER_PROFILE IDENTIFIED BY "123456"
  DEFAULT TABLESPACE TS_ADMIN
  TEMPORARY TABLESPACE TEMP
  QUOTA 200M ON TS_ADMIN
  PROFILE APP_PROFILE_USER;

CREATE USER APP_USER_1 IDENTIFIED BY "123456"
  DEFAULT TABLESPACE TS_USERS
  TEMPORARY TABLESPACE TEMP
  QUOTA 100M ON TS_USERS
  PROFILE APP_PROFILE_USER;

CREATE USER APP_OLS_MGR IDENTIFIED BY "123456"
  DEFAULT TABLESPACE TS_ADMIN
  TEMPORARY TABLESPACE TEMP
  QUOTA 100M ON TS_ADMIN
  PROFILE APP_PROFILE_USER;

CREATE USER APP_USER_DEMO IDENTIFIED BY "123456"
  DEFAULT TABLESPACE TS_USERS
  TEMPORARY TABLESPACE TEMP
  QUOTA 50M ON TS_USERS
  PROFILE APP_PROFILE_USER;

-- ----------------------------------------------------------------------------
-- 7. GAN ROLE CHO USER
-- ----------------------------------------------------------------------------
GRANT APP_ROLE_DB_ADMIN     TO APP_DBA_ADMIN;
GRANT APP_ROLE_TABLE_MGR    TO APP_TABLE;
GRANT APP_ROLE_SYSTEM_ADMIN TO APP_SYSTEM_ADMIN;
GRANT APP_ROLE_PROFILE_MGR  TO APP_MANAGER_PROFILE;
GRANT APP_ROLE_USER         TO APP_USER_1;
GRANT APP_ROLE_OLS_MGR      TO APP_OLS_MGR;
GRANT APP_ROLE_USER         TO APP_USER_DEMO;

-- Khoa user demo de co tinh huong mo khoa tai man hinh Admin.
ALTER USER APP_USER_DEMO ACCOUNT LOCK;

-- ----------------------------------------------------------------------------
-- 8. GAN QUYEN TRUC TIEP CHO APP_DBA_ADMIN
--    Phan Admin khong dung procedure, nhung user APP_DBA_ADMIN can quyen truc tiep
--    de chay cac lenh CREATE USER, ALTER USER, GRANT ROLE, REVOKE ROLE.
-- ----------------------------------------------------------------------------
GRANT CREATE SESSION TO APP_DBA_ADMIN;
GRANT UNLIMITED TABLESPACE TO APP_DBA_ADMIN;
GRANT CREATE USER, ALTER USER, DROP USER TO APP_DBA_ADMIN;
GRANT CREATE ROLE, ALTER ANY ROLE, DROP ANY ROLE, GRANT ANY ROLE TO APP_DBA_ADMIN;
GRANT CREATE PROFILE, ALTER PROFILE, DROP PROFILE TO APP_DBA_ADMIN;
GRANT SELECT ANY DICTIONARY TO APP_DBA_ADMIN;

-- Quyen truc tiep cho APP_TABLE de file 02 tao/xoa VPD policy va tao function VPD.
GRANT CREATE SESSION TO APP_TABLE;
GRANT CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER, CREATE VIEW, CREATE PROCEDURE TO APP_TABLE;
GRANT EXECUTE ON SYS.DBMS_RLS TO APP_TABLE;

-- Quyen xem dictionary cho APP_DBA_ADMIN de demo bao cao Admin.
GRANT SELECT ON SYS.DBA_USERS      TO APP_DBA_ADMIN;
GRANT SELECT ON SYS.DBA_ROLE_PRIVS TO APP_DBA_ADMIN;
GRANT SELECT ON SYS.DBA_SYS_PRIVS  TO APP_DBA_ADMIN;
GRANT SELECT ON SYS.DBA_PROFILES   TO APP_DBA_ADMIN;
GRANT SELECT ON SYS.DBA_OBJECTS    TO APP_DBA_ADMIN;
GRANT SELECT ON SYS.DBA_TAB_PRIVS  TO APP_DBA_ADMIN;
GRANT SELECT ON SYS.DBA_COL_PRIVS  TO APP_DBA_ADMIN;

PROMPT ========================================================================
PROMPT PHASE 2 - CONNECT APP_TABLE: tao bang du lieu mau va view
PROMPT ========================================================================
CONNECT &APP_TABLE_CONN

-- ----------------------------------------------------------------------------
-- 9. TAO BANG USER_PROFILE
--    USERNAME   : dung cho VPD de loc theo user dang login.
--    DEPARTMENT : dung cho VPD/OLS de loc theo phong ban.
-- ----------------------------------------------------------------------------
CREATE TABLE USER_PROFILE (
  USER_ID      NUMBER PRIMARY KEY,
  FULL_NAME    VARCHAR2(100) NOT NULL,
  ADDRESS      VARCHAR2(200),
  PHONE_NUMBER VARCHAR2(15),
  EMAIL        VARCHAR2(100),
  DEPARTMENT   VARCHAR2(50),
  ROLE_LEVEL   NUMBER,
  USERNAME     VARCHAR2(128),
  CREATED_AT   DATE DEFAULT SYSDATE,
  UPDATED_AT   DATE
);

-- Sequence dung cho ung dung web khi them user profile moi.
CREATE SEQUENCE USER_PROFILE_SEQ START WITH 100 INCREMENT BY 1 NOCACHE;

-- Trigger chi de cap nhat UPDATED_AT khi sua ho so, khong phai procedure Admin.
CREATE OR REPLACE TRIGGER TRG_USER_PROFILE_UPD_AT
BEFORE UPDATE ON USER_PROFILE
FOR EACH ROW
BEGIN
  :NEW.UPDATED_AT := SYSDATE;
END;
/

-- ----------------------------------------------------------------------------
-- 10. THEM DU LIEU MAU
-- ----------------------------------------------------------------------------
INSERT INTO USER_PROFILE
  (USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL, DEPARTMENT, ROLE_LEVEL, USERNAME)
VALUES
  (1, 'Nguyen Van A', '123 Le Loi, Q1, HCMC', '0901234567', 'a.nguyen@company.com', 'SALES', 1, 'APP_USER_1');

INSERT INTO USER_PROFILE
  (USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL, DEPARTMENT, ROLE_LEVEL, USERNAME)
VALUES
  (2, 'Tran Thi B', '456 Tran Hung Dao, Q5, HCMC', '0912345678', 'b.tran@company.com', 'HR', 2, 'APP_MANAGER_PROFILE');

INSERT INTO USER_PROFILE
  (USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL, DEPARTMENT, ROLE_LEVEL, USERNAME)
VALUES
  (3, 'Le Van C', '789 Dien Bien Phu, Q3, HCMC', '0987654321', 'c.le@company.com', 'IT', 3, 'APP_DBA_ADMIN');

INSERT INTO USER_PROFILE
  (USER_ID, FULL_NAME, ADDRESS, PHONE_NUMBER, EMAIL, DEPARTMENT, ROLE_LEVEL, USERNAME)
VALUES
  (4, 'Pham Thi D', '101 Nguyen Hue, Q1, HCMC', '0976543210', 'd.pham@company.com', 'SALES', 1, 'APP_USER_1');

COMMIT;

-- ----------------------------------------------------------------------------
-- 11. TAO VIEW CHO USER THUONG
--    View chi tra ve thong tin co ban, khong lo dia chi/sdt/email.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW VIEW_USER_BASIC_INFO AS
SELECT DISTINCT FULL_NAME, DEPARTMENT
FROM USER_PROFILE;
/

-- ----------------------------------------------------------------------------
-- 12. GRANT OBJECT PRIVILEGES THEO RBAC
--    File 02 se grant bo sung SELECT/UPDATE/DELETE tren USER_PROFILE de demo VPD.
-- ----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON USER_PROFILE TO APP_ROLE_DB_ADMIN;
GRANT SELECT ON USER_PROFILE TO APP_ROLE_SYSTEM_ADMIN;
GRANT SELECT ON USER_PROFILE TO APP_ROLE_PROFILE_MGR;
GRANT SELECT ON VIEW_USER_BASIC_INFO TO APP_ROLE_PROFILE_MGR;
GRANT SELECT ON VIEW_USER_BASIC_INFO TO APP_ROLE_USER;

-- APP_DBA_ADMIN co GRANT OPTION de cap lai object privilege neu can demo Admin.
GRANT SELECT, INSERT, UPDATE, DELETE ON USER_PROFILE TO APP_DBA_ADMIN WITH GRANT OPTION;
GRANT SELECT ON VIEW_USER_BASIC_INFO TO APP_DBA_ADMIN WITH GRANT OPTION;

PROMPT ========================================================================
PROMPT PHASE 3 - CONNECT APP_DBA_ADMIN: demo chuc nang Admin bang SQL truc tiep
PROMPT ========================================================================
CONNECT &APP_DBA_ADMIN_CONN

-- ----------------------------------------------------------------------------
-- 13. DEMO ADMIN - XEM DANH SACH USER APP_*
-- ----------------------------------------------------------------------------
SELECT USERNAME, ACCOUNT_STATUS, DEFAULT_TABLESPACE, TEMPORARY_TABLESPACE, PROFILE
FROM DBA_USERS
WHERE USERNAME LIKE 'APP_%'
ORDER BY USERNAME;

-- ----------------------------------------------------------------------------
-- 14. DEMO ADMIN - TAO USER MOI
--     Day la code SQL binh thuong, khong goi procedure/package nao.
--     Neu APP_WEB_TEST da ton tai do lan chay truoc, chay lenh sau truoc:
--       DROP USER APP_WEB_TEST CASCADE;
-- ----------------------------------------------------------------------------
CREATE USER APP_WEB_TEST IDENTIFIED BY "123456"
  DEFAULT TABLESPACE TS_USERS
  TEMPORARY TABLESPACE TEMP
  QUOTA 50M ON TS_USERS
  PROFILE APP_PROFILE_USER;

GRANT APP_ROLE_USER TO APP_WEB_TEST;

SELECT USERNAME, ACCOUNT_STATUS, DEFAULT_TABLESPACE, PROFILE
FROM DBA_USERS
WHERE USERNAME = 'APP_WEB_TEST';

SELECT GRANTEE, GRANTED_ROLE, DEFAULT_ROLE
FROM DBA_ROLE_PRIVS
WHERE GRANTEE = 'APP_WEB_TEST';

-- ----------------------------------------------------------------------------
-- 15. DEMO ADMIN - KHOA USER
-- ----------------------------------------------------------------------------
ALTER USER APP_WEB_TEST ACCOUNT LOCK;

SELECT USERNAME, ACCOUNT_STATUS
FROM DBA_USERS
WHERE USERNAME = 'APP_WEB_TEST';

-- ----------------------------------------------------------------------------
-- 16. DEMO ADMIN - MO KHOA USER
-- ----------------------------------------------------------------------------
ALTER USER APP_WEB_TEST ACCOUNT UNLOCK;

SELECT USERNAME, ACCOUNT_STATUS
FROM DBA_USERS
WHERE USERNAME = 'APP_WEB_TEST';

-- ----------------------------------------------------------------------------
-- 17. DEMO ADMIN - RESET PASSWORD
-- ----------------------------------------------------------------------------
ALTER USER APP_WEB_TEST IDENTIFIED BY "123456";

-- ----------------------------------------------------------------------------
-- 18. DEMO ADMIN - GAN THEM ROLE CHO USER
-- ----------------------------------------------------------------------------
GRANT APP_ROLE_SECURE TO APP_WEB_TEST;

SELECT GRANTEE, GRANTED_ROLE, DEFAULT_ROLE
FROM DBA_ROLE_PRIVS
WHERE GRANTEE = 'APP_WEB_TEST'
ORDER BY GRANTED_ROLE;

-- ----------------------------------------------------------------------------
-- 19. DEMO ADMIN - THU HOI ROLE
-- ----------------------------------------------------------------------------
REVOKE APP_ROLE_SECURE FROM APP_WEB_TEST;

SELECT GRANTEE, GRANTED_ROLE, DEFAULT_ROLE
FROM DBA_ROLE_PRIVS
WHERE GRANTEE = 'APP_WEB_TEST'
ORDER BY GRANTED_ROLE;

-- ----------------------------------------------------------------------------
-- 20. DEMO ADMIN - DOI PROFILE CHO USER
-- ----------------------------------------------------------------------------
ALTER USER APP_WEB_TEST PROFILE APP_PROFILE_DEFAULT;

SELECT USERNAME, PROFILE
FROM DBA_USERS
WHERE USERNAME = 'APP_WEB_TEST';

-- Doi lai profile user de demo day du quy trinh.
ALTER USER APP_WEB_TEST PROFILE APP_PROFILE_USER;

-- ----------------------------------------------------------------------------
-- 21. DEMO ADMIN - XOA USER DEMO
-- ----------------------------------------------------------------------------
DROP USER APP_WEB_TEST CASCADE;

SELECT USERNAME
FROM DBA_USERS
WHERE USERNAME = 'APP_WEB_TEST';

-- ----------------------------------------------------------------------------
-- 22. CAC CAU LENH KIEM TRA CHO BAO CAO/DEMO WEB ADMIN
-- ----------------------------------------------------------------------------
PROMPT ===== KIEM TRA USER APP_* =====
SELECT USERNAME, ACCOUNT_STATUS, DEFAULT_TABLESPACE, TEMPORARY_TABLESPACE, PROFILE
FROM DBA_USERS
WHERE USERNAME LIKE 'APP_%'
ORDER BY USERNAME;

PROMPT ===== KIEM TRA ROLE GAN CHO USER =====
SELECT GRANTEE, GRANTED_ROLE, ADMIN_OPTION, DEFAULT_ROLE
FROM DBA_ROLE_PRIVS
WHERE GRANTEE LIKE 'APP_%'
ORDER BY GRANTEE, GRANTED_ROLE;

PROMPT ===== KIEM TRA SYSTEM PRIVILEGES CUA ROLE =====
SELECT GRANTEE, PRIVILEGE, ADMIN_OPTION
FROM DBA_SYS_PRIVS
WHERE GRANTEE LIKE 'APP_ROLE_%'
ORDER BY GRANTEE, PRIVILEGE;

PROMPT ===== KIEM TRA PROFILE =====
SELECT PROFILE, RESOURCE_NAME, LIMIT
FROM DBA_PROFILES
WHERE PROFILE LIKE 'APP_PROFILE_%'
ORDER BY PROFILE, RESOURCE_NAME;

PROMPT ========================================================================
PROMPT PHASE 4 - CONNECT APP_USER_1: demo user thuong chi xem view co ban
PROMPT ========================================================================
CONNECT &APP_USER_1_CONN

-- User thuong duoc xem view basic info theo RBAC.
SELECT * FROM APP_TABLE.VIEW_USER_BASIC_INFO ORDER BY FULL_NAME;

-- Cau lenh duoi day du kien bi loi ORA-00942 trong file 01 vi APP_ROLE_USER
-- chua duoc grant SELECT truc tiep tren USER_PROFILE.
-- File 02 se grant SELECT/UPDATE va dung VPD de loc dong.
-- SELECT * FROM APP_TABLE.USER_PROFILE;

PROMPT ========================================================================
PROMPT KET THUC LAB 01 - TIEP THEO CHAY FILE 02_VPD_BaoMat_UserProfile.sql
PROMPT ========================================================================
