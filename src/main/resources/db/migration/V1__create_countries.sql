CREATE TABLE countries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    iso_code VARCHAR(2) NOT NULL,
    name VARCHAR(100) NOT NULL,
    capital_city VARCHAR(100) NOT NULL,
    phone_code VARCHAR(20) NOT NULL,
    continent_code VARCHAR(10) NOT NULL,
    currency_iso_code VARCHAR(3) NOT NULL,
    country_flag VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_country_iso UNIQUE (iso_code),
    CONSTRAINT uk_country_name UNIQUE (name)
);
CREATE TABLE languages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_id BIGINT NOT NULL,
    iso_code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT fk_language_country FOREIGN KEY (country_id) REFERENCES countries(id) ON DELETE CASCADE,
    CONSTRAINT uk_country_language UNIQUE (country_id, iso_code)
);
