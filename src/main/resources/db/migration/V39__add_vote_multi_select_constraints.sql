-- 최대 선택 개수 칼럼 추가
ALTER TABLE vote
    ADD COLUMN max_select_count INT NOT NULL DEFAULT 1;

-- vote_answer에 vote_id 추가
ALTER TABLE vote_answer
    ADD COLUMN vote_id BIGINT NOT NULL;

ALTER TABLE vote_answer
    ADD CONSTRAINT fk_vote_answer_vote
        FOREIGN KEY (vote_id) REFERENCES vote(vote_id);

-- 같은 옵션 중복 선택 방지
ALTER TABLE vote_answer
    ADD CONSTRAINT uq_vote_answer_user_option
        UNIQUE (user_id, option_id);