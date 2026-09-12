SELECT now() AS sampled_at,
       (SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()) AS db_connections,
       (SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state='active') AS active,
       (SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock') AS lock_waiters,
       deadlocks, conflicts, xact_commit, xact_rollback
FROM pg_stat_database WHERE datname=current_database();
