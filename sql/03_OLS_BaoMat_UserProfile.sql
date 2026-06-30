/*
================================================================================
03_OLS_REPAIR_FROM_CURRENT_STATE.sql
================================================================================
MUC DICH
- Sua tiep OLS tu TRANG THAI HIEN TAI
- GIA DINH:
  + Policy USER_PROFILE_OLS da ton tai
  + Cot OLS_LABEL da ton tai tren APP_TABLE.USER_PROFILE
  + Da co mot phan labels / function / procedure
- KHONG tao lai policy
- KHONG grant lai role USER_PROFILE_OLS_DBA
- Dung LBACSYS de set lai labels / apply lai policy
- Chay bang F5
================================================================================
*/

SET SERVEROUTPUT ON;
SET DEFINE ON;

DEFINE LBACSYS_CONN   = "LBACSYS/123456@//localhost:1521/FREEPDB1"
DEFINE APP_TABLE_CONN = "APP_TABLE/123456@//localhost:1521/FREEPDB1"
DEFINE SYS_CONN       = "SYS/1234567@//localhost:1521/FREEPDB1 AS SYSDBA"

PROMPT ========================================================================
PROMPT PHASE 1 - CONNECT LBACSYS: CAP NHAT / BO SUNG USER PRIVILEGES VA USER LABELS
PROMPT ========================================================================

CONNECT &LBACSYS_CONN

BEGIN
  SA_USER_ADMIN.SET_USER_PRIVS('USER_PROFILE_OLS', 'APP_DBA_ADMIN', 'READ');
  SA_USER_ADMIN.SET_USER_PRIVS('USER_PROFILE_OLS', 'APP_TABLE',     'FULL');
  SA_USER_ADMIN.SET_USER_PRIVS('USER_PROFILE_OLS', 'APP_OLS_MGR',   'FULL');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('SET_USER_PRIVS: ' || SQLERRM);
END;
/

BEGIN
  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_MANAGER_PROFILE',
    max_read_label  => 'PRI:MGR,EMP:HR',
    max_write_label => 'PRI:MGR,EMP:HR',
    min_write_label => 'PUB',
    def_label       => 'PRI:MGR,EMP:HR',
    row_label       => 'PRI:MGR:HR'
  );

  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_MANAGER_SALES',
    max_read_label  => 'PRI:MGR,EMP:SALES',
    max_write_label => 'PRI:MGR,EMP:SALES',
    min_write_label => 'PUB',
    def_label       => 'PRI:MGR,EMP:SALES',
    row_label       => 'PRI:MGR:SALES'
  );

  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_USER_1',
    max_read_label  => 'PUB:EMP:HR,SALES,IT',
    max_write_label => 'PUB:EMP:HR,SALES,IT',
    min_write_label => 'PUB',
    def_label       => 'PUB:EMP:HR,SALES,IT',
    row_label       => 'PUB:EMP:HR,SALES,IT'
  );

  SA_USER_ADMIN.SET_USER_LABELS(
    policy_name     => 'USER_PROFILE_OLS',
    user_name       => 'APP_USER_3',
    max_read_label  => 'PUB:EMP:HR,SALES,IT',
    max_write_label => 'PUB:EMP:HR,SALES,IT',
    min_write_label => 'PUB',
    def_label       => 'PUB:EMP:HR,SALES,IT',
    row_label       => 'PUB:EMP:HR,SALES,IT'
  );
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('SET_USER_LABELS: ' || SQLERRM);
END;
/

PROMPT ===== KIEM TRA USER LABELS =====
SELECT USER_NAME, MAX_READ_LABEL, MAX_WRITE_LABEL, DEFAULT_ROW_LABEL
FROM DBA_SA_USERS
WHERE POLICY_NAME = 'USER_PROFILE_OLS'
ORDER BY USER_NAME;

PROMPT ========================================================================
PROMPT PHASE 2 - CONNECT APP_TABLE: TAO LAI FUNCTION VA PROCEDURE
PROMPT ========================================================================

CONNECT &APP_TABLE_CONN

CREATE OR REPLACE FUNCTION GEN_PROFILE_LABEL(
  p_role_level    IN NUMBER,
  p_department    IN VARCHAR2,
  p_cur_label_tag IN NUMBER
) RETURN LBACSYS.LBAC_LABEL
AS
  v_level   VARCHAR2(10) := 'PUB';
  v_comp    VARCHAR2(10);
  v_grp     VARCHAR2(20);
  v_cur_txt VARCHAR2(80);
