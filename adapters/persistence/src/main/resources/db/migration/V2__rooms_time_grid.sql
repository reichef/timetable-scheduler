CREATE TABLE room_feature (
    id        UUID        PRIMARY KEY,
    school_id UUID        NOT NULL REFERENCES school (id),
    name      VARCHAR(80) NOT NULL
);
CREATE UNIQUE INDEX room_feature_name_unique ON room_feature (school_id, lower(name));

CREATE TABLE room (
    id         UUID        PRIMARY KEY,
    school_id  UUID        NOT NULL REFERENCES school (id),
    identifier VARCHAR(40) NOT NULL,
    version    INTEGER     NOT NULL
);
CREATE UNIQUE INDEX room_identifier_unique ON room (school_id, lower(identifier));

CREATE TABLE room_has_feature (
    room_id    UUID NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    feature_id UUID NOT NULL REFERENCES room_feature (id),
    PRIMARY KEY (room_id, feature_id)
);

CREATE TABLE teaching_day (
    school_id   UUID       NOT NULL REFERENCES school (id) ON DELETE CASCADE,
    day_of_week VARCHAR(9) NOT NULL,
    PRIMARY KEY (school_id, day_of_week)
);

CREATE TABLE time_slot (
    id         UUID        PRIMARY KEY,
    school_id  UUID        NOT NULL REFERENCES school (id),
    label      VARCHAR(40) NOT NULL,
    start_time TIME        NOT NULL,
    end_time   TIME        NOT NULL,
    kind       VARCHAR(8)  NOT NULL CHECK (kind IN ('TEACHING', 'BREAK')),
    CHECK (end_time > start_time)
);

-- Exclusions of a day that is no longer a teaching day are kept on purpose (spec clarification).
CREATE TABLE slot_day_exclusion (
    slot_id     UUID       NOT NULL REFERENCES time_slot (id) ON DELETE CASCADE,
    day_of_week VARCHAR(9) NOT NULL,
    PRIMARY KEY (slot_id, day_of_week)
);
