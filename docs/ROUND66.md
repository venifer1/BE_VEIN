# VEIN Round 66

Date: 2026-09-12

## 신호 카드에 C 목표까지 거리(%) (FE)

자율 루프 R66. 신호 카드(ABC/TOP)는 "C {가격}"만 보여줘 **현재가 대비 얼마나 남았는지**를
암산해야 했다. 리스트 DTO에 이미 있는 `current_price`·`c_target`으로 거리(%)를 함께 표시한다.

### 바뀐 것 (FE)

- `SignalCard`: ABC/TOP에서 `C {가격} (+X.X%)` 형태로 **현재가→C목표 거리(%)** 표시(양수 +, 소수
  1자리). `current_price`가 0/비유한이면 % 생략(방어). 백엔드/계약 무변경(리스트 DTO 기존 필드).

### 검증

- 프론트 `typecheck`/`build` 통과. **Chrome smoke 0에러**(신호 목록 렌더 경로 포함).