BEGIN
  IF p_cur_label_tag IS NOT NULL THEN
    BEGIN
      v_cur_txt := LABEL_TO_CHAR(p_cur_label_tag);
      IF v_cur_txt LIKE 'PRI%' THEN
        v_level := 'PRI';
      END IF;
    EXCEPTION
      WHEN OTHERS THEN
        v_level := 'PUB';
    END;
  END IF;

  v_comp := CASE WHEN NVL(p_role_level, 1) >= 2 THEN 'MGR' ELSE 'EMP' END;
  v_grp  := UPPER(p_department);

  RETURN TO_LBAC_DATA_LABEL(
    'USER_PROFILE_OLS',
    v_level || ':' || v_comp || ':' || v_grp
  );
END;
/
SHOW ERRORS FUNCTION GEN_PROFILE_LABEL;

GRANT EXECUTE ON GEN_PROFILE_LABEL TO LBACSYS;
GRANT EXECUTE ON GEN_PROFILE_LABEL TO LBAC_TRIGGER;

PROMPT ========================================================================
PROMPT PHASE 3 - CONNECT APP_TABLE: GAN LAI NHAN CHO DU LIEU HIEN CO
PROMPT ========================================================================

UPDATE USER_PROFILE
SET OLS_LABEL = CHAR_TO_LABEL(
  'USER_PROFILE_OLS',
  'PUB:' || CASE WHEN NVL(ROLE_LEVEL, 1) >= 2 THEN 'MGR' ELSE 'EMP' END || ':' || UPPER(DEPARTMENT)
);
COMMIT;

PROMPT ===== LABEL DU LIEU SAU KHI GAN LAI =====
SELECT USER_ID, FULL_NAME, DEPARTMENT, ROLE_LEVEL, LABEL_TO_CHAR(OLS_LABEL) AS OLS_LABEL_TEXT
FROM USER_PROFILE
ORDER BY USER_ID;

PROMPT ========================================================================
PROMPT PHASE 4 - CONNECT APP_TABLE: TAO LAI PROCEDURE NANG / HA LABEL
PROMPT ========================================================================

CREATE OR REPLACE PROCEDURE UPGRADE_PROFILE_LABEL(p_user_id IN NUMBER)
AUTHID DEFINER
AS
  v_dept         USER_PROFILE.DEPARTMENT%TYPE;
  v_level        NUMBER;
  v_comp         VARCHAR2(10);
  v_session_user VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
  v_my_dept      VARCHAR2(50);
BEGIN
  SELECT DEPARTMENT, NVL(ROLE_LEVEL, 1)
  INTO v_dept, v_level
  FROM USER_PROFILE
  WHERE USER_ID = p_user_id
  FOR UPDATE;

  IF v_session_user IN ('APP_MANAGER_PROFILE', 'APP_MANAGER_SALES') THEN
    SELECT DEPARTMENT INTO v_my_dept
    FROM APP_TABLE.USER_DEPT_MAP
    WHERE USERNAME = v_session_user;

    IF v_dept <> v_my_dept THEN
      RAISE_APPLICATION_ERROR(-20101, 'Chi duoc nang label trong phong ban cua ban: ' || v_my_dept);
    END IF;
  ELSIF v_session_user NOT IN ('APP_DBA_ADMIN', 'APP_TABLE') THEN
    RAISE_APPLICATION_ERROR(-20102, 'User hien tai khong duoc nang label');
  END IF;

  v_comp := CASE WHEN v_level >= 2 THEN 'MGR' ELSE 'EMP' END;

  IF v_session_user IN ('APP_MANAGER_PROFILE', 'APP_MANAGER_SALES') THEN
    SA_UTL.SET_LABEL(
      'USER_PROFILE_OLS',
      CHAR_TO_LABEL('USER_PROFILE_OLS', 'PRI:MGR,EMP:' || UPPER(v_dept))
    );
  END IF;

  UPDATE USER_PROFILE
  SET OLS_LABEL = CHAR_TO_LABEL(
    'USER_PROFILE_OLS',
    'PRI:' || v_comp || ':' || UPPER(v_dept)
  )
  WHERE USER_ID = p_user_id;

  COMMIT;
END;
/
SHOW ERRORS PROCEDURE UPGRADE_PROFILE_LABEL;

CREATE OR REPLACE PROCEDURE DOWNGRADE_PROFILE_LABEL(p_user_id IN NUMBER)
AUTHID DEFINER
AS
  v_dept         USER_PROFILE.DEPARTMENT%TYPE;
  v_level        NUMBER;
  v_comp         VARCHAR2(10);
  v_session_user VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
  v_my_dept      VARCHAR2(50);
