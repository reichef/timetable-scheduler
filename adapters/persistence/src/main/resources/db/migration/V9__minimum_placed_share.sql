-- 002 FR-007a: share of the best alternative's placed lessons every kept alternative must reach, in percent.
ALTER TABLE generation ADD COLUMN minimum_placed_share SMALLINT NOT NULL DEFAULT 90
    CHECK (minimum_placed_share BETWEEN 50 AND 100);
