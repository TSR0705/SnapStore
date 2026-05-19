package com.filex.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Represents a single database schema migration.
 *
 * <p>Migrations are executed in version order during application startup. Each migration is
 * idempotent and can be safely re-run if it has already been applied (the migration system tracks
 * applied versions).
 */
public interface Migration {

  /** Returns the migration version number. Migrations are executed in ascending version order. */
  int version();

  /** Returns a human-readable description of this migration. */
  String description();

  /**
   * Executes the migration DDL statements.
   *
   * @param connection the database connection; must NOT be closed by the migration
   * @throws SQLException if the migration fails
   */
  void migrate(Connection connection) throws SQLException;
}
