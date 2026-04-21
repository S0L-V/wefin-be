-- ETF/ETN 종목 마스터 데이터 추가 (중복 무시)
INSERT INTO stock (stock_code, stock_name, market, sector) VALUES
-- KODEX (삼성자산운용)
('069500', 'KODEX 200', 'KOSPI', 'ETF'),
('122630', 'KODEX 레버리지', 'KOSPI', 'ETF'),
('114800', 'KODEX 인버스', 'KOSPI', 'ETF'),
('252670', 'KODEX 200선물인버스2X', 'KOSPI', 'ETF'),
('229200', 'KODEX 코스닥150', 'KOSPI', 'ETF'),
('233740', 'KODEX 코스닥150레버리지', 'KOSPI', 'ETF'),
('251340', 'KODEX 코스닥150선물인버스', 'KOSPI', 'ETF'),
('091160', 'KODEX 반도체', 'KOSPI', 'ETF'),
('091170', 'KODEX 은행', 'KOSPI', 'ETF'),
('266410', 'KODEX 1차전지&4차산업혁명', 'KOSPI', 'ETF'),
('305720', 'KODEX 2차전지산업', 'KOSPI', 'ETF'),
('379800', 'KODEX 미국S&P500TR', 'KOSPI', 'ETF'),
('379810', 'KODEX 미국나스닥100TR', 'KOSPI', 'ETF'),
('132030', 'KODEX 골드선물(H)', 'KOSPI', 'ETF'),
('261220', 'KODEX WTI원유선물(H)', 'KOSPI', 'ETF'),
('364690', 'KODEX 은행TOP10', 'KOSPI', 'ETF'),
('494310', 'KODEX 반도체레버리지', 'KOSPI', 'ETF'),
('462330', 'KODEX 2차전지산업레버리지', 'KOSPI', 'ETF'),
-- TIGER (미래에셋자산운용)
('105190', 'TIGER 200', 'KOSPI', 'ETF'),
('091230', 'TIGER 반도체', 'KOSPI', 'ETF'),
('091220', 'TIGER 은행', 'KOSPI', 'ETF'),
('133690', 'TIGER 미국나스닥100', 'KOSPI', 'ETF'),
('360750', 'TIGER 미국S&P500', 'KOSPI', 'ETF'),
('329750', 'TIGER 미국필라델피아반도체나스닥', 'KOSPI', 'ETF'),
('371460', 'TIGER 차이나전기차SOLACTIVE', 'KOSPI', 'ETF'),
('305540', 'TIGER 2차전지테마', 'KOSPI', 'ETF'),
('364980', 'TIGER Fn반도체TOP10', 'KOSPI', 'ETF'),
('395170', 'TIGER 미국테크TOP10 INDXX', 'KOSPI', 'ETF'),
('411060', 'TIGER 미국배당+7%프리미엄다우존스', 'KOSPI', 'ETF'),
('458730', 'TIGER 인도니프티50', 'KOSPI', 'ETF'),
('130730', 'TIGER 원유선물Enhanced(H)', 'KOSPI', 'ETF'),
-- ACE (한국투자신탁운용)
('332500', 'ACE 미국빅테크TOP7 Plus', 'KOSPI', 'ETF'),
('360200', 'ACE 미국나스닥100', 'KOSPI', 'ETF'),
('429760', 'ACE 미국S&P500', 'KOSPI', 'ETF'),
('365780', 'ACE KRX금현물', 'KOSPI', 'ETF'),
-- SOL (신한자산운용)
('401470', 'SOL 미국S&P500', 'KOSPI', 'ETF'),
('394280', 'SOL 미국나스닥100', 'KOSPI', 'ETF'),
('433330', 'SOL 미국배당다우존스', 'KOSPI', 'ETF'),
-- KBSTAR (KB자산운용)
('270800', 'KBSTAR 미국S&P500', 'KOSPI', 'ETF'),
('368590', 'KBSTAR 미국나스닥100', 'KOSPI', 'ETF'),
-- ARIRANG (한화자산운용)
('161510', 'ARIRANG 고배당주', 'KOSPI', 'ETF'),
('333940', 'ARIRANG 미국S&P500(H)', 'KOSPI', 'ETF'),
('252710', 'TIGER 200선물인버스2X', 'KOSPI', 'ETF'),
('396500', 'TIGER 반도체TOP10', 'KOSPI', 'ETF'),
('412570', 'TIGER 2차전지TOP10레버리지', 'KOSPI', 'ETF'),
('102110', 'TIGER 200', 'KOSPI', 'ETF'),
-- 일반 종목 (랭킹 상위 누락분)
('101670', '하이드로리튬', 'KOSDAQ', '기타'),
('387570', '파인메딕스', 'KOSDAQ', '기타'),
('115500', '케이씨에스', 'KOSDAQ', '기타'),
('009580', '무림P&P', 'KOSPI', '기타'),
-- ETN
('Q530036', '삼성 인버스 2X WTI원유 선물 ETN', 'KOSPI', 'ETN'),
('Q530138', '삼성 KRX 레버리지 2차전지 TOP10 TR ETN', 'KOSPI', 'ETN'),
('Q580070', 'KB 레버리지 2차전지 TOP 10 TR ETN', 'KOSPI', 'ETN')
ON CONFLICT (stock_code) DO NOTHING;
