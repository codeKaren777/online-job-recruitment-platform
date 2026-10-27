package com.groupcc2.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JobsRlsIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test
    void jobsPoliciesRestrictRowsWritesAndTransactionLocalIdentity() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        UUID employer = UUID.randomUUID();
        UUID otherEmployer = UUID.randomUUID();
        UUID applicant = UUID.randomUUID();
        UUID ownJob = UUID.randomUUID();
        UUID deletableJob = UUID.randomUUID();
        UUID otherActiveJob = UUID.randomUUID();
        UUID closedJob = UUID.randomUUID();
        UUID expiredJob = UUID.randomUUID();
        UUID rejectedInsert = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID otherCompany = UUID.randomUUID();

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("""
                    CREATE ROLE jobs_rls_runtime LOGIN PASSWORD 'test-only'
                    NOSUPERUSER NOBYPASSRLS
                    """);
            statement.execute("GRANT USAGE ON SCHEMA recruitment TO jobs_rls_runtime");
            statement.execute("GRANT SELECT ON recruitment.users TO jobs_rls_runtime");
            statement.execute("GRANT SELECT ON recruitment.companies TO jobs_rls_runtime");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON recruitment.jobs TO jobs_rls_runtime");
            statement.execute("""
                    INSERT INTO recruitment.users (id, email, password_hash, role)
                    VALUES
                      ('%s', '%s@test.example', 'test-hash', 'EMPLOYER'),
                      ('%s', '%s@test.example', 'test-hash', 'EMPLOYER'),
                      ('%s', '%s@test.example', 'test-hash', 'JOB_SEEKER')
                    """.formatted(employer, employer, otherEmployer, otherEmployer, applicant, applicant));
            statement.execute("""
                    INSERT INTO recruitment.companies (id, employer_id, name)
                    VALUES
                      ('%s', '%s', 'RLS company'),
                      ('%s', '%s', 'Other RLS company')
                    """.formatted(company, employer, otherCompany, otherEmployer));
            statement.execute("""
                    INSERT INTO recruitment.jobs (id, employer_id, company_id, title, description, due_date, status)
                    VALUES
                      ('%s', '%s', '%s', 'Other active job', 'Description', CURRENT_DATE + 5, 'ACTIVE'),
                      ('%s', '%s', '%s', 'Closed job', 'Description', CURRENT_DATE + 5, 'CLOSED'),
                      ('%s', '%s', '%s', 'Expired job', 'Description', CURRENT_DATE, 'ACTIVE')
                    """.formatted(otherActiveJob, otherEmployer, otherCompany, closedJob, otherEmployer, otherCompany, expiredJob, otherEmployer, otherCompany));
        }

        try (Connection app = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "jobs_rls_runtime", "test-only")) {
            assertRestrictedRole(app);
            assertThat(hasTablePrivilege(app, "recruitment.jobs", "SELECT")).isTrue();
            assertThat(hasTablePrivilege(app, "recruitment.jobs", "INSERT")).isTrue();
            assertThat(hasTablePrivilege(app, "recruitment.jobs", "UPDATE")).isTrue();
            assertThat(hasTablePrivilege(app, "recruitment.jobs", "DELETE")).isTrue();

            app.setAutoCommit(false);
            setContext(app, employer, "EMPLOYER");
            insertJob(app, ownJob, employer, company);
            insertJob(app, deletableJob, employer, company);
            assertThat(jobCount(app, ownJob)).isEqualTo(1);
            assertThat(jobCount(app, otherActiveJob)).isZero();
            assertThat(updateTitle(app, otherActiveJob)).isZero();
            app.commit();

            assertThat(jobCount(app, ownJob)).isZero();
            app.commit();

            try (Connection admin = adminConnection();
                 PreparedStatement statement = admin.prepareStatement("""
                         INSERT INTO recruitment.applications (job_id, job_seeker_id)
                         VALUES (?, ?)
                         """)) {
                statement.setObject(1, ownJob);
                statement.setObject(2, applicant);
                statement.executeUpdate();
            }

            setContext(app, employer, "EMPLOYER");
            assertThatDeleteBlockedByApplications(() -> deleteJob(app, ownJob));
            app.rollback();

            setContext(app, employer, "EMPLOYER");
            assertThat(deleteJob(app, deletableJob)).isEqualTo(1);
            assertThat(deleteJob(app, otherActiveJob)).isZero();
            app.commit();

            setContext(app, employer, "EMPLOYER");
            assertThatThrownBySql(() -> insertJob(app, rejectedInsert, otherEmployer, otherCompany));
            app.rollback();

            setContext(app, employer, "EMPLOYER");
            assertThatThrownBySql(() -> changeOwner(app, ownJob, otherEmployer));
            app.rollback();

            setContext(app, UUID.randomUUID(), "JOB_SEEKER");
            assertThat(jobCount(app, ownJob)).isEqualTo(1);
            assertThat(jobCount(app, otherActiveJob)).isEqualTo(1);
            assertThat(jobCount(app, closedJob)).isZero();
            assertThat(jobCount(app, expiredJob)).isZero();
            app.commit();
        }

        try (Connection admin = adminConnection()) {
            assertThat(adminJobCount(admin, ownJob)).isEqualTo(1);
            assertThat(adminJobCount(admin, deletableJob)).isZero();
            assertThat(adminJobCount(admin, rejectedInsert)).isZero();
            assertThat(adminJobOwner(admin, ownJob)).isEqualTo(employer);
        }
    }

    private Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void assertRestrictedRole(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT rolsuper, rolbypassrls
                     FROM pg_roles WHERE rolname = current_user
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean("rolsuper")).isFalse();
            assertThat(result.getBoolean("rolbypassrls")).isFalse();
        }
    }

    private boolean hasTablePrivilege(Connection connection, String table, String privilege) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT has_table_privilege(current_user, ?, ?)")) {
            statement.setString(1, table);
            statement.setString(2, privilege);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private void setContext(Connection connection, UUID userId, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.user_id', ?, true), set_config('app.role', ?, true)")) {
            statement.setString(1, userId.toString());
            statement.setString(2, role);
            statement.executeQuery().close();
        }
    }

    private void insertJob(Connection connection, UUID jobId, UUID employerId, UUID companyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO recruitment.jobs (id, employer_id, company_id, title, description, due_date)
                VALUES (?, ?, ?, 'Restricted role job', 'Description', CURRENT_DATE + 5)
                """)) {
            statement.setObject(1, jobId);
            statement.setObject(2, employerId);
            statement.setObject(3, companyId);
            statement.executeUpdate();
        }
    }

    private int jobCount(Connection connection, UUID jobId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM recruitment.jobs WHERE id = ?")) {
            statement.setObject(1, jobId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int updateTitle(Connection connection, UUID jobId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE recruitment.jobs SET title = 'Unauthorized update' WHERE id = ?")) {
            statement.setObject(1, jobId);
            return statement.executeUpdate();
        }
    }

    private int deleteJob(Connection connection, UUID jobId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM recruitment.jobs WHERE id = ?")) {
            statement.setObject(1, jobId);
            return statement.executeUpdate();
        }
    }

    private void changeOwner(Connection connection, UUID jobId, UUID newOwner) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE recruitment.jobs SET employer_id = ? WHERE id = ?")) {
            statement.setObject(1, newOwner);
            statement.setObject(2, jobId);
            statement.executeUpdate();
        }
    }

    private int adminJobCount(Connection connection, UUID jobId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM recruitment.jobs WHERE id = ?")) {
            statement.setObject(1, jobId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private UUID adminJobOwner(Connection connection, UUID jobId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT employer_id FROM recruitment.jobs WHERE id = ?")) {
            statement.setObject(1, jobId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private void assertThatThrownBySql(SqlOperation operation) {
        try {
            operation.run();
            throw new AssertionError("RLS must reject an employer write for another owner");
        } catch (SQLException expected) {
            assertThat(expected.getSQLState()).isIn("42501", "P0001");
        }
    }

    private void assertThatDeleteBlockedByApplications(SqlOperation operation) {
        try {
            operation.run();
            throw new AssertionError("A job with applications must be retained");
        } catch (SQLException expected) {
            assertThat(expected.getSQLState()).isEqualTo("23503");
            assertThat(expected.getMessage()).contains("applications_job_id_fkey");
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
