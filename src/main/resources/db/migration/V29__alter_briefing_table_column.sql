ALTER TABLE briefing_cache DROP COLUMN briefing_text;

ALTER TABLE briefing_cache ADD COLUMN market_overview  TEXT NOT NULL;
ALTER TABLE briefing_cache ADD COLUMN key_issues TEXT NOT NULL;

ALTER TABLE briefing_cache ADD COLUMN investment_hint  TEXT NOT NULL;