-- ETF/ETN 종목 마스터 데이터 추가 (랭킹 API 응답에서 확인된 종목만)
INSERT INTO stock (stock_code, stock_name, market, sector) VALUES
-- 랭킹 API 응답 기반 ETF (한투 API에서 코드-종목명 매핑 확인됨)
('069500', 'KODEX 200', 'KOSPI', 'ETF'),
('122630', 'KODEX 레버리지', 'KOSPI', 'ETF'),
('114800', 'KODEX 인버스', 'KOSPI', 'ETF'),
('252670', 'KODEX 200선물인버스2X', 'KOSPI', 'ETF'),
('233740', 'KODEX 코스닥150레버리지', 'KOSPI', 'ETF'),
('251340', 'KODEX 코스닥150선물인버스', 'KOSPI', 'ETF'),
('305720', 'KODEX 2차전지산업', 'KOSPI', 'ETF'),
('379800', 'KODEX 미국S&P500', 'KOSPI', 'ETF'),
('494310', 'KODEX 반도체레버리지', 'KOSPI', 'ETF'),
('462330', 'KODEX 2차전지산업레버리지', 'KOSPI', 'ETF'),
('252710', 'TIGER 200선물인버스2X', 'KOSPI', 'ETF'),
('396500', 'TIGER 반도체TOP10', 'KOSPI', 'ETF'),
('412570', 'TIGER 2차전지TOP10레버리지', 'KOSPI', 'ETF'),
('102110', 'TIGER 200', 'KOSPI', 'ETF'),
-- 랭킹 API 응답 기반 일반 종목 (누락분)
('101670', '하이드로리튬', 'KOSDAQ', '기타'),
('387570', '파인메딕스', 'KOSDAQ', '기타'),
('115500', '케이씨에스', 'KOSDAQ', '기타'),
('009580', '무림P&P', 'KOSPI', '기타'),
-- 랭킹 API 응답 기반 ETN
('Q530036', '삼성 인버스 2X WTI원유 선물 ETN', 'KOSPI', 'ETN'),
('Q530138', '삼성 KRX 레버리지 2차전지 TOP10 TR ETN', 'KOSPI', 'ETN'),
('Q580070', 'KB 레버리지 2차전지 TOP 10 TR ETN', 'KOSPI', 'ETN')
ON CONFLICT (stock_code) DO NOTHING;
