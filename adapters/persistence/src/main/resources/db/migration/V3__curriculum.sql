CREATE TABLE subject (
    id        UUID        PRIMARY KEY,
    school_id UUID        NOT NULL REFERENCES school (id),
    name      VARCHAR(80) NOT NULL,
    version   INTEGER     NOT NULL
);
CREATE UNIQUE INDEX subject_name_unique ON subject (school_id, lower(name));

-- A lesson of the subject needs a room offering all of these features (FR-009).
CREATE TABLE subject_requires_feature (
    subject_id UUID NOT NULL REFERENCES subject (id) ON DELETE CASCADE,
    feature_id UUID NOT NULL REFERENCES room_feature (id),
    PRIMARY KEY (subject_id, feature_id)
);

CREATE TABLE class_level (
    id         UUID        PRIMARY KEY,
    school_id  UUID        NOT NULL REFERENCES school (id),
    identifier VARCHAR(10) NOT NULL,
    version    INTEGER     NOT NULL
);
CREATE UNIQUE INDEX class_level_identifier_unique ON class_level (school_id, lower(identifier));

-- position keeps the order in which the planner entered the curriculum.
CREATE TABLE curriculum_entry (
    class_level_id   UUID    NOT NULL REFERENCES class_level (id) ON DELETE CASCADE,
    subject_id       UUID    NOT NULL REFERENCES subject (id),
    periods_per_week INTEGER NOT NULL CHECK (periods_per_week >= 1),
    position         INTEGER NOT NULL,
    PRIMARY KEY (class_level_id, subject_id)
);

CREATE TABLE school_class (
    id              UUID       PRIMARY KEY,
    class_level_id  UUID       NOT NULL REFERENCES class_level (id),
    suffix          VARCHAR(5) NOT NULL,
    priority_weight INTEGER    NOT NULL DEFAULT 10 CHECK (priority_weight BETWEEN 1 AND 100),
    version         INTEGER    NOT NULL
);
CREATE UNIQUE INDEX school_class_suffix_unique ON school_class (class_level_id, lower(suffix));
