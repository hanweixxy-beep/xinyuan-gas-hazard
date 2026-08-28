SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE users ADD COLUMN can_view_company_records TINYINT(1) NOT NULL DEFAULT 0 AFTER login_scope',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'can_view_company_records'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE users
SET login_scope = 'both'
WHERE role IN ('hq_admin', 'company_admin')
  AND login_scope = 'admin';

UPDATE users
SET can_view_company_records = 0
WHERE role <> 'inspector'
  AND can_view_company_records <> 0;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inspection_records ADD COLUMN location_accuracy_meters DECIMAL(8,2) NULL AFTER location_address',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inspection_records'
      AND COLUMN_NAME = 'location_accuracy_meters'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inspection_records ADD COLUMN location_captured_at DATETIME NULL AFTER location_accuracy_meters',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inspection_records'
      AND COLUMN_NAME = 'location_captured_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE record_photos ADD COLUMN accuracy_meters DECIMAL(8,2) NULL AFTER longitude',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'record_photos'
      AND COLUMN_NAME = 'accuracy_meters'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
