DROP POLICY applications_participant_or_admin ON recruitment.applications;
DROP POLICY applications_update_owner_or_admin ON recruitment.applications;

CREATE POLICY applications_select_participant_or_admin ON recruitment.applications
    FOR SELECT
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR (
            current_setting('app.role', true) = 'JOB_SEEKER'
            AND job_seeker_id::TEXT = current_setting('app.user_id', true)
        )
        OR EXISTS (
            SELECT 1
            FROM recruitment.jobs AS j
            WHERE j.id = applications.job_id
              AND current_setting('app.role', true) = 'EMPLOYER'
              AND j.employer_id::TEXT = current_setting('app.user_id', true)
        )
    );

CREATE POLICY applications_update_employer_or_admin ON recruitment.applications
    FOR UPDATE
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR EXISTS (
            SELECT 1
            FROM recruitment.jobs AS j
            WHERE j.id = applications.job_id
              AND current_setting('app.role', true) = 'EMPLOYER'
              AND j.employer_id::TEXT = current_setting('app.user_id', true)
        )
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR EXISTS (
            SELECT 1
            FROM recruitment.jobs AS j
            WHERE j.id = applications.job_id
              AND current_setting('app.role', true) = 'EMPLOYER'
              AND j.employer_id::TEXT = current_setting('app.user_id', true)
        )
    );
