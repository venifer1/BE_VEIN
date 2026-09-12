package com.vein.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 환경설정(조용한 시간) 조회/수정 + 억제 판정 (R42). 조용한 시간 창 동안 새 알림 생성을
 * 건너뛰어 노이즈를 줄인다. 판정은 KST 기준, 자정을 넘는 창을 지원한다. 절대 throw하지 않고
 * 설정 조회 실패 시 "억제 안 함"으로 안전 폴백한다.
 */
@Service
public class NotificationPrefService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationPrefRepository repository;

    public NotificationPrefService(NotificationPrefRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NotificationPref getOrDefault(Long userId) {
        return repository.findById(userId).orElseGet(() -> NotificationPref.defaults(userId));
    }

    @Transactional
    public NotificationPref update(Long userId, boolean enabled, int startHour, int endHour) {
        NotificationPref pref = repository.findById(userId)
                .orElseGet(() -> NotificationPref.defaults(userId));
        pref.update(enabled, startHour, endHour);
        return repository.save(pref);
    }

    /**
     * True if {@code now} falls inside the user's enabled quiet window (KST).
     * Never throws — any lookup failure means "not quiet" so alerts still fire.
     */
    @Transactional(readOnly = true)
    public boolean isQuietNow(Long userId, Instant now) {
        if (userId == null) {
            return false;
        }
        try {
            NotificationPref pref = repository.findById(userId).orElse(null);
            if (pref == null || !pref.isQuietEnabled()) {
                return false;
            }
            int hour = now.atZone(KST).getHour();
            return inWindow(hour, pref.getQuietStartHour(), pref.getQuietEndHour());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 스풀링(R46): {@code now}가 사용자의 활성 조용한 시간 창 안이면 그 창이 끝나는 순간(Instant)을,
     * 아니면 null을 반환한다. 보류 알림의 {@code held_until}로 쓴다. 절대 throw하지 않는다.
     */
    @Transactional(readOnly = true)
    public Instant quietWindowEnd(Long userId, Instant now) {
        if (userId == null) {
            return null;
        }
        try {
            NotificationPref pref = repository.findById(userId).orElse(null);
            if (pref == null || !pref.isQuietEnabled()) {
                return null;
            }
            return windowEnd(now, pref.getQuietStartHour(), pref.getQuietEndHour());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * {@code now}(KST)가 창 [start,end) 안이면 그 창이 끝나는 다음 {@code end}:00 KST 순간을,
     * 밖이면 null을 반환. 자정을 넘는 창(start&gt;end)도 지원. 순수 함수(테스트 용이).
     */
    static Instant windowEnd(Instant now, int start, int end) {
        ZonedDateTime kstNow = now.atZone(KST);
        if (!inWindow(kstNow.getHour(), start, end)) {
            return null;
        }
        ZonedDateTime candidate = kstNow.toLocalDate().atTime(end % 24, 0).atZone(KST);
        if (!candidate.isAfter(kstNow)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    /** start==end → 창 없음; start<end → [start,end); start>end → 자정을 넘는 창. */
    static boolean inWindow(int hour, int start, int end) {
        if (start == end) {
            return false;
        }
        if (start < end) {
            return hour >= start && hour < end;
        }
        return hour >= start || hour < end;
    }
}
