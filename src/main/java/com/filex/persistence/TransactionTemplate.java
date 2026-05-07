package com.filex.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Template for executing database operations within a transaction.
 *
 * <p>Handles transaction lifecycle: begin, commit, rollback.
 * Ensures proper cleanup even if exceptions occur.
 *
 * <p>Example usage:
 * <pre>{@code
 * TransactionTemplate tx = new TransactionTemplate(connection);
 * tx.execute(conn -> {
 *     repository.insert(entity);
 *     return null;
 * });
 * }</pre>
 */
public final class TransactionTemplate {

    private static final Logger log = LoggerFactory.getLogger(TransactionTemplate.class);

    private final Connection connection;

    public TransactionTemplate(Connection connection) {
        this.connection = connection;
    }

    /**
     * Executes an operation within a transaction.
     *
     * @param operation the operation to execute
     * @param <T>       the return type
     * @return the operation result
     * @throws SQLException if the operation or transaction fails
     */
    public <T> T execute(Function<Connection, T> operation) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);
            log.debug("Transaction started.");

            T result = operation.apply(connection);

            connection.commit();
            log.debug("Transaction committed.");

            return result;

        } catch (Exception e) {
            connection.rollback();
            log.warn("Transaction rolled back due to exception: {}", e.getMessage());
            throw e;

        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * Executes an operation within a transaction (void return).
     */
    public void executeVoid(VoidOperation operation) throws SQLException {
        execute(conn -> {
            try {
                operation.execute(conn);
                return null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @FunctionalInterface
    public interface VoidOperation {
        void execute(Connection connection) throws SQLException;
    }
}
