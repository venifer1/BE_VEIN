package com.vein.billing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.billing.EntitlementsDto.Status;
import com.vein.user.User;
import com.vein.user.UserRepository;

/**
 * 사용자 티어 조회 + 엔타이틀먼트 파생 (Track C, R52). 게이트(예: 조건검색식 한도)에서 재사용.
 */
@Service
public class EntitlementsService {

    private final UserRepository userRepository;

    public EntitlementsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public String tierOf(Long userId) {
        return Entitlements.normalize(
                userRepository.findById(userId).map(User::getTier).orElse(Entitlements.FREE));
    }

    @Transactional(readOnly = true)
    public Status entitlements(Long userId) {
        return Entitlements.forTier(tierOf(userId));
    }

    /** 저장 조건검색식 한도(-1=무제한). 게이트 재사용. */
    @Transactional(readOnly = true)
    public int scannerRuleLimit(Long userId) {
        return Entitlements.scannerRuleLimit(tierOf(userId));
    }

    /** 신호 알림 규칙 한도(-1=무제한). 게이트 재사용. */
    @Transactional(readOnly = true)
    public int alertLimit(Long userId) {
        return Entitlements.alertLimit(tierOf(userId));
    }
}
