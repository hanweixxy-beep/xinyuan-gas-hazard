SET @schema_name = DATABASE();

DELETE older
FROM sessions older
INNER JOIN sessions newer
    ON newer.user_id = older.user_id
   AND newer.client_type = older.client_type
   AND (
       newer.created_at > older.created_at
       OR (newer.created_at = older.created_at AND newer.id > older.id)
   );

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE sessions ADD UNIQUE KEY uk_sessions_user_client (user_id, client_type)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'sessions'
      AND INDEX_NAME = 'uk_sessions_user_client'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
