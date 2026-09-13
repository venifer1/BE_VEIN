-- R90: 종합 Pattern Score 영속화 컬럼.
-- 구조 점수(score)와 별개로 완성도·거래량·추세·변동성·뉴스를 합산한 큐레이션 점수(0~100)를
-- 저장해 홈 "오늘의 주목 신호" 정렬에 쓴다. 미계산 신호는 정렬 시 score로 폴백(nullable).
ALTER TABLE pattern_signals ADD COLUMN pattern_score numeric(6,2);

-- top() 정렬 가속: coalesce(pattern_score, score) 내림차순 후보 조회용 부분 인덱스(활성만).
CREATE INDEX ix_signals_pattern_score_active
    ON pattern_signals (coalesce(pattern_score, score) DESC, detected_at DESC)
    WHERE status IN ('DETECTED', 'NEAR_COMPLETION');
