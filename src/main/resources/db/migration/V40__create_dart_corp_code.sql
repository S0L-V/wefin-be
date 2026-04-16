CREATE TABLE dart_corp_code (
    dart_corp_code_id BIGSERIAL PRIMARY KEY,
    stock_code        VARCHAR(20)  NOT NULL,
    corp_code         VARCHAR(20)  NOT NULL,
    corp_name         VARCHAR(200) NOT NULL,
    modify_date       VARCHAR(8),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_dart_corp_code_stock_code UNIQUE (stock_code)
);

CREATE INDEX idx_dart_corp_code_corp_code ON dart_corp_code (corp_code);
