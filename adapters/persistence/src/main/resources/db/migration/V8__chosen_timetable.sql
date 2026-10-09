-- The school's chosen timetable (002 FR-021): one per school, copied from an alternative. The lessons keep restricting
-- references, so master data used by it cannot be deleted (002 FR-016), and the alternative it came from cannot be
-- removed while it is chosen (002 FR-024).
CREATE TABLE chosen_timetable (
    school_id      UUID                     PRIMARY KEY REFERENCES school (id),
    alternative_id UUID                     NOT NULL REFERENCES alternative (id) ON DELETE RESTRICT,
    generation_id  UUID                     NOT NULL REFERENCES generation (id) ON DELETE RESTRICT,
    chosen_by      UUID                     NOT NULL REFERENCES user_account (id),
    chosen_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version        INTEGER                  NOT NULL
);

CREATE TABLE chosen_lesson (
    school_id    UUID       NOT NULL REFERENCES chosen_timetable (school_id) ON DELETE CASCADE,
    position     INTEGER    NOT NULL,
    class_id     UUID       NOT NULL REFERENCES school_class (id) ON DELETE RESTRICT,
    subject_id   UUID       NOT NULL REFERENCES subject (id) ON DELETE RESTRICT,
    teacher_id   UUID       NOT NULL REFERENCES teacher (id) ON DELETE RESTRICT,
    room_id      UUID       NOT NULL REFERENCES room (id) ON DELETE RESTRICT,
    day_of_week  VARCHAR(9) NOT NULL,
    time_slot_id UUID       NOT NULL REFERENCES time_slot (id) ON DELETE RESTRICT,
    PRIMARY KEY (school_id, position)
);

CREATE TABLE chosen_unplaced (
    school_id  UUID    NOT NULL REFERENCES chosen_timetable (school_id) ON DELETE CASCADE,
    class_id   UUID    NOT NULL REFERENCES school_class (id) ON DELETE RESTRICT,
    subject_id UUID    NOT NULL REFERENCES subject (id) ON DELETE RESTRICT,
    periods    INTEGER NOT NULL CHECK (periods > 0),
    PRIMARY KEY (school_id, class_id, subject_id)
);

-- Starting from the chosen timetable (002 FR-006b): the alternative compared with, and how stable each result is.
ALTER TABLE generation ADD COLUMN reference_alternative_id UUID NULL REFERENCES alternative (id) ON DELETE SET NULL;
ALTER TABLE alternative ADD COLUMN stability NUMERIC(5, 4) NULL;
ALTER TABLE alternative ADD COLUMN changed_lessons INTEGER NULL CHECK (changed_lessons >= 0);
