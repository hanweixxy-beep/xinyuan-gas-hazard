SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE users ADD COLUMN login_scope ENUM(''app'', ''admin'', ''both'') NOT NULL DEFAULT ''both'' AFTER role',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'login_scope'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE users
SET login_scope = 'admin'
WHERE role IN ('hq_admin', 'company_admin')
  AND login_scope <> 'admin';

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE sessions ADD COLUMN client_type ENUM(''app'', ''admin'') NOT NULL DEFAULT ''admin'' AFTER user_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'sessions'
      AND COLUMN_NAME = 'client_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inspection_records ADD COLUMN record_state ENUM(''draft'', ''submitted'') NOT NULL DEFAULT ''submitted'' AFTER creator_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inspection_records'
      AND COLUMN_NAME = 'record_state'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inspection_records ADD KEY idx_records_creator_state_date (creator_id, record_state, inspection_date)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inspection_records'
      AND INDEX_NAME = 'idx_records_creator_state_date'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
