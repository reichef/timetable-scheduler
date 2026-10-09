-- Planner-defined scheduling constraints (002 FR-012 – FR-015, G9). One table for all catalogue types with typed,
-- restricting references, so entries used by a constraint cannot be deleted (002 FR-016).
CREATE TABLE scheduling_constraint (
    id            UUID        PRIMARY KEY,
    school_id     UUID        NOT NULL REFERENCES school (id),
    type          VARCHAR(40) NOT NULL CHECK (type IN (
        'CLASS_MAX_PERIODS_PER_DAY', 'CLASS_MIN_PERIODS_PER_DAY', 'CLASS_NO_GAPS', 'TEACHER_MAX_PERIODS_PER_DAY',
        'TEACHER_MAX_GAPS_PER_DAY', 'SUBJECT_MAX_PER_DAY_IN_CLASS', 'SUBJECT_DOUBLE_PERIODS_IN_CLASS', 'CLASS_EXCLUDED_SLOTS',
        'TEACHER_EXCLUDED_SLOTS', 'SUBJECT_EXCLUDED_SLOTS')),
    kind          VARCHAR(4)  NOT NULL CHECK (kind IN ('HARD', 'SOFT')),
    weight        VARCHAR(6)  NULL CHECK (weight IN ('LOW', 'MEDIUM', 'HIGH')),
    active        BOOLEAN     NOT NULL,
    all_classes   BOOLEAN     NOT NULL DEFAULT FALSE,
    class_id      UUID        NULL REFERENCES school_class (id) ON DELETE RESTRICT,
    teacher_id    UUID        NULL REFERENCES teacher (id) ON DELETE RESTRICT,
    subject_id    UUID        NULL REFERENCES subject (id) ON DELETE RESTRICT,
    limit_value   INTEGER     NULL CHECK (limit_value >= 0),
    across_breaks BOOLEAN     NULL,
    version       INTEGER     NOT NULL,
    -- Exactly soft constraints carry a weight (FR-014).
    CHECK ((kind = 'SOFT') = (weight IS NOT NULL)),
    -- Class-related types apply to all classes or to one class, never both.
    CHECK (NOT (all_classes AND class_id IS NOT NULL)),
    CHECK (type NOT IN ('CLASS_MAX_PERIODS_PER_DAY', 'CLASS_MIN_PERIODS_PER_DAY', 'CLASS_NO_GAPS', 'SUBJECT_MAX_PER_DAY_IN_CLASS')
        OR all_classes OR class_id IS NOT NULL),
    CHECK (type NOT IN ('TEACHER_MAX_PERIODS_PER_DAY', 'TEACHER_MAX_GAPS_PER_DAY', 'TEACHER_EXCLUDED_SLOTS') OR teacher_id IS NOT NULL),
    CHECK (type NOT IN ('CLASS_MAX_PERIODS_PER_DAY', 'CLASS_MIN_PERIODS_PER_DAY', 'TEACHER_MAX_PERIODS_PER_DAY',
        'TEACHER_MAX_GAPS_PER_DAY', 'SUBJECT_MAX_PER_DAY_IN_CLASS') OR limit_value IS NOT NULL),
    CHECK (type <> 'SUBJECT_DOUBLE_PERIODS_IN_CLASS' OR (class_id IS NOT NULL AND subject_id IS NOT NULL AND across_breaks IS NOT NULL)),
    CHECK (type <> 'CLASS_EXCLUDED_SLOTS' OR class_id IS NOT NULL),
    CHECK (type <> 'SUBJECT_EXCLUDED_SLOTS' OR subject_id IS NOT NULL)
);
CREATE INDEX scheduling_constraint_school ON scheduling_constraint (school_id);

-- Excluded slots of the exclusion types.
CREATE TABLE scheduling_constraint_slot (
    constraint_id UUID       NOT NULL REFERENCES scheduling_constraint (id) ON DELETE CASCADE,
    day_of_week   VARCHAR(9) NOT NULL,
    slot_id       UUID       NOT NULL REFERENCES time_slot (id) ON DELETE RESTRICT,
    PRIMARY KEY (constraint_id, day_of_week, slot_id)
);
CREATE INDEX scheduling_constraint_slot_slot ON scheduling_constraint_slot (slot_id);

