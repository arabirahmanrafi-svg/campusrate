-- CampusRate schema.
-- Flyway runs this file once, in order, the first time the app starts.
-- Never edit a migration after it has run; add a new V3__..., V4__... file instead.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,           -- BCrypt hash, never the real password
    display_name  VARCHAR(60)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE universities (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(200) NOT NULL,
    city           VARCHAR(100) NOT NULL,
    state          VARCHAR(100),
    country        VARCHAR(100) NOT NULL,
    website        VARCHAR(255),
    -- Stored aggregates: updated in the same transaction as every review change,
    -- so pages never need to recalculate them.
    review_count   INT          NOT NULL DEFAULT 0,
    avg_overall    DOUBLE       NOT NULL DEFAULT 0,
    avg_academics  DOUBLE       NOT NULL DEFAULT 0,
    avg_campus_life DOUBLE      NOT NULL DEFAULT 0,
    avg_housing    DOUBLE       NOT NULL DEFAULT 0,
    avg_food       DOUBLE       NOT NULL DEFAULT 0,
    avg_safety     DOUBLE       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_universities_name ON universities (name);

CREATE TABLE reviews (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    university_id BIGINT        NOT NULL,
    academics     INT           NOT NULL,           -- each rating is 1 to 5
    campus_life   INT           NOT NULL,
    housing       INT           NOT NULL,
    food          INT           NOT NULL,
    safety        INT           NOT NULL,
    overall       DOUBLE        NOT NULL,           -- average of the five ratings
    body          VARCHAR(2000) NOT NULL,
    major         VARCHAR(100),
    grad_year     INT,
    helpful_count INT           NOT NULL DEFAULT 0,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- One review per student per university, enforced by the database itself.
    CONSTRAINT uq_reviews_user_university UNIQUE (user_id, university_id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_university FOREIGN KEY (university_id) REFERENCES universities (id)
);

CREATE INDEX idx_reviews_university_created ON reviews (university_id, created_at);

CREATE TABLE review_votes (
    user_id    BIGINT    NOT NULL,
    review_id  BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- The composite key means a user can mark a review helpful only once.
    PRIMARY KEY (user_id, review_id),
    CONSTRAINT fk_votes_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_votes_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE
);
