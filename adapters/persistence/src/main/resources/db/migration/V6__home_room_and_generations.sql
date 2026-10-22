-- 002 FR-002a: optional home room per class; a room is the home room of at most one class and cannot be deleted
-- while it is one.
ALTER TABLE school_class ADD COLUMN home_room_id UUID NULL REFERENCES room (id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX school_class_home_room_unique ON school_class (home_room_id) WHERE home_room_id IS NOT NULL;

-- One run of the scheduler (002 FR-022). input_fingerprint identifies the scheduling input at the start (G8).
CREATE TABLE generation (
    id                 UUID                     PRIMARY KEY,
    school_id          UUID                     NOT NULL REFERENCES school (id),
    started_by         UUID                     NOT NULL REFERENCES user_account (id),
    started_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at        TIMESTAMP WITH TIME ZONE NULL,
    time_limit_minutes INTEGER                  NOT NULL CHECK (time_limit_minutes BETWEEN 1 AND 60),
    max_alternatives   INTEGER                  NOT NULL CHECK (max_alternatives >= 1),
    status             VARCHAR(17)              NOT NULL
        CHECK (status IN ('RUNNING', 'FINISHED', 'STOPPED', 'FAILED', 'FAILED_INFEASIBLE')),
    input_fingerprint  CHAR(64)                 NOT NULL,
    alternatives_found INTEGER                  NOT NULL DEFAULT 0 CHECK (alternatives_found >= 0)
);
-- At most one running generation per school (002 FR-011).
CREATE UNIQUE INDEX generation_one_running_per_school ON generation (school_id) WHERE status = 'RUNNING';

-- Objective values are normalized to 0…1 and rounded to 4 decimals (G3).
CREATE TABLE alternative (
    id                 UUID         PRIMARY KEY,
    generation_id      UUID         NOT NULL REFERENCES generation (id) ON DELETE CASCADE,
    class_fill         NUMERIC(5, 4) NOT NULL,
    teacher_load_usage NUMERIC(5, 4) NOT NULL,
    separate_1         NUMERIC(5, 4) NULL,
    separate_2         NUMERIC(5, 4) NULL,
    separate_3         NUMERIC(5, 4) NULL,
    other_soft         NUMERIC(5, 4) NOT NULL
);
CREATE INDEX alternative_generation ON alternative (generation_id);

-- Lessons keep plain ids without foreign keys to master data, so results stay viewable after changes (G7).
CREATE TABLE alternative_lesson (
    alternative_id UUID       NOT NULL REFERENCES alternative (id) ON DELETE CASCADE,
    position       INTEGER    NOT NULL,
    class_id       UUID       NOT NULL,
    subject_id     UUID       NOT NULL,
    teacher_id     UUID       NOT NULL,
    room_id        UUID       NOT NULL,
    day_of_week    VARCHAR(9) NOT NULL,
    time_slot_id   UUID       NOT NULL,
    PRIMARY KEY (alternative_id, position)
);

CREATE TABLE alternative_unplaced (
    alternative_id UUID    NOT NULL REFERENCES alternative (id) ON DELETE CASCADE,
    class_id       UUID    NOT NULL,
    subject_id     UUID    NOT NULL,
    periods        INTEGER NOT NULL CHECK (periods > 0),
    PRIMARY KEY (alternative_id, class_id, subject_id)
);

CREATE TABLE alternative_soft_violation (
    alternative_id UUID    NOT NULL REFERENCES alternative (id) ON DELETE CASCADE,
    constraint_id  UUID    NOT NULL,
    violations     INTEGER NOT NULL CHECK (violations >= 0),
    PRIMARY KEY (alternative_id, constraint_id)
);
