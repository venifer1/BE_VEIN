package com.vein.notification;

import java.time.Instant;
import java.time.ZoneId;

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
