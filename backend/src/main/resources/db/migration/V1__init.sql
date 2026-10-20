CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    phone         VARCHAR(20)  NOT NULL UNIQUE,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    -- Hesabın bağlı olduğu cihaz (boşdursa növbəti girişdə yeni cihaza bağlanır)
    device_id     VARCHAR(100) NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Telefon nömrəsinə görə açarlanır (users-ə bağlı deyil) ki, admin ödənişi
-- şagird qeydiyyatdan keçməmişdən əvvəl də aça bilsin
CREATE TABLE purchases (
    phone      VARCHAR(20) PRIMARY KEY,
    unlocked   BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "number" sınağın daimi identifikatorudur: 1-3 pulsuzdur, 4+ ödənişlidir
CREATE TABLE categories (
    number INT PRIMARY KEY CHECK (number > 0),
    name   VARCHAR(100)
);

INSERT INTO categories (number) SELECT generate_series(1, 25);

CREATE TABLE questions (
    id                   BIGSERIAL PRIMARY KEY,
    category_number      INT    NOT NULL REFERENCES categories (number) ON DELETE CASCADE,
    text                 TEXT   NOT NULL,
    options              TEXT[] NOT NULL,
    correct_answer_index INT    NOT NULL,
    image_url            VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_questions_category ON questions (category_number);

CREATE TABLE results (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Sınaq sonradan silinsə belə nəticə tarixçədə qalsın deyə FK yoxdur
    category_number INT    NOT NULL,
    score           INT    NOT NULL,
    total           INT    NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_results_user ON results (user_id);
