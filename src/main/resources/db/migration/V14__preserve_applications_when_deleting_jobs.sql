ALTER TABLE recruitment.applications
    DROP CONSTRAINT applications_job_id_fkey,
    ADD CONSTRAINT applications_job_id_fkey
        FOREIGN KEY (job_id)
        REFERENCES recruitment.jobs (id)
        ON DELETE RESTRICT;
