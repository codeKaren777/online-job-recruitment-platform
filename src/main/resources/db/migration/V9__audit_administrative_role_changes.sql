CREATE TABLE recruitment.admin_role_changes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    administrator_id UUID NOT NULL REFERENCES recruitment.users (id) ON DELETE RESTRICT,
    target_user_id UUID NOT NULL REFERENCES recruitment.users (id) ON DELETE RESTRICT,
    previous_role recruitment.user_role NOT NULL,
    new_role recruitment.user_role NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT admin_role_changes_must_assign_admin CHECK (new_role = 'ADMIN'),
    CONSTRAINT admin_role_changes_must_change_role CHECK (previous_role <> new_role)
);

CREATE INDEX idx_admin_role_changes_target_user
    ON recruitment.admin_role_changes (target_user_id, changed_at DESC);
