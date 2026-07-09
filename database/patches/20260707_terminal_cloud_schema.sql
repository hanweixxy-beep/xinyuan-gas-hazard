SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inspection_records ADD COLUMN client_uuid VARCHAR(80) NULL AFTER id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inspection_records'
      AND COLUMN_NAME = 'client_uuid'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inspection_records ADD COLUMN rectification_person VARCHAR(80) NOT NULL DEFAULT '''' AFTER rectification_note',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inspection_records'
      AND COLUMN_NAME = 'rectification_person'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inspection_records ADD UNIQUE KEY uq_inspection_client_uuid (client_uuid)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inspection_records'
      AND INDEX_NAME = 'uq_inspection_client_uuid'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
