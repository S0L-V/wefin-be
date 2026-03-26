CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE "stock_daily" (
                               "daily_id"	UUID		NOT NULL,
                               "symbol"	VARCHAR		NOT NULL,
                               "trade_date"	DATE		NOT NULL,
                               "open_price"	DECIMAL(18,2)		NULL,
                               "high_price"	DECIMAL(18,2)		NULL,
                               "low_price"	DECIMAL(18,2)		NULL,
                               "close_price"	DECIMAL(18,2)		NULL,
                               "volume"	DECIMAL(18,2)		NULL,
                               "change_rate"	DECIMAL(8,2)		NULL
);


CREATE TABLE "briefing_cache" (
                                  "briefing_id"	UUID		NOT NULL,
                                  "target_date"	DATE		NOT NULL,
                                  "text"	TEXT		NOT NULL,
                                  "created_at"	TIMESTAMPTZ		NOT NULL
);


CREATE TABLE "game_participant" (
                                    "participant_id"	UUID		NOT NULL,
                                    "user_id"	UUID		NOT NULL,
                                    "room_id"	UUID		NOT NULL,
                                    "is_leader"	BOOLEAN		NOT NULL,
                                    "seed"	BIGINT		NULL,
                                    "status"	VARCHAR		NULL,
                                    "joined_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NULL
);

COMMENT ON COLUMN "game_participant"."seed" IS '지급받은 시드머니';

COMMENT ON COLUMN "game_participant"."status" IS 'ACTIVE / LEFT';


CREATE TABLE "daily_snapshot" (
                                  "daily_snapshot_id"	BIGSERIAL	NOT NULL,
                                  "virtual_account_id"	BIGINT		NOT NULL,
                                  "snapshot_date"	DATE		NOT NULL,
                                  "total_asset"	NUMERIC(15, 2)		NOT NULL,
                                  "balance"	NUMERIC(15,  2)		NOT NULL,
                                  "evaluation_amount"	NUMERIC(15, 2)		NOT NULL,
                                  "realized_profit"	NUMERIC(15, 2)		NOT NULL,
                                  "created_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL
);

COMMENT ON COLUMN "daily_snapshot"."snapshot_date" IS '스냅샷 기준일';

COMMENT ON COLUMN "daily_snapshot"."total_asset" IS '예수금 + 보유 평가 금액';

COMMENT ON COLUMN "daily_snapshot"."realized_profit" IS '그날 확정 손익';

COMMENT ON COLUMN "daily_snapshot"."created_at" IS '스냅샷 생성 시간';


