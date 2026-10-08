-- school_id: the teacher's school (FR-036); the multi-school extension moves it into a teacher_school table.
CREATE TABLE teacher (
    id          UUID         PRIMARY KEY,
    school_id   UUID         NOT NULL REFERENCES school (id),
    name        VARCHAR(120) NOT NULL,
    short_code  VARCHAR(8)   NOT NULL,
    weekly_load INTEGER      NOT NULL CHECK (weekly_load >= 0),
    version     INTEGER      NOT NULL
);
-- Short codes are stored in upper case and unique in the installation (FR-016).
CREATE UNIQUE INDEX teacher_short_code_unique ON teacher (upper(short_code));

CREATE TABLE teacher_qualification (
    teacher_id UUID NOT NULL REFERENCES teacher (id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject (id),
    PRIMARY KEY (teacher_id, subject_id)
);

-- Removing a time slot removes the unavailabilities entered for it (spec clarification).
CREATE TABLE teacher_unavailability (
    teacher_id  UUID       NOT NULL REFERENCES teacher (id) ON DELETE CASCADE,
    day_of_week VARCHAR(9) NOT NULL,
    slot_id     UUID       NOT NULL REFERENCES time_slot (id) ON DELETE CASCADE,
    PRIMARY KEY (teacher_id, day_of_week, slot_id)
);
CREATE INDEX teacher_unavailability_slot ON teacher_unavailability (slot_id);
