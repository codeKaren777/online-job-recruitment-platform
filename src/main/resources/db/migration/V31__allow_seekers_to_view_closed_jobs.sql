DROP POLICY jobs_owner_or_active_listing ON recruitment.jobs;

CREATE POLICY jobs_owner_or_active_listing ON recruitment.jobs
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR employer_id::TEXT = current_setting('app.user_id', true)
        OR (
            current_setting('app.role', true) = 'JOB_SEEKER'
            AND (
                (status = 'ACTIVE'
                 AND due_date > (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date)
                OR status = 'CLOSED'
                OR recruitment.applicant_has_application_for_job(
                    jobs.id,
                    current_setting('app.user_id', true)::UUID
                )
            )
        )
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR (
            current_setting('app.role', true) = 'EMPLOYER'
            AND employer_id::TEXT = current_setting('app.user_id', true)
        )
    );