CREATE TABLE "news_article_tag" (
                                    "news_article_tag_id"	BIGSERIAL		NOT NULL,
                                    "news_article_id"	BIGINT		NOT NULL,
                                    "tag_type"	VARCHAR(30)		NOT NULL,
                                    "tag_code"	VARCHAR(100)		NOT NULL,
                                    "tag_name"	VARCHAR(100)		NOT NULL,
                                    "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "news_article_tag"."news_article_tag_id" IS 'news_article_tag 식별자';

COMMENT ON COLUMN "news_article_tag"."news_article_id" IS 'news_article.news_article_id 참조';

COMMENT ON COLUMN "news_article_tag"."tag_type" IS 'STOCK / SECTOR / TOPIC';

COMMENT ON COLUMN "news_article_tag"."tag_code" IS '태그 코드';

COMMENT ON COLUMN "news_article_tag"."tag_name" IS '태그명';

COMMENT ON COLUMN "news_article_tag"."created_at" IS '생성 시각';


CREATE TABLE "stock_info" (
                              "symbol"	VARCHAR		NOT NULL,
                              "stock_name"	VARCHAR		NOT NULL,
                              "market"	VARCHAR		NOT NULL,
                              "sector"	VARCHAR		NULL
);

COMMENT ON COLUMN "stock_info"."market" IS 'kospi / kosdaq';


CREATE TABLE "vote_option" (
                               "option_id"	BIGSERIAL		NOT NULL,
                               "vote_id"	BIGINT		NOT NULL,
                               "option_text"	VARCHAR(30)		NOT NULL,
                               "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);


CREATE TABLE "game_order" (
                              "order_id"	UUID		NOT NULL,
                              "participant_id"	UUID		NOT NULL,
                              "turn_id"	UUID		NOT NULL,
                              "symbol"	VARCHAR		NOT NULL,
                              "stock_name"	VARCHAR		NULL,
                              "order_type"	VARCHAR		NULL,
                              "order_price"	BIGINT		NULL,
                              "quantity"	INT		NULL,
                              "fee"	BIGINT		NULL,
                              "tax"	BIGINT		NULL
);

COMMENT ON COLUMN "game_order"."order_type" IS 'BUY / SELL';

COMMENT ON COLUMN "game_order"."fee" IS '0.015';

COMMENT ON COLUMN "game_order"."tax" IS '0.18 매도만';


CREATE TABLE "market_insight_card" (
                                       "market_insight_card_id"	BIGSERIAL		NOT NULL,
                                       "market_trend_id"	BIGSERIAL		NOT NULL,
                                       "target_type"	VARCHAR(30)		NOT NULL,
                                       "target_code"	VARCHAR(100)		NOT NULL,
                                       "target_name"	VARCHAR(100)		NOT NULL,
                                       "title"	VARCHAR(200)		NOT NULL,
                                       "summary"	TEXT		NOT NULL,
                                       "display_order"	INT		NOT NULL,
                                       "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "market_insight_card"."market_insight_card_id" IS 'market_insight_card 식별자';

COMMENT ON COLUMN "market_insight_card"."target_type" IS 'STOCK / SECTOR / TOPIC';

COMMENT ON COLUMN "market_insight_card"."target_code" IS '대상 코드';

COMMENT ON COLUMN "market_insight_card"."target_name" IS '대상명';

COMMENT ON COLUMN "market_insight_card"."title" IS '카드 제목';

COMMENT ON COLUMN "market_insight_card"."summary" IS '카드 요약';

COMMENT ON COLUMN "market_insight_card"."display_order" IS '정렬 순서';

COMMENT ON COLUMN "market_insight_card"."created_at" IS '생성 시각';


CREATE TABLE "trade" (
                         "trade_id"	BIGSERIAL	NOT NULL,
                         "trade_no"	UUID	DEFAULT gen_random_uuid()	NOT NULL,
                         "order_id"	BIGINT		NOT NULL,
                         "virtual_account_id"	BIGINT		NOT NULL,
                         "stock_id"	BIGINT		NOT NULL,
                         "side"	VARCHAR(4)		NOT NULL,
                         "quantity"	NUMERIC(15, 2)		NOT NULL,
                         "price"	NUMERIC(15, 2)		NOT NULL,
                         "total_amount"	NUMERIC(15, 2)		NOT NULL,
                         "fee"	NUMERIC(15, 2)		NOT NULL,
                         "tax"	NUMERIC(15, 2)		NOT NULL,
                         "realized_profit"	NUMERIC(15, 2)		NULL,
                         "currency"	VARCHAR(3)	DEFAULT 'KRW'	NOT NULL,
                         "exchange_rate"	NUMERIC(15, 2)		NULL,
                         "created_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL
);

COMMENT ON COLUMN "trade"."trade_no" IS '외부 노출용, UNIQUE';

COMMENT ON COLUMN "trade"."order_id" IS 'orders참조';

COMMENT ON COLUMN "trade"."virtual_account_id" IS 'virtual_account 참조';

COMMENT ON COLUMN "trade"."stock_id" IS 'stock 참조';

COMMENT ON COLUMN "trade"."side" IS 'BUY / SELL';

COMMENT ON COLUMN "trade"."quantity" IS '이번 체결 수량';

COMMENT ON COLUMN "trade"."price" IS '실제 체결 가격';

COMMENT ON COLUMN "trade"."total_amount" IS 'price * quantity';

COMMENT ON COLUMN "trade"."fee" IS '이번 체결 수수료';

COMMENT ON COLUMN "trade"."tax" IS '이번 체결 세금';

COMMENT ON COLUMN "trade"."realized_profit" IS '매도 시만';

COMMENT ON COLUMN "trade"."currency" IS 'KRW / USD';

COMMENT ON COLUMN "trade"."exchange_rate" IS '체결 시점 환율 (해외만)';

COMMENT ON COLUMN "trade"."created_at" IS '체결 시간';


CREATE TABLE "subscription_plan" (
                                     "plan_id"	BIGSERIAL		NOT NULL,
                                     "plan_name"	VARCHAR(100)		NOT NULL,
                                     "price"	NUMERIC(12,2)		NOT NULL,
                                     "billing_cycle"	VARCHAR(20)		NOT NULL,
                                     "description"	TEXT		NULL,
                                     "is_active"	BOOLEAN	DEFAULT TRUE	NOT NULL,
                                     "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                                     "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "subscription_plan"."billing_cycle" IS 'MONTHLY / YEARLY';

COMMENT ON COLUMN "subscription_plan"."description" IS '상품 설명';


CREATE TABLE "groups" (
                          "group_id"	BIGSERIAL		NOT NULL,
                          "group_name"	VARCHAR(100)		NOT NULL,
                          "created_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL
);

COMMENT ON COLUMN "groups"."group_id" IS '그룹 아이디 복합 pk';


CREATE TABLE "game_news_archive" (
                                     "news_id"	UUID		NOT NULL,
                                     "title"	VARCHAR(500)		NOT NULL,
                                     "summary"	TEXT		NOT NULL,
                                     "source"	VARCHAR(100)		NULL,
                                     "original_url"	VARCHAR(1000)		NOT NULL,
                                     "published_at"	TIMESTAMPTZ		NULL,
                                     "category"	VARCHAR(50)		NULL,
                                     "keyword"	VARCHAR(100)		NULL,
                                     "Field8"	TIMESTAMPTZ		NULL
);


CREATE TABLE "users" (
                         "user_id"	UUID		NOT NULL,
                         "email"	VARCHAR(100)		NOT NULL,
                         "nickname"	VARCHAR(20)		NOT NULL,
                         "password"	VARCHAR(255)		NOT NULL,
                         "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                         "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "users"."user_id" IS '회원 PK';

COMMENT ON COLUMN "users"."email" IS '로그인 이메일';

COMMENT ON COLUMN "users"."nickname" IS '닉네임';

COMMENT ON COLUMN "users"."password" IS '암호화 비밀번호';


CREATE TABLE "game_holding" (
                                "holding_id"	UUID		NOT NULL,
                                "participant_id"	UUID		NOT NULL,
                                "symbol"	VARCHAR		NOT NULL,
                                "stock_name"	VARCHAR		NOT NULL,
                                "avg_price"	BIGINT		NOT NULL
);


CREATE TABLE "game_room" (
                             "room_id"	UUID		NOT NULL,
                             "group_id"	BIGINT		NOT NULL,
                             "user_id"	UUID		NOT NULL,
                             "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                             "seed"	BIGINT		NOT NULL,
                             "period_month"	INT		NOT NULL,
                             "move_days"	INT		NOT NULL,
                             "start_date"	DATE		NOT NULL,
                             "end_date"	DATE		NOT NULL,
                             "status"	VARCHAR		NOT NULL
);

COMMENT ON COLUMN "game_room"."group_id" IS '그룹 아이디 복합 pk';

COMMENT ON COLUMN "game_room"."user_id" IS '회원 PK';

COMMENT ON COLUMN "game_room"."status" IS 'PENDING / START/EXPIRED';


CREATE TABLE "article_embedding" (
                                     "article_embedding_id"	BIGSERIAL		NOT NULL,
                                     "news_article_id"	BIGINT		NOT NULL,
                                     "chunk_index"	INT		NOT NULL,
                                     "chunk_text"	TEXT		NOT NULL,
                                     "embedding_vector"	VECTOR		NOT NULL,
                                     "model_name"	VARCHAR(100)		NOT NULL,
                                     "token_count"	INT		NULL,
                                     "created_at"	TIMESTAMPTZ		NOT NULL
);

COMMENT ON COLUMN "article_embedding"."article_embedding_id" IS 'article_embedding 식별자';

COMMENT ON COLUMN "article_embedding"."news_article_id" IS 'news_article.news_article_id 참조';

COMMENT ON COLUMN "article_embedding"."chunk_index" IS '기사 내 청크 순번';

COMMENT ON COLUMN "article_embedding"."chunk_text" IS '임베딩 대상 텍스트';

COMMENT ON COLUMN "article_embedding"."embedding_vector" IS 'pgvector 임베딩 벡터';

COMMENT ON COLUMN "article_embedding"."model_name" IS '임베딩 모델명';

COMMENT ON COLUMN "article_embedding"."token_count" IS '청크 토큰 수';

COMMENT ON COLUMN "article_embedding"."created_at" IS '생성 시각';


CREATE TABLE "user_interest" (
                                 "user_interest_id"	VARCHAR(255)		NOT NULL,
                                 "user_id"	UUID		NOT NULL,
                                 "interest_type"	VARCHAR(255)		NULL,
                                 "interest_value"	VARCHAR(255)		NULL,
                                 "weight"	VARCHAR(255)		NULL,
                                 "created_at"	TIMESTAMPTZ		NULL
);

COMMENT ON COLUMN "user_interest"."user_id" IS '회원 PK';

COMMENT ON COLUMN "user_interest"."interest_type" IS 'STOCK, SECTOR, TOPIC';

COMMENT ON COLUMN "user_interest"."interest_value" IS '삼성전자, 반도체, 금리';


CREATE TABLE "market_trend" (
                                "market_trend_id"	BIGSERIAL		NOT NULL,
                                "trend_date"	DATE		NOT NULL,
                                "session"	VARCHAR(20)		NOT NULL,
                                "created_at"	TIMESTAMPTZ		NOT NULL
);

COMMENT ON COLUMN "market_trend"."trend_date" IS '금융 동향 기준 날짜';

COMMENT ON COLUMN "market_trend"."session" IS 'MORNING / EVENING';

COMMENT ON COLUMN "market_trend"."created_at" IS 'CURRENT_TIMESTAMP';


CREATE TABLE "news_cluster_summary" (
                                        "news_cluster_summary_id"	BIGSERIAL		NOT NULL,
                                        "news_cluster_id"	BIGSERIAL		NOT NULL,
                                        "summary_text"	TEXT		NOT NULL,
                                        "model_name"	VARCHAR(100)		NULL,
                                        "prompt_version"	VARCHAR(50)		NULL,
                                        "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "news_cluster_summary"."news_cluster_summary_id" IS 'news_cluster_summary 식별자';

COMMENT ON COLUMN "news_cluster_summary"."news_cluster_id" IS 'news_cluster.news_cluster_id 참조';

COMMENT ON COLUMN "news_cluster_summary"."summary_text" IS 'AI 요약 본문';

COMMENT ON COLUMN "news_cluster_summary"."model_name" IS '모델명';

COMMENT ON COLUMN "news_cluster_summary"."prompt_version" IS '프롬프트 버전';

COMMENT ON COLUMN "news_cluster_summary"."created_at" IS '생성 시각';


CREATE TABLE "user_news_cluster_feedback" (
                                              "user_news_cluster_feedback_id"	BIGSERIAL		NOT NULL,
                                              "user_id"	UUID		NOT NULL,
                                              "news_cluster_id"	BIGINT		NOT NULL,
                                              "feedback_type"	VARCHAR(30)		NOT NULL,
                                              "submitted_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "user_news_cluster_feedback"."user_news_cluster_feedback_id" IS 'user_news_cluster_feedback 식별자';

COMMENT ON COLUMN "user_news_cluster_feedback"."user_id" IS '회원 PK';

COMMENT ON COLUMN "user_news_cluster_feedback"."news_cluster_id" IS 'news_cluster.news_cluster_id 참조';

COMMENT ON COLUMN "user_news_cluster_feedback"."feedback_type" IS 'HELPFUL / NOT_HELPFUL';

COMMENT ON COLUMN "user_news_cluster_feedback"."submitted_at" IS '피드백 등록 시각';


CREATE TABLE "vote_answer" (
                               "answer_id"	BIGSERIAL		NOT NULL,
                               "option_id"	BIGINT		NOT NULL,
                               "user_id"	UUID		NOT NULL,
                               "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "vote_answer"."user_id" IS '회원 PK';


CREATE TABLE "group_invite" (
                                "code_id"	BIGSERIAL		NOT NULL,
                                "group_id"	BIGINT		NOT NULL,
                                "created_by"	UUID		NOT NULL,
                                "created_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL,
                                "expired_at"	TIMESTAMPTZ		NOT NULL,
                                "status"	VARCHAR(20)	DEFAULT 'PENDING'	NOT NULL,
                                "invite_code"	UUID		NOT NULL
);

COMMENT ON COLUMN "group_invite"."code_id" IS 'UUID';

COMMENT ON COLUMN "group_invite"."group_id" IS '그룹 아이디 복합 pk';

COMMENT ON COLUMN "group_invite"."created_by" IS '회원 PK';

COMMENT ON COLUMN "group_invite"."created_at" IS '초대 코드 생성 시각';

COMMENT ON COLUMN "group_invite"."expired_at" IS '스케줄러 n분 후 만료';

COMMENT ON COLUMN "group_invite"."status" IS 'PENDING / ACCEPTED / EXPIRED';


CREATE TABLE "news_source" (
                               "news_source_id"	BIGSERIAL		NOT NULL,
                               "source_name"	VARCHAR(100)		NOT NULL,
                               "source_type"	VARCHAR(50)		NOT NULL,
                               "base_url"	TEXT		NULL,
                               "is_active"	BOOLEAN		NOT NULL,
                               "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "news_source"."news_source_id" IS 'news_source 식별자';

COMMENT ON COLUMN "news_source"."source_name" IS '뉴스 수집 출처명';

COMMENT ON COLUMN "news_source"."source_type" IS 'API / RSS / CRAWLER';

COMMENT ON COLUMN "news_source"."base_url" IS '수집 대상 기본 URL';

COMMENT ON COLUMN "news_source"."is_active" IS '사용 여부';

COMMENT ON COLUMN "news_source"."created_at" IS '생성 시각';


CREATE TABLE "batch_progress" (
                                  "progress_id"	UUID		NOT NULL,
                                  "symbol"	VARCHAR		NOT NULL,
                                  "batch_type"	VARCHAR(20)		NOT NULL,
                                  "status"	VARCHAR(20)		NOT NULL,
                                  "last_collected_date"	DATE		NOT NULL,
                                  "retry_count"	INT		NOT NULL,
                                  "error_message"	TEXT		NOT NULL,
                                  "updated_at"	TIMESTAMPTZ		NOT NULL
);

COMMENT ON COLUMN "batch_progress"."status" IS 'pending / in_progress / done /failed';


CREATE TABLE "news_collect_batch" (
                                      "news_collect_batch_id"	BIGSERIAL		NOT NULL,
                                      "news_source_id"	BIGSERIAL		NOT NULL,
                                      "started_at"	TIMESTAMP		NOT NULL,
                                      "finished_at"	TIMESTAMP		NULL,
                                      "status"	VARCHAR(30)		NOT NULL,
                                      "requested_category"	VARCHAR(50)		NULL,
                                      "collected_count"	INT		NOT NULL,
                                      "failed_count"	INT		NOT NULL,
                                      "error_message"	TEXT		NULL
);

COMMENT ON COLUMN "news_collect_batch"."news_collect_batch_id" IS 'news_collect_batch 식별자';

COMMENT ON COLUMN "news_collect_batch"."news_source_id" IS 'news_source.news_source_id 참조';

COMMENT ON COLUMN "news_collect_batch"."started_at" IS '수집 시작 시각';

COMMENT ON COLUMN "news_collect_batch"."finished_at" IS '수집 종료 시각';

COMMENT ON COLUMN "news_collect_batch"."status" IS 'READY / RUNNING / SUCCESS / FAILED';

COMMENT ON COLUMN "news_collect_batch"."requested_category" IS '요청 카테고리';

COMMENT ON COLUMN "news_collect_batch"."collected_count" IS '수집 건수';

COMMENT ON COLUMN "news_collect_batch"."failed_count" IS '실패 건수';

COMMENT ON COLUMN "news_collect_batch"."error_message" IS '에러 메시지';


CREATE TABLE "news_article" (
                                "news_article_id"	BIGSERIAL		NOT NULL,
                                "raw_news_article_id"	BIGINT		NULL,
                                "publisher_name"	VARCHAR(100)		NOT NULL,
                                "title"	TEXT		NOT NULL,
                                "summary"	TEXT		NULL,
                                "content"	TEXT		NULL,
                                "original_url"	TEXT		NOT NULL,
                                "thumbnail_url"	TEXT		NULL,
                                "published_at"	TIMESTAMP		NULL,
                                "category"	VARCHAR(50)		NULL,
                                "market_scope"	VARCHAR(30)		NULL,
                                "language_code"	VARCHAR(10)		NULL,
                                "dedup_key"	VARCHAR(255)		NULL,
                                "collected_at"	TIMESTAMP		NULL,
                                "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "news_article"."news_article_id" IS 'news_article 식별자';

COMMENT ON COLUMN "news_article"."raw_news_article_id" IS 'raw_news_article.raw_news_article_id 참조';

COMMENT ON COLUMN "news_article"."publisher_name" IS '언론사명';

COMMENT ON COLUMN "news_article"."title" IS '정제 제목';

COMMENT ON COLUMN "news_article"."summary" IS '기사 단문 요약';

COMMENT ON COLUMN "news_article"."content" IS '정제 본문';

COMMENT ON COLUMN "news_article"."original_url" IS '원문 URL';

COMMENT ON COLUMN "news_article"."thumbnail_url" IS '서비스용 대표 썸네일 URL';

COMMENT ON COLUMN "news_article"."published_at" IS '발행 시각';

COMMENT ON COLUMN "news_article"."category" IS 'ECONOMY / POLITICS / SOCIETY / IT_SCIENCE / GLOBAL';

COMMENT ON COLUMN "news_article"."market_scope" IS 'DOMESTIC / GLOBAL';

COMMENT ON COLUMN "news_article"."language_code" IS 'KO / EN';

COMMENT ON COLUMN "news_article"."dedup_key" IS '중복 제거 키';

COMMENT ON COLUMN "news_article"."collected_at" IS '수집 시각';

COMMENT ON COLUMN "news_article"."created_at" IS '생성 시각';


CREATE TABLE "market_summary" (
                                  "market_summary_id"	BIGSERIAL		NOT NULL,
                                  "title"	VARCHAR(100)		NOT NULL,
                                  "summary"	TEXT		NOT NULL,
                                  "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "market_summary"."market_summary_id" IS 'market_summary 식별자';

COMMENT ON COLUMN "market_summary"."title" IS '시장 개요 제목';

COMMENT ON COLUMN "market_summary"."summary" IS '시장 개요 본문';

COMMENT ON COLUMN "market_summary"."created_at" IS '생성 시각';


CREATE TABLE "news_cluster_interest_mapping" (
                                                 "mapping_id"	BIGSERIAL		NOT NULL,
                                                 "news_cluster_id"	BIGSERIAL		NOT NULL,
                                                 "interest_type"	VARCHAR(30)		NOT NULL,
                                                 "interest_code"	VARCHAR(100)		NOT NULL,
                                                 "prompt_version"	VARCHAR(100)		NOT NULL,
                                                 "created_at"	TIMESTAMP		NOT NULL,
                                                 "user_id"	UUID		NOT NULL
);

COMMENT ON COLUMN "news_cluster_interest_mapping"."mapping_id" IS 'news_cluster_summary 식별자';

COMMENT ON COLUMN "news_cluster_interest_mapping"."news_cluster_id" IS 'news_cluster.news_cluster_id 참조';

COMMENT ON COLUMN "news_cluster_interest_mapping"."interest_type" IS 'STOCK, SECTOR, TOPIC';

COMMENT ON COLUMN "news_cluster_interest_mapping"."interest_code" IS 'NVDA, FINANCIAL, AI_SEMICONDUCTOR';

COMMENT ON COLUMN "news_cluster_interest_mapping"."prompt_version" IS '엔비디아, 금융, AI 반도체';

COMMENT ON COLUMN "news_cluster_interest_mapping"."created_at" IS '생성 시각';

COMMENT ON COLUMN "news_cluster_interest_mapping"."user_id" IS '회원 PK';


CREATE TABLE "group_member" (
                                "group_member_id"	BIGSERIAL		NOT NULL,
                                "user_id"	UUID		NOT NULL,
                                "group_id"	BIGINT		NOT NULL,
                                "role"	VARCHAR(20)	DEFAULT 'MEMBER'	NOT NULL,
                                "status"	VARCHAR(20)	DEFAULT 'ACTIVE'	NOT NULL,
                                "joined_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL,
                                "left_at"	TIMESTAMPTZ		NULL
);

COMMENT ON COLUMN "group_member"."user_id" IS '회원 PK';

COMMENT ON COLUMN "group_member"."group_id" IS '그룹 아이디 복합 pk';

COMMENT ON COLUMN "group_member"."role" IS 'LEADER / MEMBER';

COMMENT ON COLUMN "group_member"."status" IS 'ACTIVE/LEFT';


CREATE TABLE "ai_chat_message" (
                                   "message_id"	BIGSERIAL		NOT NULL,
                                   "user_id"	UUID		NOT NULL,
                                   "role"	VARCHAR(10)		NOT NULL,
                                   "content"	TEXT		NOT NULL,
                                   "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "ai_chat_message"."user_id" IS '회원 PK';

COMMENT ON COLUMN "ai_chat_message"."role" IS 'USER/AI';


CREATE TABLE "news_cluster" (
                                "news_cluster_id"	BIGSERIAL		NOT NULL,
                                "cluster_type"	VARCHAR(30)		NOT NULL,
                                "category"	VARCHAR(50)		NULL,
                                "topic"	VARCHAR(100)		NULL,
                                "topic_label"	VARCHAR(100)		NULL,
                                "representative_article_id"	BIGINT		NULL,
                                "title"	TEXT		NOT NULL,
                                "summary"	TEXT		NULL,
                                "thumbnail_url"	TEXT		NULL,
                                "published_at"	TIMESTAMP		NULL,
                                "created_at"	TIMESTAMP		NOT NULL,
                                "updated_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "news_cluster"."news_cluster_id" IS 'news_cluster 식별자';

COMMENT ON COLUMN "news_cluster"."cluster_type" IS 'TOPIC / STOCK / GENERAL';

COMMENT ON COLUMN "news_cluster"."category" IS '뉴스 카테고리';

COMMENT ON COLUMN "news_cluster"."topic" IS '예: AI_SEMICONDUCTOR';

COMMENT ON COLUMN "news_cluster"."topic_label" IS '예: AI 반도체';

COMMENT ON COLUMN "news_cluster"."representative_article_id" IS 'news_article.news_article_id 참조';

COMMENT ON COLUMN "news_cluster"."title" IS '대표 제목';

COMMENT ON COLUMN "news_cluster"."summary" IS '목록용 대표 요약';

COMMENT ON COLUMN "news_cluster"."thumbnail_url" IS '대표 썸네일 URL';

COMMENT ON COLUMN "news_cluster"."published_at" IS '대표 기사 발행 시각';

COMMENT ON COLUMN "news_cluster"."created_at" IS '생성 시각';

COMMENT ON COLUMN "news_cluster"."updated_at" IS '수정 시각';


CREATE TABLE "payment" (
                           "payment_id"	BIGSERIAL		NOT NULL,
                           "plan_id"	BIGINT		NOT NULL,
                           "user_id"	UUID		NOT NULL,
                           "order_id"	VARCHAR(100)		NOT NULL,
                           "provider"	VARCHAR(50)		NOT NULL,
                           "provider_payment_key"	VARCHAR(200)		NULL,
                           "amount"	NUMERIC(12,2)		NOT NULL,
                           "status"	VARCHAR(20)		NOT NULL,
                           "requested_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                           "approved_at"	TIMESTAMPTZ		NULL,
                           "failed_at"	TIMESTAMPTZ		NULL,
                           "failure_reason"	VARCHAR(200)		NULL,
                           "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                           "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "payment"."user_id" IS '회원 PK';

COMMENT ON COLUMN "payment"."status" IS 'READY / PAID / FAILED / CANCELED';


CREATE TABLE "user_insight_card" (
                                     "user_insight_card_id"	BIGSERIAL		NOT NULL,
                                     "market_insight_card_id"	BIGSERIAL		NOT NULL,
                                     "user_id"	UUID		NOT NULL,
                                     "tip_content"	TEXT		NULL,
                                     "recommendation_reason"	VARCHAR(255)		NOT NULL,
                                     "relevance_score"	DECIMAL(5,2)		NULL,
                                     "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "user_insight_card"."user_insight_card_id" IS 'market_insight_card 식별자';

COMMENT ON COLUMN "user_insight_card"."market_insight_card_id" IS 'market_insight_card 식별자';

COMMENT ON COLUMN "user_insight_card"."user_id" IS '회원 PK';

COMMENT ON COLUMN "user_insight_card"."tip_content" IS '회원 맞춤 제안 내용';

COMMENT ON COLUMN "user_insight_card"."recommendation_reason" IS '추천 이유';

COMMENT ON COLUMN "user_insight_card"."relevance_score" IS '개인화 점수';

COMMENT ON COLUMN "user_insight_card"."created_at" IS '생성 시각';


CREATE TABLE "game_portfolio_snapshot" (
                                           "snapshot_id"	UUID		NOT NULL,
                                           "turn_id"	UUID		NOT NULL,
                                           "participant_id"	UUID		NOT NULL,
                                           "total_asset"	BIGINT		NOT NULL,
                                           "cash"	BIGINT		NOT NULL,
                                           "stock_value"	BIGINT		NOT NULL,
                                           "profit_rate"	DECIMAL		NOT NULL
);


CREATE TABLE "bet" (
                       "game_id"	BIGSERIAL		NOT NULL,
                       "group_id"	BIGINT		NOT NULL,
                       "created_by"	UUID		NOT NULL,
                       "status"	VARCHAR(20)		NOT NULL,
                       "bet_amount"	INTEGER	DEFAULT 0	NOT NULL,
                       "started_at"	TIMESTAMPTZ		NULL,
                       "ended_at"	TIMESTAMPTZ		NULL,
                       "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                       "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "bet"."group_id" IS '그룹 아이디 복합 pk';

COMMENT ON COLUMN "bet"."status" IS 'RECRUITING / IN_PROGRESS / FINISHED / CANCLED';


CREATE TABLE "chat_message" (
                                "message_id"	BIGSERIAL		NOT NULL,
                                "group_id"	BIGINT		NOT NULL,
                                "user_id"	UUID		NULL,
                                "message_type"	VARCHAR(20)		NOT NULL,
                                "content"	TEXT		NOT NULL,
                                "ref_type"	VARCHAR(20)		NULL,
                                "ref_id"	BIGINT		NULL,
                                "is_deleted"	BOOLEAN	DEFAULT FALSE	NOT NULL,
                                "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                                "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "chat_message"."group_id" IS '그룹 아이디 복합 pk';

COMMENT ON COLUMN "chat_message"."user_id" IS '회원 PK';

COMMENT ON COLUMN "chat_message"."message_type" IS 'CHAT/MEMO/SYSTEM';

COMMENT ON COLUMN "chat_message"."ref_type" IS 'VOTE / BET / TRANSFER';

COMMENT ON COLUMN "chat_message"."ref_id" IS 'vote_id / game_id / transfer_id';


CREATE TABLE "news_cluster_followup_question" (
                                                  "news_cluster_followup_question_id"	BIGSERIAL		NOT NULL,
                                                  "news_cluster_id"	BIGINT		NOT NULL,
                                                  "question_text"	TEXT		NOT NULL,
                                                  "question_order"	INT		NOT NULL,
                                                  "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "news_cluster_followup_question"."news_cluster_followup_question_id" IS 'news_cluster_followup_question 식별자';

COMMENT ON COLUMN "news_cluster_followup_question"."news_cluster_id" IS 'news_cluster.news_cluster_id 참조';

COMMENT ON COLUMN "news_cluster_followup_question"."question_text" IS '후속 질문 문구';

COMMENT ON COLUMN "news_cluster_followup_question"."question_order" IS '질문 정렬 순서';

COMMENT ON COLUMN "news_cluster_followup_question"."created_at" IS '생성 시각';


CREATE TABLE "personal_market_trend_source" (
                                                "personal_market_trend_source_id"	BIGSERIAL		NOT NULL,
                                                "personal_market_trend_id"	BIGINT		NOT NULL,
                                                "news_article_id"	BIGINT		NOT NULL,
                                                "article_embedding_id"	BIGINT		NULL,
                                                "source_order"	INT		NOT NULL
);

COMMENT ON COLUMN "personal_market_trend_source"."personal_market_trend_source_id" IS 'personal_market_trend_source 식별자';

COMMENT ON COLUMN "personal_market_trend_source"."personal_market_trend_id" IS 'personal_market_trend.personal_market_trend_id 참조';

COMMENT ON COLUMN "personal_market_trend_source"."news_article_id" IS 'news_article.news_article_id 참조';

COMMENT ON COLUMN "personal_market_trend_source"."article_embedding_id" IS 'article_embedding.article_embedding_id 참조';

COMMENT ON COLUMN "personal_market_trend_source"."source_order" IS '출처 노출 순서';


CREATE TABLE "global_chat_message" (
                                       "message_id"	BIGSERIAL		NOT NULL,
                                       "user_id"	UUID		NULL,
                                       "role"	VARCHAR(10)		NOT NULL,
                                       "content"	TEXT		NOT NULL,
                                       "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "global_chat_message"."user_id" IS '회원 PK';

COMMENT ON COLUMN "global_chat_message"."role" IS 'USER/SYSTEM';


CREATE TABLE "stock" (
                         "stock_id"	BIGSERIAL 	NOT NULL,
                         "stock_code"	VARCHAR(255)		NOT NULL,
                         "stock_name"	VARCHAR(100)		NOT NULL,
                         "market"	VARCHAR(255)		NOT NULL,
                         "sector"	VARCHAR(255)		NOT NULL
);

COMMENT ON COLUMN "stock"."stock_id" IS 'PK';

COMMENT ON COLUMN "stock"."stock_code" IS '"005830",  "APPL"';

COMMENT ON COLUMN "stock"."stock_name" IS '삼성전자, Apple';

COMMENT ON COLUMN "stock"."market" IS 'KR / US';

COMMENT ON COLUMN "stock"."sector" IS '전기, 반도체, 방산';


CREATE TABLE "personalized_briefing" (
                                         "personalized_briefing_id"	BIGSERIAL		NOT NULL,
                                         "user_id"	UUID		NOT NULL,
                                         "briefing_kind"	VARCHAR(30)		NOT NULL,
                                         "base_news_cluster_id"	BIGINT		NULL,
                                         "session"	VARCHAR(20)		NULL,
                                         "briefing_date"	DATE		NULL,
                                         "title"	VARCHAR(200)		NOT NULL,
                                         "subtitle"	VARCHAR(200)		NULL,
                                         "content"	TEXT		NOT NULL,
                                         "insight_message"	TEXT		NULL,
                                         "interest_type"	VARCHAR(30)		NULL,
                                         "interest_code"	VARCHAR(100)		NULL,
                                         "interest_name"	VARCHAR(100)		NULL,
                                         "related_keywords"	JSONB		NULL,
                                         "representative_news_cluster_id"	BIGINT		NULL,
                                         "tone"	VARCHAR(30)		NULL,
                                         "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "personalized_briefing"."personalized_briefing_id" IS 'personalized_briefing 식별자';

COMMENT ON COLUMN "personalized_briefing"."user_id" IS '사용자 식별자';

COMMENT ON COLUMN "personalized_briefing"."briefing_kind" IS 'DAILY / NEWS_DETAIL';

COMMENT ON COLUMN "personalized_briefing"."base_news_cluster_id" IS 'news_cluster.news_cluster_id 참조';

COMMENT ON COLUMN "personalized_briefing"."session" IS 'MORNING / EVENING';

COMMENT ON COLUMN "personalized_briefing"."briefing_date" IS '브리핑 기준 일자';

COMMENT ON COLUMN "personalized_briefing"."title" IS '브리핑 제목';

COMMENT ON COLUMN "personalized_briefing"."subtitle" IS '브리핑 부제';

COMMENT ON COLUMN "personalized_briefing"."content" IS '개인화 브리핑 본문';

COMMENT ON COLUMN "personalized_briefing"."insight_message" IS '개인화 설명 문구';

COMMENT ON COLUMN "personalized_briefing"."interest_type" IS 'STOCK / SECTOR / TOPIC';

COMMENT ON COLUMN "personalized_briefing"."interest_code" IS '관심 코드';

COMMENT ON COLUMN "personalized_briefing"."interest_name" IS '관심명';

COMMENT ON COLUMN "personalized_briefing"."related_keywords" IS '관련 키워드 배열';

COMMENT ON COLUMN "personalized_briefing"."representative_news_cluster_id" IS 'news_cluster.news_cluster_id 참조';

COMMENT ON COLUMN "personalized_briefing"."tone" IS 'DEFAULT / CONCISE / DETAILED';

COMMENT ON COLUMN "personalized_briefing"."created_at" IS '생성 시각';


CREATE TABLE "raw_news_article" (
                                    "raw_news_article_id"	BIGSERIAL		NOT NULL,
                                    "news_source_id"	BIGSERIAL		NOT NULL,
                                    "news_collect_batch_id"	BIGSERIAL		NOT NULL,
                                    "external_article_id"	VARCHAR(255)		NULL,
                                    "original_url"	TEXT		NOT NULL,
                                    "original_title"	TEXT		NOT NULL,
                                    "original_content"	TEXT		NULL,
                                    "original_thumbnail_url"	TEXT		NULL,
                                    "original_published_at"	TIMESTAMP		NULL,
                                    "raw_payload"	JSONB		NULL,
                                    "processing_status"	VARCHAR(30)		NOT NULL,
                                    "collected_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "raw_news_article"."raw_news_article_id" IS 'raw_news_article 식별자';

COMMENT ON COLUMN "raw_news_article"."news_source_id" IS 'news_source.news_source_id 참조';

COMMENT ON COLUMN "raw_news_article"."news_collect_batch_id" IS 'news_collect_batch.news_collect_batch_id 참조';

COMMENT ON COLUMN "raw_news_article"."external_article_id" IS '외부 기사 식별자';

COMMENT ON COLUMN "raw_news_article"."original_url" IS '원문 링크';

COMMENT ON COLUMN "raw_news_article"."original_title" IS '원문 제목';

COMMENT ON COLUMN "raw_news_article"."original_content" IS '원문 본문';

COMMENT ON COLUMN "raw_news_article"."original_thumbnail_url" IS '원문 썸네일 URL';

COMMENT ON COLUMN "raw_news_article"."original_published_at" IS '원문 발행 시각';

COMMENT ON COLUMN "raw_news_article"."raw_payload" IS '외부 응답 원본';

COMMENT ON COLUMN "raw_news_article"."processing_status" IS 'PENDING / NORMALIZED / FAILED / DUPLICATED';

COMMENT ON COLUMN "raw_news_article"."collected_at" IS '수집 시각';


CREATE TABLE "virtual_account" (
                                   "virtual_account_id"	BIGSERIAL		NOT NULL,
                                   "user_id"	UUID		NOT NULL,
                                   "balance"	NUMERIC(15, 2)		NOT NULL,
                                   "initial_balance"	NUMERIC(15, 2)		NOT NULL,
                                   "total_realized_profit"	NUMERIC(15, 2)	DEFAULT 0.0	NULL,
                                   "updated_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL
);

COMMENT ON COLUMN "virtual_account"."user_id" IS '회원 PK';

COMMENT ON COLUMN "virtual_account"."balance" IS '현재 예수금 (원화)';

COMMENT ON COLUMN "virtual_account"."initial_balance" IS '수익률 계산 기준';

COMMENT ON COLUMN "virtual_account"."total_realized_profit" IS '매도 확정 손익 합계';

COMMENT ON COLUMN "virtual_account"."updated_at" IS '최종 수정 시간';


CREATE TABLE "transfer" (
                            "transfer_id"	BIGSERIAL		NOT NULL,
                            "group_id"	BIGINT		NOT NULL,
                            "sender_id"	UUID		NOT NULL,
                            "receiver_id"	UUID		NOT NULL,
                            "game_id"	BIGINT		NULL,
                            "transfer_type"	VARCHAR(20)	DEFAULT 'NORMAL'	NOT NULL,
                            "amount"	INTEGER		NOT NULL,
                            "status"	VARCHAR(20)		NOT NULL,
                            "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                            "completed_at"	TIMESTAMPTZ		NULL
);

COMMENT ON COLUMN "transfer"."group_id" IS '그룹 아이디 복합 pk';

COMMENT ON COLUMN "transfer"."transfer_type" IS 'GAME_REWARD/NORMAL';

COMMENT ON COLUMN "transfer"."status" IS 'REQUESTED / COMPLETED / CANCELED';


CREATE TABLE "vote" (
                        "vote_id"	BIGSERIAL		NOT NULL,
                        "group_id"	BIGINT		NOT NULL,
                        "created_by"	UUID		NOT NULL,
                        "title"	VARCHAR(200)		NOT NULL,
                        "status"	VARCHAR(20)	DEFAULT 'OPEN'	NOT NULL,
                        "ends_at"	TIMESTAMPTZ		NULL,
                        "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                        "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "vote"."group_id" IS '그룹 아이디 복합 pk';

COMMENT ON COLUMN "vote"."status" IS 'OPEN / CLOSED';


CREATE TABLE "subscription" (
                                "subscription_id"	BIGSERIAL		NOT NULL,
                                "plan_id"	BIGINT		NOT NULL,
                                "user_id"	UUID		NOT NULL,
                                "status"	VARCHAR(20)		NOT NULL,
                                "started_at"	TIMESTAMPTZ		NOT NULL,
                                "expired_at"	TIMESTAMPTZ		NULL,
                                "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                                "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "subscription"."user_id" IS '회원 PK';

COMMENT ON COLUMN "subscription"."status" IS 'ACTIVE / CANCELED / EXPIRED';

COMMENT ON COLUMN "subscription"."started_at" IS '구독 시작 시각';

COMMENT ON COLUMN "subscription"."expired_at" IS '구독 만료 시각';


CREATE TABLE "personal_market_trend" (
                                         "personal_market_trend_id"	BIGSERIAL		NOT NULL,
                                         "user_id"	UUID		NOT NULL,
                                         "trend_date"	DATE		NOT NULL,
                                         "session"	VARCHAR(20)		NOT NULL,
                                         "title"	VARCHAR(200)		NOT NULL,
                                         "summary"	TEXT		NOT NULL,
                                         "related_keywords"	JSONB		NULL,
                                         "refreshable"	BOOLEAN		NOT NULL,
                                         "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "personal_market_trend"."personal_market_trend_id" IS 'personal_market_trend 식별자';

COMMENT ON COLUMN "personal_market_trend"."user_id" IS '회원 PK';

COMMENT ON COLUMN "personal_market_trend"."trend_date" IS '기준 일자';

COMMENT ON COLUMN "personal_market_trend"."session" IS 'MORNING / EVENING';

COMMENT ON COLUMN "personal_market_trend"."title" IS '개인 맞춤 동향 제목';

COMMENT ON COLUMN "personal_market_trend"."summary" IS '개인 맞춤 동향 본문';

COMMENT ON COLUMN "personal_market_trend"."related_keywords" IS '관련 키워드 배열';

COMMENT ON COLUMN "personal_market_trend"."refreshable" IS '새로고침 가능 여부';

COMMENT ON COLUMN "personal_market_trend"."created_at" IS '생성 시각';


CREATE TABLE "betting_participant" (
                                       "participant_id"	BIGSERIAL		NOT NULL,
                                       "game_id"	BIGINT		NOT NULL,
                                       "user_id"	UUID		NOT NULL,
                                       "status"	VARCHAR(20)		NOT NULL,
                                       "joined_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                                       "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "betting_participant"."status" IS 'JOINED / WIN / LOSE';

COMMENT ON COLUMN "betting_participant"."updated_at" IS '로그용';


CREATE TABLE "game_analysis_report" (
                                        "report_id"	UUID		NOT NULL,
                                        "daily_id"	UUID		NOT NULL,
                                        "participant_id"	UUID		NOT NULL,
                                        "report_text"	TEXT		NOT NULL,
                                        "created_at"	TIMESTAMPTZ		NOT NULL
);


CREATE TABLE "personalized_briefing_source" (
                                                "personalized_briefing_source_id"	BIGSERIAL		NOT NULL,
                                                "personalized_briefing_id"	BIGSERIAL		NOT NULL,
                                                "news_article_id"	BIGSERIAL		NOT NULL,
                                                "article_embedding_id"	BIGINT		NULL,
                                                "source_order"	INT		NOT NULL
);

COMMENT ON COLUMN "personalized_briefing_source"."personalized_briefing_source_id" IS 'personalized_briefing_source 식별자';

COMMENT ON COLUMN "personalized_briefing_source"."personalized_briefing_id" IS 'personalized_briefing.personalized_briefing_id 참조';

COMMENT ON COLUMN "personalized_briefing_source"."news_article_id" IS 'news_article.news_article_id 참조';

COMMENT ON COLUMN "personalized_briefing_source"."article_embedding_id" IS 'article_embedding.article_embedding_id 참조';

COMMENT ON COLUMN "personalized_briefing_source"."source_order" IS '출처 노출 순서';


CREATE TABLE "game_turn" (
                             "turn_id"	UUID		NOT NULL,
                             "room_id"	UUID		NOT NULL,
                             "briefing_id"	UUID		NOT NULL,
                             "turn_number"	INT		NOT NULL,
                             "turn_date"	DATE		NOT NULL,
                             "status"	VARCHAR		NOT NULL
);

COMMENT ON COLUMN "game_turn"."status" IS 'Active / completed';


CREATE TABLE "user_news_cluster_read" (
                                          "user_news_cluster_read_id"	BIGSERIAL		NOT NULL,
                                          "user_id"	UUID		NOT NULL,
                                          "news_cluster_id"	BIGINT		NOT NULL,
                                          "read_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "user_news_cluster_read"."user_news_cluster_read_id" IS 'user_news_cluster_read 식별자';

COMMENT ON COLUMN "user_news_cluster_read"."user_id" IS '회원 PK';

COMMENT ON COLUMN "user_news_cluster_read"."news_cluster_id" IS 'news_cluster.news_cluster_id 참조';

COMMENT ON COLUMN "user_news_cluster_read"."read_at" IS '읽음 처리 시각';


CREATE TABLE "market_snapshot" (
                                   "market_snapshot_id"	BIGSERIAL		NOT NULL,
                                   "market_trend_id"	BIGSERIAL		NOT NULL,
                                   "metric_type"	VARCHAR(30)		NOT NULL,
                                   "label"	VARCHAR(100)		NOT NULL,
                                   "value"	NUMERIC(18,4)		NOT NULL,
                                   "change_rate"	NUMERIC(10,4)		NULL,
                                   "change_value"	NUMERIC(18,4)		NULL,
                                   "unit"	VARCHAR(20)		NOT NULL,
                                   "change_direction"	VARCHAR(20)		NOT NULL,
                                   "display_order"	INT		NOT NULL,
                                   "created_at"	TIMESTAMPTZ		NOT NULL
);

COMMENT ON COLUMN "market_snapshot"."market_snapshot_id" IS 'market_snapshot_id';

COMMENT ON COLUMN "market_snapshot"."metric_type" IS 'KOSPI / NASDAQ / BASE_RATE / USD_KRW';

COMMENT ON COLUMN "market_snapshot"."label" IS '화면 표시용 이름';

COMMENT ON COLUMN "market_snapshot"."value" IS '현재 값';

COMMENT ON COLUMN "market_snapshot"."unit" IS 'POINT / PERCENT / KRW';

COMMENT ON COLUMN "market_snapshot"."change_direction" IS 'UP / DOWN / FLAT';

COMMENT ON COLUMN "market_snapshot"."display_order" IS '노출 순서';


CREATE TABLE "quest_template" (
                                  "template_id"	BIGSERIAL		NOT NULL,
                                  "title"	VARCHAR(100)		NOT NULL,
                                  "description"	TEXT		NOT NULL,
                                  "complete_type"	VARCHAR(30)		NOT NULL,
                                  "event_type"	VARCHAR(30)		NOT NULL,
                                  "target_value"	INTEGER		NULL,
                                  "condition_json"	JSONB		NULL,
                                  "reward"	INTEGER		NOT NULL,
                                  "is_repeatable"	BOOLEAN	DEFAULT FALSE	NOT NULL,
                                  "is_active"	BOOLEAN	DEFAULT TRUE	NOT NULL,
                                  "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                                  "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "quest_template"."complete_type" IS 'COUNT / PERCENT ex) 뉴스 n개 읽기';

COMMENT ON COLUMN "quest_template"."event_type" IS 'ADD/EXPENSE / BUY_STOCK/LOGIN';

COMMENT ON COLUMN "quest_template"."condition_json" IS '{   "compareType": "PREVIOUS_WEEK",   "targetRate": 10 }';

COMMENT ON COLUMN "quest_template"."is_repeatable" IS '첫 소비 등록: false';


CREATE TABLE "news_cluster_summary_source" (
                                               "news_cluster_summary_source_id"	BIGSERIAL		NOT NULL,
                                               "news_cluster_summary_id"	BIGSERIAL		NOT NULL,
                                               "news_article_id"	BIGSERIAL		NOT NULL,
                                               "source_order"	INT		NOT NULL
);

COMMENT ON COLUMN "news_cluster_summary_source"."news_cluster_summary_source_id" IS 'news_cluster_summary_source 식별자';

COMMENT ON COLUMN "news_cluster_summary_source"."news_cluster_summary_id" IS 'news_cluster_summary.news_cluster_summary_id 참조';

COMMENT ON COLUMN "news_cluster_summary_source"."news_article_id" IS 'news_article.news_article_id 참조';

COMMENT ON COLUMN "news_cluster_summary_source"."source_order" IS '출처 노출 순서';


CREATE TABLE "orders" (
                          "order_id"	BIGSERIAL	NOT NULL,
                          "order_no"	UUID	DEFAULT gen_random_uuid()	NOT NULL,
                          "virtual_account_id"	BIGINT		NOT NULL,
                          "stock_id"	BIGINT		NOT NULL,
                          "order_type"	VARCHAR(10)		NOT NULL,
                          "side"	VARCHAR(4)		NOT NULL,
                          "quantity"	INTEGER		NOT NULL,
                          "filled_quantity"	INTEGER	DEFAULT 0	NOT NULL,
                          "request_price"	NUMERIC(15, 2)		NULL,
                          "status"	VARCHAR(20)	DEFAULT 'PENDING'	NOT NULL,
                          "currency"	VARCHAR(3)	DEFAULT 'KRW'	NOT NULL,
                          "exchange_rate"	NUMERIC(10, 4)		NULL,
                          "fee"	NUMERIC(15, 2)		NOT NULL,
                          "tax"	NUMERIC(15, 2)		NOT NULL,
                          "created_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL,
                          "updated_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL,
                          "cancelled_at"	TIMESTAMPTZ		NULL
);

COMMENT ON COLUMN "orders"."order_id" IS 'PK';

COMMENT ON COLUMN "orders"."order_no" IS '외부 노출용';

COMMENT ON COLUMN "orders"."virtual_account_id" IS 'virtual_account 참조';

COMMENT ON COLUMN "orders"."stock_id" IS 'stock 참조';

COMMENT ON COLUMN "orders"."order_type" IS 'MARKET / LIMIT';

COMMENT ON COLUMN "orders"."side" IS 'BUY / SELL';

COMMENT ON COLUMN "orders"."quantity" IS '주문한 총 수량';

COMMENT ON COLUMN "orders"."filled_quantity" IS '체결된 수량';

COMMENT ON COLUMN "orders"."request_price" IS '지정가 가격 (시장가면 NULL)';

COMMENT ON COLUMN "orders"."status" IS 'PENDING / FILLED / PARTIAL / CANCELLED';

COMMENT ON COLUMN "orders"."currency" IS 'KRW / USD';

COMMENT ON COLUMN "orders"."exchange_rate" IS '주문 시점 환율 (해외만)';

COMMENT ON COLUMN "orders"."fee" IS '매수/매도 수수료';

COMMENT ON COLUMN "orders"."tax" IS '증권 거래세';

COMMENT ON COLUMN "orders"."created_at" IS '주문 생성 시간';

COMMENT ON COLUMN "orders"."updated_at" IS '최종 수정 시간';

COMMENT ON COLUMN "orders"."cancelled_at" IS '취소된 경우에만';


CREATE TABLE "news_cluster_article" (
                                        "news_cluster_article_id"	BIGSERIAL		NOT NULL,
                                        "news_cluster_id"	BIGINT		NOT NULL,
                                        "news_article_id"	BIGINT		NOT NULL,
                                        "is_representative"	BOOLEAN		NOT NULL,
                                        "article_order"	INT		NOT NULL,
                                        "created_at"	TIMESTAMP		NOT NULL
);

COMMENT ON COLUMN "news_cluster_article"."news_cluster_article_id" IS 'news_cluster_article 식별자';

COMMENT ON COLUMN "news_cluster_article"."news_cluster_id" IS 'news_cluster.news_cluster_id 참조';

COMMENT ON COLUMN "news_cluster_article"."news_article_id" IS 'news_article.news_article_id 참조';

COMMENT ON COLUMN "news_cluster_article"."is_representative" IS '대표 기사 여부';

COMMENT ON COLUMN "news_cluster_article"."article_order" IS '정렬 순서';

COMMENT ON COLUMN "news_cluster_article"."created_at" IS '생성 시각';


CREATE TABLE "portfolio" (
                             "portfolio_id"	BIGSERIAL		NOT NULL,
                             "virtual_account_id"	BIGINT		NOT NULL,
                             "stock_id"	BIGINT		NOT NULL,
                             "quantity"	INTEGER		NOT NULL,
                             "avg_price"	NUMERIC(15, 2)		NOT NULL,
                             "total_buy_amount"	NUMERIC(15, 2)		NOT NULL,
                             "currency"	VARCHAR(3)	DEFAULT 'KRW'	NOT NULL,
                             "created_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL,
                             "updated_at"	TIMESTAMPTZ	DEFAULT NOW()	NOT NULL
);

COMMENT ON COLUMN "portfolio"."quantity" IS '현재 보유 주식 수';

COMMENT ON COLUMN "portfolio"."avg_price" IS '가중평균 매수 단가';

COMMENT ON COLUMN "portfolio"."total_buy_amount" IS '평균 단가 재계산용';

COMMENT ON COLUMN "portfolio"."currency" IS 'KRW / USD';

COMMENT ON COLUMN "portfolio"."created_at" IS '최초 매수 시간';

COMMENT ON COLUMN "portfolio"."updated_at" IS '최종 매매 시간';


CREATE TABLE "market_summary_source" (
                                         "market_summary_source_id"	BIGSERIAL		NOT NULL,
                                         "market_summary_id"	BIGSERIAL		NOT NULL,
                                         "news_article_id"	BIGSERIAL		NOT NULL,
                                         "article_embedding_id"	BIGINT		NULL,
                                         "source_order"	INT		NOT NULL
);

COMMENT ON COLUMN "market_summary_source"."market_summary_source_id" IS 'market_summary_source 식별자';

COMMENT ON COLUMN "market_summary_source"."market_summary_id" IS 'market_summary.market_summary_id 참조';

COMMENT ON COLUMN "market_summary_source"."news_article_id" IS 'news_article.news_article_id 참조';

COMMENT ON COLUMN "market_summary_source"."article_embedding_id" IS 'article_embedding.article_embedding_id 참조';

COMMENT ON COLUMN "market_summary_source"."source_order" IS '출처 노출 순서';


CREATE TABLE "user_quest" (
                              "quest_id"	BIGSERIAL		NOT NULL,
                              "template_id"	BIGINT		NOT NULL,
                              "user_id"	UUID		NOT NULL,
                              "status"	VARCHAR(20)		NOT NULL,
                              "progress"	INTEGER		NOT NULL,
                              "target_value"	INTEGER		NULL,
                              "reward"	INTEGER		NOT NULL,
                              "assigned_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                              "started_at"	TIMESTAMPTZ		NULL,
                              "completed_at"	TIMESTAMPTZ		NULL,
                              "expired_at"	TIMESTAMPTZ		NULL,
                              "created_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL,
                              "updated_at"	TIMESTAMPTZ	DEFAULT CURRENT_TIMESTAMP	NOT NULL
);

COMMENT ON COLUMN "user_quest"."user_id" IS '회원 PK';

COMMENT ON COLUMN "user_quest"."status" IS 'NOT_STARTED / IN_PROGRESS / COMPLETED / REWARDED';

COMMENT ON COLUMN "user_quest"."progress" IS '0 / 1 ...';

COMMENT ON COLUMN "user_quest"."target_value" IS '퀘스트 템플릿이 변경될 수 있으므로 복사본 저장';

COMMENT ON COLUMN "user_quest"."reward" IS '퀘스트 템플릿이 변경될 수 있으므로 복사본 저장';


CREATE TABLE "market_insight_source" (
                                         "market_insight_source_id"	BIGSERIAL		NOT NULL,
                                         "market_insight_card_id"	BIGINT		NOT NULL,
                                         "news_article_id"	BIGINT		NOT NULL,
                                         "article_embedding_id"	BIGINT		NULL,
                                         "source_order"	INT		NOT NULL
);

COMMENT ON COLUMN "market_insight_source"."market_insight_source_id" IS 'market_insight_source 식별자';

COMMENT ON COLUMN "market_insight_source"."market_insight_card_id" IS 'market_insight_card.market_insight_card_id 참조';

COMMENT ON COLUMN "market_insight_source"."news_article_id" IS 'news_article.news_article_id 참조';

COMMENT ON COLUMN "market_insight_source"."article_embedding_id" IS 'article_embedding.article_embedding_id 참조';

COMMENT ON COLUMN "market_insight_source"."source_order" IS '출처 노출 순서';

ALTER TABLE "stock_daily" ADD CONSTRAINT "PK_STOCK_DAILY" PRIMARY KEY (
                                                                       "daily_id"
    );

ALTER TABLE "briefing_cache" ADD CONSTRAINT "PK_BRIEFING_CACHE" PRIMARY KEY (
                                                                             "briefing_id"
    );

ALTER TABLE "game_participant" ADD CONSTRAINT "PK_GAME_PARTICIPANT" PRIMARY KEY (
                                                                                 "participant_id"
    );

ALTER TABLE "daily_snapshot" ADD CONSTRAINT "PK_DAILY_SNAPSHOT" PRIMARY KEY (
                                                                             "daily_snapshot_id"
    );

ALTER TABLE "news_article_tag" ADD CONSTRAINT "PK_NEWS_ARTICLE_TAG" PRIMARY KEY (
                                                                                 "news_article_tag_id"
    );

ALTER TABLE "stock_info" ADD CONSTRAINT "PK_STOCK_INFO" PRIMARY KEY (
                                                                     "symbol"
    );

ALTER TABLE "vote_option" ADD CONSTRAINT "PK_VOTE_OPTION" PRIMARY KEY (
                                                                       "option_id"
    );

ALTER TABLE "game_order" ADD CONSTRAINT "PK_GAME_ORDER" PRIMARY KEY (
                                                                     "order_id"
    );

ALTER TABLE "market_insight_card" ADD CONSTRAINT "PK_MARKET_INSIGHT_CARD" PRIMARY KEY (
                                                                                       "market_insight_card_id"
    );

ALTER TABLE "trade" ADD CONSTRAINT "PK_TRADE" PRIMARY KEY (
                                                           "trade_id"
    );

ALTER TABLE "subscription_plan" ADD CONSTRAINT "PK_SUBSCRIPTION_PLAN" PRIMARY KEY (
                                                                                   "plan_id"
    );

ALTER TABLE "groups" ADD CONSTRAINT "PK_GROUP" PRIMARY KEY (
                                                            "group_id"
    );

ALTER TABLE "game_news_archive" ADD CONSTRAINT "PK_GAME_NEWS_ARCHIVE" PRIMARY KEY (
                                                                                   "news_id"
    );

ALTER TABLE "users" ADD CONSTRAINT "PK_USER" PRIMARY KEY (
                                                          "user_id"
    );

ALTER TABLE "game_holding" ADD CONSTRAINT "PK_GAME_HOLDING" PRIMARY KEY (
                                                                         "holding_id"
    );

ALTER TABLE "game_room" ADD CONSTRAINT "PK_GAME_ROOM" PRIMARY KEY (
                                                                   "room_id"
    );

ALTER TABLE "article_embedding" ADD CONSTRAINT "PK_ARTICLE_EMBEDDING" PRIMARY KEY (
                                                                                   "article_embedding_id"
    );

ALTER TABLE "user_interest" ADD CONSTRAINT "PK_USER_INTEREST" PRIMARY KEY (
                                                                           "user_interest_id"
    );

ALTER TABLE "market_trend" ADD CONSTRAINT "PK_MARKET_TREND" PRIMARY KEY (
                                                                         "market_trend_id"
    );

ALTER TABLE "news_cluster_summary" ADD CONSTRAINT "PK_NEWS_CLUSTER_SUMMARY" PRIMARY KEY (
                                                                                         "news_cluster_summary_id"
    );

ALTER TABLE "user_news_cluster_feedback" ADD CONSTRAINT "PK_USER_NEWS_CLUSTER_FEEDBACK" PRIMARY KEY (
                                                                                                     "user_news_cluster_feedback_id"
    );

ALTER TABLE "vote_answer" ADD CONSTRAINT "PK_VOTE_ANSWER" PRIMARY KEY (
                                                                       "answer_id"
    );

ALTER TABLE "group_invite" ADD CONSTRAINT "PK_GROUP_INVITE" PRIMARY KEY (
                                                                         "code_id"
    );

ALTER TABLE "news_source" ADD CONSTRAINT "PK_NEWS_SOURCE" PRIMARY KEY (
                                                                       "news_source_id"
    );

ALTER TABLE "batch_progress" ADD CONSTRAINT "PK_BATCH_PROGRESS" PRIMARY KEY (
                                                                             "progress_id"
    );

ALTER TABLE "news_collect_batch" ADD CONSTRAINT "PK_NEWS_COLLECT_BATCH" PRIMARY KEY (
                                                                                     "news_collect_batch_id"
    );

ALTER TABLE "news_article" ADD CONSTRAINT "PK_NEWS_ARTICLE" PRIMARY KEY (
                                                                         "news_article_id"
    );

ALTER TABLE "market_summary" ADD CONSTRAINT "PK_MARKET_SUMMARY" PRIMARY KEY (
                                                                             "market_summary_id"
    );

ALTER TABLE "news_cluster_interest_mapping" ADD CONSTRAINT "PK_NEWS_CLUSTER_INTEREST_MAPPING" PRIMARY KEY (
                                                                                                           "mapping_id"
    );

ALTER TABLE "group_member" ADD CONSTRAINT "PK_GROUP_MEMBER" PRIMARY KEY (
                                                                         "group_member_id"
    );

ALTER TABLE "ai_chat_message" ADD CONSTRAINT "PK_AI_CHAT_MESSAGE" PRIMARY KEY (
                                                                               "message_id"
    );

ALTER TABLE "news_cluster" ADD CONSTRAINT "PK_NEWS_CLUSTER" PRIMARY KEY (
                                                                         "news_cluster_id"
    );

ALTER TABLE "payment" ADD CONSTRAINT "PK_PAYMENT" PRIMARY KEY (
                                                               "payment_id"
    );

ALTER TABLE "user_insight_card" ADD CONSTRAINT "PK_USER_INSIGHT_CARD" PRIMARY KEY (
                                                                                   "user_insight_card_id"
    );

ALTER TABLE "game_portfolio_snapshot" ADD CONSTRAINT "PK_GAME_PORTFOLIO_SNAPSHOT" PRIMARY KEY (
                                                                                               "snapshot_id"
    );

ALTER TABLE "bet" ADD CONSTRAINT "PK_BET" PRIMARY KEY (
                                                       "game_id"
    );

ALTER TABLE "chat_message" ADD CONSTRAINT "PK_CHAT_MESSAGE" PRIMARY KEY (
                                                                         "message_id"
    );

ALTER TABLE "news_cluster_followup_question" ADD CONSTRAINT "PK_NEWS_CLUSTER_FOLLOWUP_QUESTION" PRIMARY KEY (
                                                                                                             "news_cluster_followup_question_id"
    );

ALTER TABLE "personal_market_trend_source" ADD CONSTRAINT "PK_PERSONAL_MARKET_TREND_SOURCE" PRIMARY KEY (
                                                                                                         "personal_market_trend_source_id"
    );

ALTER TABLE "global_chat_message" ADD CONSTRAINT "PK_GLOBAL_CHAT_MESSAGE" PRIMARY KEY (
                                                                                       "message_id"
    );

ALTER TABLE "stock" ADD CONSTRAINT "PK_STOCK" PRIMARY KEY (
                                                           "stock_id"
    );

ALTER TABLE "personalized_briefing" ADD CONSTRAINT "PK_PERSONALIZED_BRIEFING" PRIMARY KEY (
                                                                                           "personalized_briefing_id"
    );

ALTER TABLE "raw_news_article" ADD CONSTRAINT "PK_RAW_NEWS_ARTICLE" PRIMARY KEY (
                                                                                 "raw_news_article_id"
    );

ALTER TABLE "virtual_account" ADD CONSTRAINT "PK_VIRTUAL_ACCOUNT" PRIMARY KEY (
                                                                               "virtual_account_id"
    );

ALTER TABLE "transfer" ADD CONSTRAINT "PK_TRANSFER" PRIMARY KEY (
                                                                 "transfer_id"
    );

ALTER TABLE "vote" ADD CONSTRAINT "PK_VOTE" PRIMARY KEY (
                                                         "vote_id"
    );

ALTER TABLE "subscription" ADD CONSTRAINT "PK_SUBSCRIPTION" PRIMARY KEY (
                                                                         "subscription_id"
    );

ALTER TABLE "personal_market_trend" ADD CONSTRAINT "PK_PERSONAL_MARKET_TREND" PRIMARY KEY (
                                                                                           "personal_market_trend_id"
    );

ALTER TABLE "betting_participant" ADD CONSTRAINT "PK_BETTING_PARTICIPANT" PRIMARY KEY (
                                                                                       "participant_id"
    );

ALTER TABLE "game_analysis_report" ADD CONSTRAINT "PK_GAME_ANALYSIS_REPORT" PRIMARY KEY (
                                                                                         "report_id"
    );

ALTER TABLE "personalized_briefing_source" ADD CONSTRAINT "PK_PERSONALIZED_BRIEFING_SOURCE" PRIMARY KEY (
                                                                                                         "personalized_briefing_source_id"
    );

ALTER TABLE "game_turn" ADD CONSTRAINT "PK_GAME_TURN" PRIMARY KEY (
                                                                   "turn_id"
    );

ALTER TABLE "user_news_cluster_read" ADD CONSTRAINT "PK_USER_NEWS_CLUSTER_READ" PRIMARY KEY (
                                                                                             "user_news_cluster_read_id"
    );

ALTER TABLE "market_snapshot" ADD CONSTRAINT "PK_MARKET_SNAPSHOT" PRIMARY KEY (
                                                                               "market_snapshot_id"
    );

ALTER TABLE "quest_template" ADD CONSTRAINT "PK_QUEST_TEMPLATE" PRIMARY KEY (
                                                                             "template_id"
    );

ALTER TABLE "news_cluster_summary_source" ADD CONSTRAINT "PK_NEWS_CLUSTER_SUMMARY_SOURCE" PRIMARY KEY (
                                                                                                       "news_cluster_summary_source_id"
    );

ALTER TABLE "orders" ADD CONSTRAINT "PK_ORDERS" PRIMARY KEY (
                                                             "order_id"
    );

ALTER TABLE "news_cluster_article" ADD CONSTRAINT "PK_NEWS_CLUSTER_ARTICLE" PRIMARY KEY (
                                                                                         "news_cluster_article_id"
    );

ALTER TABLE "portfolio" ADD CONSTRAINT "PK_PORTFOLIO" PRIMARY KEY (
                                                                   "portfolio_id"
    );

ALTER TABLE "market_summary_source" ADD CONSTRAINT "PK_MARKET_SUMMARY_SOURCE" PRIMARY KEY (
                                                                                           "market_summary_source_id"
    );

ALTER TABLE "user_quest" ADD CONSTRAINT "PK_USER_QUEST" PRIMARY KEY (
                                                                     "quest_id"
    );

ALTER TABLE "market_insight_source" ADD CONSTRAINT "PK_MARKET_INSIGHT_SOURCE" PRIMARY KEY (
                                                                                           "market_insight_source_id"
    );

ALTER TABLE "stock_daily" ADD CONSTRAINT "FK_stock_info_TO_stock_daily_1" FOREIGN KEY (
                                                                                       "symbol"
    )
    REFERENCES "stock_info" (
                             "symbol"
        );

ALTER TABLE "game_participant" ADD CONSTRAINT "FK_user_TO_game_participant_1" FOREIGN KEY (
                                                                                           "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "game_participant" ADD CONSTRAINT "FK_game_room_TO_game_participant_1" FOREIGN KEY (
                                                                                                "room_id"
    )
    REFERENCES "game_room" (
                            "room_id"
        );

ALTER TABLE "daily_snapshot" ADD CONSTRAINT "FK_virtual_account_TO_daily_snapshot_1" FOREIGN KEY (
                                                                                                  "virtual_account_id"
    )
    REFERENCES "virtual_account" (
                                  "virtual_account_id"
        );

ALTER TABLE "news_article_tag" ADD CONSTRAINT "FK_news_article_TO_news_article_tag_1" FOREIGN KEY (
                                                                                                   "news_article_id"
    )
    REFERENCES "news_article" (
                               "news_article_id"
        );

ALTER TABLE "vote_option" ADD CONSTRAINT "FK_vote_TO_vote_option_1" FOREIGN KEY (
                                                                                 "vote_id"
    )
    REFERENCES "vote" (
                       "vote_id"
        );

ALTER TABLE "game_order" ADD CONSTRAINT "FK_game_participant_TO_game_order_1" FOREIGN KEY (
                                                                                           "participant_id"
    )
    REFERENCES "game_participant" (
                                   "participant_id"
        );

ALTER TABLE "game_order" ADD CONSTRAINT "FK_game_turn_TO_game_order_1" FOREIGN KEY (
                                                                                    "turn_id"
    )
    REFERENCES "game_turn" (
                            "turn_id"
        );

ALTER TABLE "game_order" ADD CONSTRAINT "FK_stock_info_TO_game_order_1" FOREIGN KEY (
                                                                                     "symbol"
    )
    REFERENCES "stock_info" (
                             "symbol"
        );

ALTER TABLE "market_insight_card" ADD CONSTRAINT "FK_market_trend_TO_market_insight_card_1" FOREIGN KEY (
                                                                                                         "market_trend_id"
    )
    REFERENCES "market_trend" (
                               "market_trend_id"
        );

ALTER TABLE "trade" ADD CONSTRAINT "FK_orders_TO_trade_1" FOREIGN KEY (
                                                                       "order_id"
    )
    REFERENCES "orders" (
                         "order_id"
        );

ALTER TABLE "trade" ADD CONSTRAINT "FK_virtual_account_TO_trade_1" FOREIGN KEY (
                                                                                "virtual_account_id"
    )
    REFERENCES "virtual_account" (
                                  "virtual_account_id"
        );

ALTER TABLE "trade" ADD CONSTRAINT "FK_stock_TO_trade_1" FOREIGN KEY (
                                                                      "stock_id"
    )
    REFERENCES "stock" (
                        "stock_id"
        );

ALTER TABLE "game_holding" ADD CONSTRAINT "FK_game_participant_TO_game_holding_1" FOREIGN KEY (
                                                                                               "participant_id"
    )
    REFERENCES "game_participant" (
                                   "participant_id"
        );

ALTER TABLE "game_holding" ADD CONSTRAINT "FK_stock_info_TO_game_holding_1" FOREIGN KEY (
                                                                                         "symbol"
    )
    REFERENCES "stock_info" (
                             "symbol"
        );

ALTER TABLE "game_room" ADD CONSTRAINT "FK_group_TO_game_room_1" FOREIGN KEY (
                                                                              "group_id"
    )
    REFERENCES "groups" (
                         "group_id"
        );

ALTER TABLE "game_room" ADD CONSTRAINT "FK_user_TO_game_room_1" FOREIGN KEY (
                                                                             "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "article_embedding" ADD CONSTRAINT "FK_news_article_TO_article_embedding_1" FOREIGN KEY (
                                                                                                     "news_article_id"
    )
    REFERENCES "news_article" (
                               "news_article_id"
        );

ALTER TABLE "user_interest" ADD CONSTRAINT "FK_user_TO_user_interest_1" FOREIGN KEY (
                                                                                     "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "news_cluster_summary" ADD CONSTRAINT "FK_news_cluster_TO_news_cluster_summary_1" FOREIGN KEY (
                                                                                                           "news_cluster_id"
    )
    REFERENCES "news_cluster" (
                               "news_cluster_id"
        );

ALTER TABLE "user_news_cluster_feedback" ADD CONSTRAINT "FK_user_TO_user_news_cluster_feedback_1" FOREIGN KEY (
                                                                                                               "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "user_news_cluster_feedback" ADD CONSTRAINT "FK_news_cluster_TO_user_news_cluster_feedback_1" FOREIGN KEY (
                                                                                                                       "news_cluster_id"
    )
    REFERENCES "news_cluster" (
                               "news_cluster_id"
        );

ALTER TABLE "vote_answer" ADD CONSTRAINT "FK_vote_option_TO_vote_answer_1" FOREIGN KEY (
                                                                                        "option_id"
    )
    REFERENCES "vote_option" (
                              "option_id"
        );

ALTER TABLE "vote_answer" ADD CONSTRAINT "FK_user_TO_vote_answer_1" FOREIGN KEY (
                                                                                 "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "group_invite" ADD CONSTRAINT "FK_group_TO_group_invite_1" FOREIGN KEY (
                                                                                    "group_id"
    )
    REFERENCES "groups" (
                         "group_id"
        );

ALTER TABLE "group_invite" ADD CONSTRAINT "FK_user_TO_group_invite_1" FOREIGN KEY (
                                                                                   "created_by"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "batch_progress" ADD CONSTRAINT "FK_stock_info_TO_batch_progress_1" FOREIGN KEY (
                                                                                             "symbol"
    )
    REFERENCES "stock_info" (
                             "symbol"
        );

ALTER TABLE "news_collect_batch" ADD CONSTRAINT "FK_news_source_TO_news_collect_batch_1" FOREIGN KEY (
                                                                                                      "news_source_id"
    )
    REFERENCES "news_source" (
                              "news_source_id"
        );

ALTER TABLE "news_article" ADD CONSTRAINT "FK_raw_news_article_TO_news_article_1" FOREIGN KEY (
                                                                                               "raw_news_article_id"
    )
    REFERENCES "raw_news_article" (
                                   "raw_news_article_id"
        );

ALTER TABLE "news_cluster_interest_mapping" ADD CONSTRAINT "FK_news_cluster_TO_news_cluster_interest_mapping_1" FOREIGN KEY (
                                                                                                                             "news_cluster_id"
    )
    REFERENCES "news_cluster" (
                               "news_cluster_id"
        );

ALTER TABLE "news_cluster_interest_mapping" ADD CONSTRAINT "FK_user_TO_news_cluster_interest_mapping_1" FOREIGN KEY (
                                                                                                                     "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "group_member" ADD CONSTRAINT "FK_user_TO_group_member_1" FOREIGN KEY (
                                                                                   "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "group_member" ADD CONSTRAINT "FK_group_TO_group_member_1" FOREIGN KEY (
                                                                                    "group_id"
    )
    REFERENCES "groups" (
                         "group_id"
        );

ALTER TABLE "ai_chat_message" ADD CONSTRAINT "FK_user_TO_ai_chat_message_1" FOREIGN KEY (
                                                                                         "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "news_cluster" ADD CONSTRAINT "FK_news_article_TO_news_cluster_1" FOREIGN KEY (
                                                                                           "representative_article_id"
    )
    REFERENCES "news_article" (
                               "news_article_id"
        );

ALTER TABLE "payment" ADD CONSTRAINT "FK_subscription_plan_TO_payment_1" FOREIGN KEY (
                                                                                      "plan_id"
    )
    REFERENCES "subscription_plan" (
                                    "plan_id"
        );

ALTER TABLE "payment" ADD CONSTRAINT "FK_user_TO_payment_1" FOREIGN KEY (
                                                                         "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "user_insight_card" ADD CONSTRAINT "FK_market_insight_card_TO_user_insight_card_1" FOREIGN KEY (
                                                                                                            "market_insight_card_id"
    )
    REFERENCES "market_insight_card" (
                                      "market_insight_card_id"
        );

ALTER TABLE "user_insight_card" ADD CONSTRAINT "FK_user_TO_user_insight_card_1" FOREIGN KEY (
                                                                                             "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "game_portfolio_snapshot" ADD CONSTRAINT "FK_game_turn_TO_game_portfolio_snapshot_1" FOREIGN KEY (
                                                                                                              "turn_id"
    )
    REFERENCES "game_turn" (
                            "turn_id"
        );

ALTER TABLE "game_portfolio_snapshot" ADD CONSTRAINT "FK_game_participant_TO_game_portfolio_snapshot_1" FOREIGN KEY (
                                                                                                                     "participant_id"
    )
    REFERENCES "game_participant" (
                                   "participant_id"
        );

ALTER TABLE "bet" ADD CONSTRAINT "FK_group_TO_bet_1" FOREIGN KEY (
                                                                  "group_id"
    )
    REFERENCES "groups" (
                         "group_id"
        );

ALTER TABLE "bet" ADD CONSTRAINT "FK_user_TO_bet_1" FOREIGN KEY (
                                                                 "created_by"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "chat_message" ADD CONSTRAINT "FK_group_TO_chat_message_1" FOREIGN KEY (
                                                                                    "group_id"
    )
    REFERENCES "groups" (
                         "group_id"
        );

ALTER TABLE "chat_message" ADD CONSTRAINT "FK_user_TO_chat_message_1" FOREIGN KEY (
                                                                                   "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "news_cluster_followup_question" ADD CONSTRAINT "FK_news_cluster_TO_news_cluster_followup_question_1" FOREIGN KEY (
                                                                                                                               "news_cluster_id"
    )
    REFERENCES "news_cluster" (
                               "news_cluster_id"
        );

ALTER TABLE "personal_market_trend_source" ADD CONSTRAINT "FK_personal_market_trend_TO_personal_market_trend_source_1" FOREIGN KEY (
                                                                                                                                    "personal_market_trend_id"
    )
    REFERENCES "personal_market_trend" (
                                        "personal_market_trend_id"
        );

ALTER TABLE "personal_market_trend_source" ADD CONSTRAINT "FK_news_article_TO_personal_market_trend_source_1" FOREIGN KEY (
                                                                                                                           "news_article_id"
    )
    REFERENCES "news_article" (
                               "news_article_id"
        );

ALTER TABLE "personal_market_trend_source" ADD CONSTRAINT "FK_article_embedding_TO_personal_market_trend_source_1" FOREIGN KEY (
                                                                                                                                "article_embedding_id"
    )
    REFERENCES "article_embedding" (
                                    "article_embedding_id"
        );

ALTER TABLE "global_chat_message" ADD CONSTRAINT "FK_user_TO_global_chat_message_1" FOREIGN KEY (
                                                                                                 "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "personalized_briefing" ADD CONSTRAINT "FK_news_cluster_TO_personalized_briefing_1" FOREIGN KEY (
                                                                                                             "base_news_cluster_id"
    )
    REFERENCES "news_cluster" (
                               "news_cluster_id"
        );

ALTER TABLE "personalized_briefing" ADD CONSTRAINT "FK_news_cluster_TO_personalized_briefing_2" FOREIGN KEY (
                                                                                                             "representative_news_cluster_id"
    )
    REFERENCES "news_cluster" (
                               "news_cluster_id"
        );

ALTER TABLE "raw_news_article" ADD CONSTRAINT "FK_news_source_TO_raw_news_article_1" FOREIGN KEY (
                                                                                                  "news_source_id"
    )
    REFERENCES "news_source" (
                              "news_source_id"
        );

ALTER TABLE "raw_news_article" ADD CONSTRAINT "FK_news_collect_batch_TO_raw_news_article_1" FOREIGN KEY (
                                                                                                         "news_collect_batch_id"
    )
    REFERENCES "news_collect_batch" (
                                     "news_collect_batch_id"
        );

ALTER TABLE "virtual_account" ADD CONSTRAINT "FK_user_TO_virtual_account_1" FOREIGN KEY (
                                                                                         "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "transfer" ADD CONSTRAINT "FK_group_TO_transfer_1" FOREIGN KEY (
                                                                            "group_id"
    )
    REFERENCES "groups" (
                         "group_id"
        );

ALTER TABLE "transfer" ADD CONSTRAINT "FK_user_TO_transfer_1" FOREIGN KEY (
                                                                           "sender_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "transfer" ADD CONSTRAINT "FK_user_TO_transfer_2" FOREIGN KEY (
                                                                           "receiver_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "transfer" ADD CONSTRAINT "FK_bet_TO_transfer_1" FOREIGN KEY (
                                                                          "game_id"
    )
    REFERENCES "bet" (
                      "game_id"
        );

ALTER TABLE "vote" ADD CONSTRAINT "FK_group_TO_vote_1" FOREIGN KEY (
                                                                    "group_id"
    )
    REFERENCES "groups" (
                         "group_id"
        );

ALTER TABLE "vote" ADD CONSTRAINT "FK_user_TO_vote_1" FOREIGN KEY (
                                                                   "created_by"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "subscription" ADD CONSTRAINT "FK_subscription_plan_TO_subscription_1" FOREIGN KEY (
                                                                                                "plan_id"
    )
    REFERENCES "subscription_plan" (
                                    "plan_id"
        );

ALTER TABLE "subscription" ADD CONSTRAINT "FK_user_TO_subscription_1" FOREIGN KEY (
                                                                                   "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "personal_market_trend" ADD CONSTRAINT "FK_user_TO_personal_market_trend_1" FOREIGN KEY (
                                                                                                     "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "betting_participant" ADD CONSTRAINT "FK_bet_TO_betting_participant_1" FOREIGN KEY (
                                                                                                "game_id"
    )
    REFERENCES "bet" (
                      "game_id"
        );

ALTER TABLE "betting_participant" ADD CONSTRAINT "FK_user_TO_betting_participant_1" FOREIGN KEY (
                                                                                                 "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "game_analysis_report" ADD CONSTRAINT "FK_stock_daily_TO_game_analysis_report_1" FOREIGN KEY (
                                                                                                          "daily_id"
    )
    REFERENCES "stock_daily" (
                              "daily_id"
        );

ALTER TABLE "game_analysis_report" ADD CONSTRAINT "FK_game_participant_TO_game_analysis_report_1" FOREIGN KEY (
                                                                                                               "participant_id"
    )
    REFERENCES "game_participant" (
                                   "participant_id"
        );

ALTER TABLE "personalized_briefing_source" ADD CONSTRAINT "FK_personalized_briefing_TO_personalized_briefing_source_1" FOREIGN KEY (
                                                                                                                                    "personalized_briefing_id"
    )
    REFERENCES "personalized_briefing" (
                                        "personalized_briefing_id"
        );

ALTER TABLE "personalized_briefing_source" ADD CONSTRAINT "FK_news_article_TO_personalized_briefing_source_1" FOREIGN KEY (
                                                                                                                           "news_article_id"
    )
    REFERENCES "news_article" (
                               "news_article_id"
        );

ALTER TABLE "personalized_briefing_source" ADD CONSTRAINT "FK_article_embedding_TO_personalized_briefing_source_1" FOREIGN KEY (
                                                                                                                                "article_embedding_id"
    )
    REFERENCES "article_embedding" (
                                    "article_embedding_id"
        );

ALTER TABLE "game_turn" ADD CONSTRAINT "FK_game_room_TO_game_turn_1" FOREIGN KEY (
                                                                                  "room_id"
    )
    REFERENCES "game_room" (
                            "room_id"
        );

ALTER TABLE "game_turn" ADD CONSTRAINT "FK_briefing_cache_TO_game_turn_1" FOREIGN KEY (
                                                                                       "briefing_id"
    )
    REFERENCES "briefing_cache" (
                                 "briefing_id"
        );

ALTER TABLE "user_news_cluster_read" ADD CONSTRAINT "FK_user_TO_user_news_cluster_read_1" FOREIGN KEY (
                                                                                                       "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "user_news_cluster_read" ADD CONSTRAINT "FK_news_cluster_TO_user_news_cluster_read_1" FOREIGN KEY (
                                                                                                               "news_cluster_id"
    )
    REFERENCES "news_cluster" (
                               "news_cluster_id"
        );

ALTER TABLE "market_snapshot" ADD CONSTRAINT "FK_market_trend_TO_market_snapshot_1" FOREIGN KEY (
                                                                                                 "market_trend_id"
    )
    REFERENCES "market_trend" (
                               "market_trend_id"
        );

ALTER TABLE "news_cluster_summary_source" ADD CONSTRAINT "FK_news_cluster_summary_TO_news_cluster_summary_source_1" FOREIGN KEY (
                                                                                                                                 "news_cluster_summary_id"
    )
    REFERENCES "news_cluster_summary" (
                                       "news_cluster_summary_id"
        );

ALTER TABLE "news_cluster_summary_source" ADD CONSTRAINT "FK_news_article_TO_news_cluster_summary_source_1" FOREIGN KEY (
                                                                                                                         "news_article_id"
    )
    REFERENCES "news_article" (
                               "news_article_id"
        );

ALTER TABLE "orders" ADD CONSTRAINT "FK_virtual_account_TO_orders_1" FOREIGN KEY (
                                                                                  "virtual_account_id"
    )
    REFERENCES "virtual_account" (
                                  "virtual_account_id"
        );

ALTER TABLE "orders" ADD CONSTRAINT "FK_stock_TO_orders_1" FOREIGN KEY (
                                                                        "stock_id"
    )
    REFERENCES "stock" (
                        "stock_id"
        );

ALTER TABLE "news_cluster_article" ADD CONSTRAINT "FK_news_cluster_TO_news_cluster_article_1" FOREIGN KEY (
                                                                                                           "news_cluster_id"
    )
    REFERENCES "news_cluster" (
                               "news_cluster_id"
        );

ALTER TABLE "news_cluster_article" ADD CONSTRAINT "FK_news_article_TO_news_cluster_article_1" FOREIGN KEY (
                                                                                                           "news_article_id"
    )
    REFERENCES "news_article" (
                               "news_article_id"
        );

ALTER TABLE "portfolio" ADD CONSTRAINT "FK_virtual_account_TO_portfolio_1" FOREIGN KEY (
                                                                                        "virtual_account_id"
    )
    REFERENCES "virtual_account" (
                                  "virtual_account_id"
        );

ALTER TABLE "portfolio" ADD CONSTRAINT "FK_stock_TO_portfolio_1" FOREIGN KEY (
                                                                              "stock_id"
    )
    REFERENCES "stock" (
                        "stock_id"
        );

ALTER TABLE "market_summary_source" ADD CONSTRAINT "FK_market_summary_TO_market_summary_source_1" FOREIGN KEY (
                                                                                                               "market_summary_id"
    )
    REFERENCES "market_summary" (
                                 "market_summary_id"
        );

ALTER TABLE "market_summary_source" ADD CONSTRAINT "FK_news_article_TO_market_summary_source_1" FOREIGN KEY (
                                                                                                             "news_article_id"
    )
    REFERENCES "news_article" (
                               "news_article_id"
        );

ALTER TABLE "market_summary_source" ADD CONSTRAINT "FK_article_embedding_TO_market_summary_source_1" FOREIGN KEY (
                                                                                                                  "article_embedding_id"
    )
    REFERENCES "article_embedding" (
                                    "article_embedding_id"
        );

ALTER TABLE "user_quest" ADD CONSTRAINT "FK_quest_template_TO_user_quest_1" FOREIGN KEY (
                                                                                         "template_id"
    )
    REFERENCES "quest_template" (
                                 "template_id"
        );

ALTER TABLE "user_quest" ADD CONSTRAINT "FK_user_TO_user_quest_1" FOREIGN KEY (
                                                                               "user_id"
    )
    REFERENCES "users" (
                        "user_id"
        );

ALTER TABLE "market_insight_source" ADD CONSTRAINT "FK_market_insight_card_TO_market_insight_source_1" FOREIGN KEY (
                                                                                                                    "market_insight_card_id"
    )
    REFERENCES "market_insight_card" (
                                      "market_insight_card_id"
        );

ALTER TABLE "market_insight_source" ADD CONSTRAINT "FK_news_article_TO_market_insight_source_1" FOREIGN KEY (
                                                                                                             "news_article_id"
    )
    REFERENCES "news_article" (
                               "news_article_id"
        );

ALTER TABLE "market_insight_source" ADD CONSTRAINT "FK_article_embedding_TO_market_insight_source_1" FOREIGN KEY (
                                                                                                                  "article_embedding_id"
    )
    REFERENCES "article_embedding" (
                                    "article_embedding_id"
        );

ALTER TABLE "game_holding"
    ADD COLUMN quantity INT NOT NULL DEFAULT 0;



ALTER TABLE game_participant
    ADD CONSTRAINT uk_participant_user_room UNIQUE (user_id, room_id);

ALTER TABLE game_turn
    ADD CONSTRAINT uk_turn_room_number UNIQUE (room_id, turn_number);

ALTER TABLE game_holding
    ADD CONSTRAINT uk_holding_participant_symbol UNIQUE (participant_id, symbol);

ALTER TABLE game_portfolio_snapshot
    ADD CONSTRAINT uk_snapshot_turn_participant UNIQUE (turn_id, participant_id);

ALTER TABLE stock_daily
    ADD CONSTRAINT uk_daily_symbol_date UNIQUE ("symbol", "trade_date");

ALTER TABLE briefing_cache
    ADD CONSTRAINT uk_briefing_date UNIQUE (target_date);

ALTER TABLE game_news_archive
    ADD CONSTRAINT uk_news_url UNIQUE (original_url);

ALTER TABLE game_analysis_report
    ADD CONSTRAINT uk_report_daily_participant UNIQUE (daily_id, participant_id);

ALTER TABLE batch_progress
    ADD CONSTRAINT uk_batch_symbol_type UNIQUE (symbol, batch_type);



ALTER TABLE game_room
    ADD CONSTRAINT chk_room_status CHECK (status IN ('WAITING', 'IN_PROGRESS', 'FINISHED')),
ADD CONSTRAINT chk_room_seed CHECK (seed > 0),
ADD CONSTRAINT chk_room_period CHECK (period_month > 0),
ADD CONSTRAINT chk_room_move_days CHECK (move_days > 0);

ALTER TABLE game_participant
    ADD CONSTRAINT chk_participant_status CHECK (status IN ('ACTIVE', 'LEFT'));

ALTER TABLE game_turn
    ADD CONSTRAINT chk_turn_status CHECK (status IN ('ACTIVE', 'COMPLETED'));

ALTER TABLE game_order
    ADD CONSTRAINT chk_order_type CHECK (order_type IN ('BUY', 'SELL')),
ADD CONSTRAINT chk_order_quantity CHECK (quantity > 0),
ADD CONSTRAINT chk_order_price CHECK (order_price > 0);

ALTER TABLE game_holding
    ADD CONSTRAINT chk_holding_quantity CHECK (quantity > 0),
ADD CONSTRAINT chk_holding_avg_price CHECK (avg_price > 0);

ALTER TABLE batch_progress
    ADD CONSTRAINT chk_batch_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED'));


ALTER TABLE orders ADD CONSTRAINT uk_orders_order_no UNIQUE (order_no);
ALTER TABLE trade ADD CONSTRAINT uk_trade_trade_no UNIQUE (trade_no);
ALTER TABLE portfolio ADD CONSTRAINT uk_portfolio_account_stock UNIQUE (virtual_account_id, stock_id);
ALTER TABLE daily_snapshot ADD CONSTRAINT uk_snapshot_account_date UNIQUE (virtual_account_id, snapshot_date);
ALTER TABLE virtual_account ADD CONSTRAINT uk_virtual_account_user UNIQUE (user_id);
ALTER TABLE stock ADD CONSTRAINT uk_stock_code UNIQUE (stock_code);


ALTER TABLE batch_progress
    ALTER COLUMN status SET DEFAULT 'PENDING',
ALTER COLUMN retry_count SET DEFAULT 0;


-- user_interest 수정
ALTER TABLE "user_interest"
DROP CONSTRAINT IF EXISTS "PK_USER_INTEREST";

ALTER TABLE "user_interest"
DROP COLUMN "user_interest_id";

ALTER TABLE "user_interest"
    ADD COLUMN "user_interest_id" BIGSERIAL;

ALTER TABLE "user_interest"
    ADD CONSTRAINT "PK_USER_INTEREST" PRIMARY KEY ("user_interest_id");

ALTER TABLE "user_interest"
ALTER COLUMN "weight" TYPE NUMERIC(5,2)
USING NULLIF("weight", '')::NUMERIC(5,2);

ALTER TABLE "user_interest"
ALTER COLUMN "interest_type" TYPE VARCHAR(30);

ALTER TABLE "user_interest"
ALTER COLUMN "interest_value" TYPE VARCHAR(100);

-- FK 컬럼 BIGSERIAL -> BIGINT
ALTER TABLE "market_insight_card"
ALTER COLUMN "market_trend_id" TYPE BIGINT;

ALTER TABLE "news_cluster_summary"
ALTER COLUMN "news_cluster_id" TYPE BIGINT;

ALTER TABLE "news_collect_batch"
ALTER COLUMN "news_source_id" TYPE BIGINT;

ALTER TABLE "news_cluster_interest_mapping"
ALTER COLUMN "news_cluster_id" TYPE BIGINT;

ALTER TABLE "user_insight_card"
ALTER COLUMN "market_insight_card_id" TYPE BIGINT;

ALTER TABLE "personalized_briefing"
ALTER COLUMN "base_news_cluster_id" TYPE BIGINT,
ALTER COLUMN "representative_news_cluster_id" TYPE BIGINT;

ALTER TABLE "raw_news_article"
ALTER COLUMN "news_source_id" TYPE BIGINT,
ALTER COLUMN "news_collect_batch_id" TYPE BIGINT;

ALTER TABLE "personalized_briefing_source"
ALTER COLUMN "personalized_briefing_id" TYPE BIGINT,
ALTER COLUMN "news_article_id" TYPE BIGINT,
ALTER COLUMN "article_embedding_id" TYPE BIGINT;

ALTER TABLE "market_snapshot"
ALTER COLUMN "market_trend_id" TYPE BIGINT;

ALTER TABLE "news_cluster_summary_source"
ALTER COLUMN "news_cluster_summary_id" TYPE BIGINT,
ALTER COLUMN "news_article_id" TYPE BIGINT;

ALTER TABLE "market_summary_source"
ALTER COLUMN "market_summary_id" TYPE BIGINT,
ALTER COLUMN "news_article_id" TYPE BIGINT,
ALTER COLUMN "article_embedding_id" TYPE BIGINT;

ALTER TABLE "user_interest"
    ADD CONSTRAINT "uk_user_interest_user_type_value"
        UNIQUE ("user_id", "interest_type", "interest_value");

ALTER TABLE "news_article"
    ADD CONSTRAINT "uk_news_article_original_url" UNIQUE ("original_url");

ALTER TABLE "news_article"
    ADD CONSTRAINT "uk_news_article_dedup_key" UNIQUE ("dedup_key");

ALTER TABLE "article_embedding"
    ADD CONSTRAINT "uk_article_embedding_article_chunk"
        UNIQUE ("news_article_id", "chunk_index");

ALTER TABLE "news_article_tag"
    ADD CONSTRAINT "uk_news_article_tag_article_type_code"
        UNIQUE ("news_article_id", "tag_type", "tag_code");

ALTER TABLE "news_cluster_summary"
    ADD CONSTRAINT "uk_news_cluster_summary_cluster"
        UNIQUE ("news_cluster_id");

ALTER TABLE "news_cluster_summary_source"
    ADD CONSTRAINT "uk_news_cluster_summary_source_order"
        UNIQUE ("news_cluster_summary_id", "source_order");

ALTER TABLE "news_cluster_summary_source"
    ADD CONSTRAINT "uk_news_cluster_summary_source_article"
        UNIQUE ("news_cluster_summary_id", "news_article_id");

ALTER TABLE "news_cluster_article"
    ADD CONSTRAINT "uk_news_cluster_article_cluster_article"
        UNIQUE ("news_cluster_id", "news_article_id");

ALTER TABLE "news_cluster_article"
    ADD CONSTRAINT "uk_news_cluster_article_cluster_order"
        UNIQUE ("news_cluster_id", "article_order");

ALTER TABLE "news_cluster_followup_question"
    ADD CONSTRAINT "uk_news_cluster_followup_question_order"
        UNIQUE ("news_cluster_id", "question_order");

ALTER TABLE "user_news_cluster_read"
    ADD CONSTRAINT "uk_user_news_cluster_read_user_cluster"
        UNIQUE ("user_id", "news_cluster_id");

ALTER TABLE "user_news_cluster_feedback"
    ADD CONSTRAINT "uk_user_news_cluster_feedback_user_cluster"
        UNIQUE ("user_id", "news_cluster_id");

ALTER TABLE "market_trend"
    ADD CONSTRAINT "uk_market_trend_date_session"
        UNIQUE ("trend_date", "session");

ALTER TABLE "market_snapshot"
    ADD CONSTRAINT "uk_market_snapshot_metric_type"
        UNIQUE ("market_trend_id", "metric_type");

ALTER TABLE "market_snapshot"
    ADD CONSTRAINT "uk_market_snapshot_display_order"
        UNIQUE ("market_trend_id", "display_order");

ALTER TABLE "market_insight_card"
    ADD CONSTRAINT "uk_market_insight_card_display_order"
        UNIQUE ("market_trend_id", "display_order");

ALTER TABLE "market_insight_source"
    ADD CONSTRAINT "uk_market_insight_source_order"
        UNIQUE ("market_insight_card_id", "source_order");

ALTER TABLE "market_summary_source"
    ADD CONSTRAINT "uk_market_summary_source_order"
        UNIQUE ("market_summary_id", "source_order");

ALTER TABLE "personal_market_trend"
    ADD CONSTRAINT "uk_personal_market_trend_user_date_session"
        UNIQUE ("user_id", "trend_date", "session");

ALTER TABLE "personal_market_trend_source"
    ADD CONSTRAINT "uk_personal_market_trend_source_order"
        UNIQUE ("personal_market_trend_id", "source_order");

ALTER TABLE "user_insight_card"
    ADD CONSTRAINT "uk_user_insight_card_user_card"
        UNIQUE ("user_id", "market_insight_card_id");


ALTER TABLE "personalized_briefing_source"
    ADD CONSTRAINT "uk_personalized_briefing_source_order"
        UNIQUE ("personalized_briefing_id", "source_order");


CREATE UNIQUE INDEX IF NOT EXISTS "uk_raw_news_article_source_external_id"
    ON "raw_news_article" ("news_source_id", "external_article_id")
    WHERE "external_article_id" IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS "uk_raw_news_article_original_url"
    ON "raw_news_article" ("original_url");


CREATE UNIQUE INDEX IF NOT EXISTS "uk_personalized_briefing_daily_once"
    ON "personalized_briefing" ("user_id", "briefing_kind", "briefing_date", "session")
    WHERE "briefing_kind" = 'DAILY';

ALTER TABLE "user_interest"
    ADD CONSTRAINT "chk_user_interest_type"
        CHECK ("interest_type" IN ('STOCK', 'SECTOR', 'TOPIC'));

ALTER TABLE "news_source"
    ADD CONSTRAINT "chk_news_source_type"
        CHECK ("source_type" IN ('API', 'RSS', 'CRAWLER'));

ALTER TABLE "news_collect_batch"
    ADD CONSTRAINT "chk_news_collect_batch_status"
        CHECK ("status" IN ('READY', 'RUNNING', 'SUCCESS', 'FAILED'));

ALTER TABLE "raw_news_article"
    ADD CONSTRAINT "chk_raw_news_article_status"
        CHECK ("processing_status" IN ('PENDING', 'NORMALIZED', 'FAILED', 'DUPLICATED'));

ALTER TABLE "news_article"
    ADD CONSTRAINT "chk_news_article_category"
        CHECK ("category" IS NULL OR "category" IN ('ECONOMY', 'POLITICS', 'SOCIETY', 'IT_SCIENCE', 'GLOBAL'));

ALTER TABLE "news_article"
    ADD CONSTRAINT "chk_news_article_market_scope"
        CHECK ("market_scope" IS NULL OR "market_scope" IN ('DOMESTIC', 'GLOBAL'));

ALTER TABLE "news_article"
    ADD CONSTRAINT "chk_news_article_language_code"
        CHECK ("language_code" IS NULL OR "language_code" IN ('KO', 'EN'));

ALTER TABLE "news_article_tag"
    ADD CONSTRAINT "chk_news_article_tag_type"
        CHECK ("tag_type" IN ('STOCK', 'SECTOR', 'TOPIC'));

ALTER TABLE "news_cluster"
    ADD CONSTRAINT "chk_news_cluster_type"
        CHECK ("cluster_type" IN ('TOPIC', 'STOCK', 'GENERAL'));

ALTER TABLE "news_cluster_summary_source"
    ADD CONSTRAINT "chk_news_cluster_summary_source_order_positive"
        CHECK ("source_order" > 0);

ALTER TABLE "news_cluster_followup_question"
    ADD CONSTRAINT "chk_news_cluster_followup_question_order_positive"
        CHECK ("question_order" > 0);

ALTER TABLE "news_cluster_interest_mapping"
    ADD CONSTRAINT "chk_news_cluster_interest_mapping_type"
        CHECK ("interest_type" IN ('STOCK', 'SECTOR', 'TOPIC'));

ALTER TABLE "market_trend"
    ADD CONSTRAINT "chk_market_trend_session"
        CHECK ("session" IN ('MORNING', 'EVENING'));

ALTER TABLE "market_snapshot"
    ADD CONSTRAINT "chk_market_snapshot_metric_type"
        CHECK ("metric_type" IN ('KOSPI', 'NASDAQ', 'BASE_RATE', 'USD_KRW'));

ALTER TABLE "market_snapshot"
    ADD CONSTRAINT "chk_market_snapshot_unit"
        CHECK ("unit" IN ('POINT', 'PERCENT', 'KRW'));

ALTER TABLE "market_snapshot"
    ADD CONSTRAINT "chk_market_snapshot_direction"
        CHECK ("change_direction" IN ('UP', 'DOWN', 'FLAT'));

ALTER TABLE "market_snapshot"
    ADD CONSTRAINT "chk_market_snapshot_display_order_positive"
        CHECK ("display_order" > 0);

ALTER TABLE "market_insight_card"
    ADD CONSTRAINT "chk_market_insight_card_target_type"
        CHECK ("target_type" IN ('STOCK', 'SECTOR', 'TOPIC'));

ALTER TABLE "market_insight_card"
    ADD CONSTRAINT "chk_market_insight_card_display_order_positive"
        CHECK ("display_order" > 0);

ALTER TABLE "personal_market_trend"
    ADD CONSTRAINT "chk_personal_market_trend_session"
        CHECK ("session" IN ('MORNING', 'EVENING'));

ALTER TABLE "personalized_briefing"
    ADD CONSTRAINT "chk_personalized_briefing_kind"
        CHECK ("briefing_kind" IN ('DAILY', 'NEWS_DETAIL'));

ALTER TABLE "personalized_briefing"
    ADD CONSTRAINT "chk_personalized_briefing_session"
        CHECK ("session" IS NULL OR "session" IN ('MORNING', 'EVENING'));

ALTER TABLE "personalized_briefing"
    ADD CONSTRAINT "chk_personalized_briefing_interest_type"
        CHECK ("interest_type" IS NULL OR "interest_type" IN ('STOCK', 'SECTOR', 'TOPIC'));

ALTER TABLE "personalized_briefing"
    ADD CONSTRAINT "chk_personalized_briefing_tone"
        CHECK ("tone" IS NULL OR "tone" IN ('DEFAULT', 'CONCISE', 'DETAILED'));

ALTER TABLE "user_news_cluster_feedback"
    ADD CONSTRAINT "chk_user_news_cluster_feedback_type"
        CHECK ("feedback_type" IN ('HELPFUL', 'NOT_HELPFUL'));

ALTER TABLE "news_collect_batch"
    ADD CONSTRAINT "chk_news_collect_batch_counts_non_negative"
        CHECK ("collected_count" >= 0 AND "failed_count" >= 0);

ALTER TABLE "news_collect_batch"
    ADD CONSTRAINT "chk_news_collect_batch_finished_after_started"
        CHECK ("finished_at" IS NULL OR "finished_at" >= "started_at");

ALTER TABLE "article_embedding"
    ADD CONSTRAINT "chk_article_embedding_chunk_index_non_negative"
        CHECK ("chunk_index" >= 0);

ALTER TABLE "article_embedding"
    ADD CONSTRAINT "chk_article_embedding_token_count_non_negative"
        CHECK ("token_count" IS NULL OR "token_count" >= 0);


ALTER TABLE "market_summary_source"
    ADD CONSTRAINT "chk_market_summary_source_order_positive"
        CHECK ("source_order" > 0);

ALTER TABLE "market_insight_source"
    ADD CONSTRAINT "chk_market_insight_source_order_positive"
        CHECK ("source_order" > 0);

ALTER TABLE "personal_market_trend_source"
    ADD CONSTRAINT "chk_personal_market_trend_source_order_positive"
        CHECK ("source_order" > 0);

ALTER TABLE "personalized_briefing_source"
    ADD CONSTRAINT "chk_personalized_briefing_source_order_positive"
        CHECK ("source_order" > 0);

ALTER TABLE "user_insight_card"
    ADD CONSTRAINT "chk_user_insight_card_relevance_score_range"
        CHECK ("relevance_score" IS NULL OR ("relevance_score" >= 0 AND "relevance_score" <= 100));