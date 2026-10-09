-- US6: language chosen by the user (de/en); null until the first active choice (FR-039, FR-042).
ALTER TABLE user_account
    ADD COLUMN preferred_language VARCHAR(2) NULL CHECK (preferred_language IN ('de', 'en'));
