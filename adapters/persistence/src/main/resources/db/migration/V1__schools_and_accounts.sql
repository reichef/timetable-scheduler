CREATE TABLE school (
    id      UUID         PRIMARY KEY,
    name    VARCHAR(120) NOT NULL,
    version INTEGER      NOT NULL
);
CREATE UNIQUE INDEX school_name_unique ON school (lower(name));

CREATE TABLE user_account (
    id       UUID        PRIMARY KEY,
    username VARCHAR(60) NOT NULL,
    role     VARCHAR(16) NOT NULL CHECK (role IN ('PLANNER', 'TEACHER')),
    active   BOOLEAN     NOT NULL,
    version  INTEGER     NOT NULL
);
CREATE UNIQUE INDEX user_account_username_unique ON user_account (lower(username));

CREATE TABLE account_school (
    account_id UUID NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    school_id  UUID NOT NULL REFERENCES school (id),
    PRIMARY KEY (account_id, school_id)
);

CREATE TABLE password_credential (
    account_id    UUID         PRIMARY KEY REFERENCES user_account (id) ON DELETE CASCADE,
    password_hash VARCHAR(100) NOT NULL
);
