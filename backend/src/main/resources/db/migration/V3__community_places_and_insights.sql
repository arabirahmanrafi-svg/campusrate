-- Phases 2-7: places, international features, photos, Q&A, follows, notifications,
-- moderation and insights. Added as a NEW migration so databases that already ran
-- V1 and V2 just get these changes on the next start.

-- ---------- Profiles, roles and reputation ----------
ALTER TABLE users
    ADD COLUMN role             VARCHAR(20) NOT NULL DEFAULT 'STUDENT',   -- STUDENT or ADMIN
    ADD COLUMN student_status   VARCHAR(20) NOT NULL DEFAULT 'CURRENT',   -- CURRENT, ALUMNI, TRANSFER, PROSPECTIVE
    ADD COLUMN is_international BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN home_country     VARCHAR(80),
    ADD COLUMN show_country     BOOLEAN     NOT NULL DEFAULT FALSE,       -- hidden unless the student opts in
    ADD COLUMN reputation       INT         NOT NULL DEFAULT 0;

-- ---------- Universities: map position, international score, trend ----------
ALTER TABLE universities
    ADD COLUMN latitude          DOUBLE,
    ADD COLUMN longitude         DOUBLE,
    ADD COLUMN avg_international DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN intl_review_count INT    NOT NULL DEFAULT 0,
    ADD COLUMN trend_delta       DOUBLE NOT NULL DEFAULT 0;

-- ---------- Reviews: international ratings, sentiment, moderation ----------
ALTER TABLE reviews
    ADD COLUMN intl_support     INT,                                       -- all four are filled in together, or all left empty
    ADD COLUMN visa_help        INT,
    ADD COLUMN job_availability INT,
    ADD COLUMN welcoming        INT,
    ADD COLUMN sentiment        DOUBLE      NOT NULL DEFAULT 0,            -- -1 (negative) to +1 (positive), computed when saved
    ADD COLUMN status           VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';  -- PUBLISHED, FLAGGED or REMOVED

CREATE INDEX idx_reviews_university_status ON reviews (university_id, status);

-- Why a review was flagged by the fake-review rules.
CREATE TABLE review_flags (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    review_id  BIGINT       NOT NULL,
    rule       VARCHAR(40)  NOT NULL,
    detail     VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_flags_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE
);

-- ---------- Places inside a university: dorms, dining, courses, jobs, clubs ----------
-- One generic table for all five types instead of five near-identical tables.
CREATE TABLE places (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    university_id BIGINT       NOT NULL,
    type          VARCHAR(20)  NOT NULL,                  -- DORM, DINING, COURSE, JOB, CLUB
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500),
    status        VARCHAR(20)  NOT NULL DEFAULT 'APPROVED', -- student suggestions start as PENDING
    created_by    BIGINT,
    review_count  INT          NOT NULL DEFAULT 0,
    avg_rating    DOUBLE       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_places_university FOREIGN KEY (university_id) REFERENCES universities (id),
    CONSTRAINT fk_places_user FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX idx_places_university_type ON places (university_id, type, status);

-- Ratings differ by place type (a dorm has "noise", a job has "pay"), so the
-- type-specific scores are stored as JSON text, e.g. {"cleanliness":4,"noise":2}.
CREATE TABLE place_reviews (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    place_id   BIGINT        NOT NULL,
    user_id    BIGINT        NOT NULL,
    rating     DOUBLE        NOT NULL,          -- average of the type-specific ratings
    ratings    VARCHAR(1000) NOT NULL,          -- JSON
    extras     VARCHAR(1000) NOT NULL,          -- JSON, e.g. {"meal":"Butter chicken"} or {"hourlyPay":"13"}
    body       VARCHAR(1500) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_place_reviews_user UNIQUE (place_id, user_id),
    CONSTRAINT fk_place_reviews_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT fk_place_reviews_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_place_reviews_created ON place_reviews (place_id, created_at);

-- ---------- Photos (campus gallery, places and reviews) ----------
CREATE TABLE photos (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    university_id BIGINT       NOT NULL,
    place_id      BIGINT,
    review_id     BIGINT,
    user_id       BIGINT       NOT NULL,
    file_name     VARCHAR(100) NOT NULL,           -- random name on disk; the original name is never used
    caption       VARCHAR(200),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
    report_count  INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_photos_university FOREIGN KEY (university_id) REFERENCES universities (id),
    CONSTRAINT fk_photos_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT fk_photos_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_photos_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_photos_university_status ON photos (university_id, status);

-- One report per student per photo, so a single person can't hide a photo by reporting it many times.
CREATE TABLE photo_reports (
    user_id  BIGINT NOT NULL,
    photo_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, photo_id),
    CONSTRAINT fk_photo_reports_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_photo_reports_photo FOREIGN KEY (photo_id) REFERENCES photos (id) ON DELETE CASCADE
);

-- ---------- International students: cost of living and community links ----------
CREATE TABLE cost_reports (
    id               BIGINT    NOT NULL AUTO_INCREMENT,
    university_id    BIGINT    NOT NULL,
    user_id          BIGINT    NOT NULL,
    rent             INT       NOT NULL,       -- all amounts are US dollars per month
    groceries        INT       NOT NULL,
    transport        INT       NOT NULL,
    phone            INT       NOT NULL,
    tuition_per_year INT,                      -- optional, dollars per year
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_cost_reports_user UNIQUE (university_id, user_id),
    CONSTRAINT fk_costs_university FOREIGN KEY (university_id) REFERENCES universities (id),
    CONSTRAINT fk_costs_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE community_links (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    university_id BIGINT       NOT NULL,
    kind          VARCHAR(20)  NOT NULL,           -- FOOD, ASSOCIATION, OTHER
    name          VARCHAR(150) NOT NULL,
    url           VARCHAR(500),
    description   VARCHAR(300),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_by    BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_links_university FOREIGN KEY (university_id) REFERENCES universities (id),
    CONSTRAINT fk_links_user FOREIGN KEY (created_by) REFERENCES users (id)
);

-- ---------- Q&A ----------
CREATE TABLE questions (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    university_id BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    title         VARCHAR(150)  NOT NULL,
    body          VARCHAR(2000),
    answer_count  INT           NOT NULL DEFAULT 0,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_questions_university FOREIGN KEY (university_id) REFERENCES universities (id),
    CONSTRAINT fk_questions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_questions_university ON questions (university_id, created_at);

CREATE TABLE answers (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    question_id BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    votes       INT           NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    CONSTRAINT fk_answers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE answer_votes (
    user_id   BIGINT NOT NULL,
    answer_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, answer_id),
    CONSTRAINT fk_answer_votes_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_answer_votes_answer FOREIGN KEY (answer_id) REFERENCES answers (id) ON DELETE CASCADE
);

-- ---------- Follows and notifications ----------
CREATE TABLE follows (
    user_id       BIGINT    NOT NULL,
    university_id BIGINT    NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, university_id),
    CONSTRAINT fk_follows_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_follows_university FOREIGN KEY (university_id) REFERENCES universities (id)
);

CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    message    VARCHAR(300) NOT NULL,
    link       VARCHAR(200),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_notifications_user ON notifications (user_id, is_read, created_at);
