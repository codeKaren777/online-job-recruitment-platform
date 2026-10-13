-- V2's ALL policies allow relevant employers to DELETE visible rows because
-- DELETE only evaluates USING. Add restrictive mutation guards without changing
-- the existing approved visibility rules or any applied migration.
CREATE POLICY profiles_insert_owner_guard ON recruitment.profiles AS RESTRICTIVE
 FOR INSERT WITH CHECK (current_setting('app.role', true) = 'ADMIN' OR
 (current_setting('app.role', true) = 'JOB_SEEKER' AND user_id::text = current_setting('app.user_id', true)));
CREATE POLICY profiles_update_owner_guard ON recruitment.profiles AS RESTRICTIVE
 FOR UPDATE USING (current_setting('app.role', true) = 'ADMIN' OR
 (current_setting('app.role', true) = 'JOB_SEEKER' AND user_id::text = current_setting('app.user_id', true)))
 WITH CHECK (current_setting('app.role', true) = 'ADMIN' OR
 (current_setting('app.role', true) = 'JOB_SEEKER' AND user_id::text = current_setting('app.user_id', true)));
CREATE POLICY profiles_delete_owner_guard ON recruitment.profiles AS RESTRICTIVE
 FOR DELETE USING (current_setting('app.role', true) = 'ADMIN' OR
 (current_setting('app.role', true) = 'JOB_SEEKER' AND user_id::text = current_setting('app.user_id', true)));
CREATE POLICY documents_insert_owner_guard ON recruitment.documents AS RESTRICTIVE
 FOR INSERT WITH CHECK (current_setting('app.role', true) = 'ADMIN' OR
 (current_setting('app.role', true) = 'JOB_SEEKER' AND profile_user_id::text = current_setting('app.user_id', true)));
CREATE POLICY documents_update_owner_guard ON recruitment.documents AS RESTRICTIVE
 FOR UPDATE USING (current_setting('app.role', true) = 'ADMIN' OR
 (current_setting('app.role', true) = 'JOB_SEEKER' AND profile_user_id::text = current_setting('app.user_id', true)))
 WITH CHECK (current_setting('app.role', true) = 'ADMIN' OR
 (current_setting('app.role', true) = 'JOB_SEEKER' AND profile_user_id::text = current_setting('app.user_id', true)));
CREATE POLICY documents_delete_owner_guard ON recruitment.documents AS RESTRICTIVE
 FOR DELETE USING (current_setting('app.role', true) = 'ADMIN' OR
 (current_setting('app.role', true) = 'JOB_SEEKER' AND profile_user_id::text = current_setting('app.user_id', true)));
