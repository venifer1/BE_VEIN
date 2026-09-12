-- 온보딩 "시작하기" 체크리스트(R48, Track B #2). 신규 사용자가 핵심 기능(관심종목·알림·
-- 모의투자·조건검색)에 도달하도록 안내하는 홈 카드. 사용자가 닫으면 이 시각을 기록해 다시
-- 띄우지 않는다. NULL = 아직 안 닫음. 스텝 완료 여부는 각 기능의 실제 데이터에서 파생하므로
-- 별도 저장하지 않는다.
ALTER TABLE users ADD COLUMN onboarding_dismissed_at TIMESTAMPTZ NULL;
