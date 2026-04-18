ALTER TABLE users
    ADD COLUMN home_group_id BIGINT;

ALTER TABLE users
    ADD CONSTRAINT fk_user_home_group
        FOREIGN KEY (home_group_id)
            REFERENCES groups(group_id);

ALTER TABLE game_news_archive RENAME COLUMN "Field8" TO created_at;

ALTER TABLE game_news_archive ALTER COLUMN source SET NOT NULL;
ALTER TABLE game_news_archive ALTER COLUMN published_at SET NOT NULL;
ALTER TABLE game_news_archive ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE briefing_cache RENAME COLUMN "text" TO briefing_text;