-- Teacher fixations (002 FR-013a): always hard, at most one per class and subject.
CREATE TABLE teacher_fixation (
    id         UUID    PRIMARY KEY,
    school_id  UUID    NOT NULL REFERENCES school (id),
    class_id   UUID    NOT NULL REFERENCES school_class (id) ON DELETE RESTRICT,
    subject_id UUID    NOT NULL REFERENCES subject (id) ON DELETE RESTRICT,
    teacher_id UUID    NOT NULL REFERENCES teacher (id) ON DELETE RESTRICT,
    version    INTEGER NOT NULL,
    UNIQUE (class_id, subject_id)
);

-- Soft constraints compared as separate objectives (002 FR-006a); they refer to the generation's snapshot.
CREATE TABLE generation_separate_objective (
    generation_id          UUID     NOT NULL REFERENCES generation (id) ON DELETE CASCADE,
    position               SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 3),
    original_constraint_id UUID     NOT NULL,
    PRIMARY KEY (generation_id, position)
);

-- Hard constraints named when a generation ended as infeasible (002 FR-017).
CREATE TABLE generation_infeasible_constraint (
    generation_id          UUID NOT NULL REFERENCES generation (id) ON DELETE CASCADE,
    original_constraint_id UUID NOT NULL,
    PRIMARY KEY (generation_id, original_constraint_id)
);

-- Snapshot of the constraints and fixations in effect at the start (002 FR-022). Plain ids without foreign keys,
-- so later changes or deletions neither alter nor block it (G7).
CREATE TABLE generation_constraint (
    generation_id          UUID        NOT NULL REFERENCES generation (id) ON DELETE CASCADE,
    position               INTEGER     NOT NULL,
    original_constraint_id UUID        NOT NULL,
    type                   VARCHAR(40) NOT NULL,
    kind                   VARCHAR(4)  NOT NULL,
    weight                 VARCHAR(6)  NULL,
    active                 BOOLEAN     NOT NULL,
    all_classes            BOOLEAN     NOT NULL,
    class_id               UUID        NULL,
    teacher_id             UUID        NULL,
    subject_id             UUID        NULL,
    limit_value            INTEGER     NULL,
    across_breaks          BOOLEAN     NULL,
    version                INTEGER     NOT NULL,
    PRIMARY KEY (generation_id, position)
);

CREATE TABLE generation_constraint_slot (
    generation_id UUID       NOT NULL,
    position      INTEGER    NOT NULL,
    day_of_week   VARCHAR(9) NOT NULL,
    slot_id       UUID       NOT NULL,
    PRIMARY KEY (generation_id, position, day_of_week, slot_id),
    FOREIGN KEY (generation_id, position) REFERENCES generation_constraint (generation_id, position) ON DELETE CASCADE
);

CREATE TABLE generation_fixation (
    generation_id         UUID    NOT NULL REFERENCES generation (id) ON DELETE CASCADE,
    position              INTEGER NOT NULL,
    original_fixation_id  UUID    NOT NULL,
    class_id              UUID    NOT NULL,
    subject_id            UUID    NOT NULL,
    teacher_id            UUID    NOT NULL,
    version               INTEGER NOT NULL,
    PRIMARY KEY (generation_id, position)
);

-- 002 FR-013c: the existing school starts with the two soft default constraints of weight medium.
INSERT INTO scheduling_constraint (id, school_id, type, kind, weight, active, all_classes, subject_id, limit_value, version)
SELECT gen_random_uuid(), id, 'SUBJECT_MAX_PER_DAY_IN_CLASS', 'SOFT', 'MEDIUM', TRUE, TRUE, NULL, 2, 0 FROM school;
INSERT INTO scheduling_constraint (id, school_id, type, kind, weight, active, all_classes, version)
SELECT gen_random_uuid(), id, 'CLASS_NO_GAPS', 'SOFT', 'MEDIUM', TRUE, TRUE, 0 FROM school;