BEGIN
  SELECT DEPARTMENT, NVL(ROLE_LEVEL, 1)
  INTO v_dept, v_level
  FROM USER_PROFILE
  WHERE USER_ID = p_user_id
  FOR UPDATE;

  IF v_session_user IN ('APP_MANAGER_PROFILE', 'APP_MANAGER_SALES') THEN
    SELECT DEPARTMENT INTO v_my_dept
    FROM APP_TABLE.USER_DEPT_MAP
    WHERE USERNAME = v_session_user;

    IF v_dept <> v_my_dept THEN
      RAISE_APPLICATION_ERROR(-20103, 'Chi duoc ha label trong phong ban cua ban: ' || v_my_dept);
    END IF;
  ELSIF v_session_user NOT IN ('APP_DBA_ADMIN', 'APP_TABLE') THEN
    RAISE_APPLICATION_ERROR(-20104, 'User hien tai khong duoc ha label');
  END IF;

  v_comp := CASE WHEN v_level >= 2 THEN 'MGR' ELSE 'EMP' END;

  IF v_session_user IN ('APP_MANAGER_PROFILE', 'APP_MANAGER_SALES') THEN
    SA_UTL.SET_LABEL(
      'USER_PROFILE_OLS',
      CHAR_TO_LABEL('USER_PROFILE_OLS', 'PRI:MGR,EMP:' || UPPER(v_dept))
    );
  END IF;

  UPDATE USER_PROFILE
  SET OLS_LABEL = CHAR_TO_LABEL(
    'USER_PROFILE_OLS',
    'PUB:' || v_comp || ':' || UPPER(v_dept)
  )
  WHERE USER_ID = p_user_id;

  COMMIT;
END;
/
SHOW ERRORS PROCEDURE DOWNGRADE_PROFILE_LABEL;

GRANT EXECUTE ON UPGRADE_PROFILE_LABEL TO APP_ROLE_PROFILE_MGR;
GRANT EXECUTE ON DOWNGRADE_PROFILE_LABEL TO APP_ROLE_PROFILE_MGR;
GRANT EXECUTE ON UPGRADE_PROFILE_LABEL TO APP_ROLE_DB_ADMIN;
GRANT EXECUTE ON DOWNGRADE_PROFILE_LABEL TO APP_ROLE_DB_ADMIN;

PROMPT ========================================================================
PROMPT PHASE 5 - CONNECT LBACSYS: APPLY LAI POLICY VOI READ/WRITE/CHECK_CONTROL
PROMPT ========================================================================

CONNECT &LBACSYS_CONN

BEGIN
  SA_POLICY_ADMIN.REMOVE_TABLE_POLICY(
    policy_name => 'USER_PROFILE_OLS',
    schema_name => 'APP_TABLE',
    table_name  => 'USER_PROFILE'
  );
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

BEGIN
  SA_POLICY_ADMIN.APPLY_TABLE_POLICY(
    policy_name    => 'USER_PROFILE_OLS',
    schema_name    => 'APP_TABLE',
    table_name     => 'USER_PROFILE',
    table_options  => 'READ_CONTROL,WRITE_CONTROL,CHECK_CONTROL',
    label_function => 'APP_TABLE.GEN_PROFILE_LABEL(:new.ROLE_LEVEL, :new.DEPARTMENT, :new.OLS_LABEL)',
    predicate      => NULL
  );
  DBMS_OUTPUT.PUT_LINE('Da apply lai policy USER_PROFILE_OLS voi READ/WRITE/CHECK_CONTROL');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('APPLY_TABLE_POLICY: ' || SQLERRM);
END;
/

PROMPT ========================================================================
PROMPT PHASE 6 - CONNECT SYS: TAO VIEW CHO WEB XEM NHAN OLS
PROMPT ========================================================================

CONNECT &SYS_CONN

CREATE OR REPLACE VIEW LBACSYS.V_MY_OLS_LABEL AS
SELECT USER_NAME, POLICY_NAME, USER_PRIVILEGES, MAX_READ_LABEL, MAX_WRITE_LABEL,
       MIN_WRITE_LABEL, DEFAULT_READ_LABEL, DEFAULT_ROW_LABEL
FROM DBA_SA_USERS
WHERE USER_NAME = SYS_CONTEXT('USERENV','SESSION_USER');

GRANT SELECT ON LBACSYS.V_MY_OLS_LABEL TO APP_ROLE_PROFILE_MGR;
GRANT SELECT ON LBACSYS.V_MY_OLS_LABEL TO APP_ROLE_USER;

PROMPT ========================================================================
PROMPT PHASE 7 - CONNECT APP_TABLE: BAT LAI VPD
PROMPT ========================================================================

CONNECT &APP_TABLE_CONN

BEGIN
  DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_SELECT', TRUE);
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

BEGIN
  DBMS_RLS.ENABLE_POLICY('APP_TABLE', 'USER_PROFILE', 'VPD_USER_PROFILE_DML', TRUE);
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

PROMPT ========================================================================
PROMPT KET THUC 03_OLS_REPAIR_FROM_CURRENT_STATE.sql
PROMPT ========================================================================