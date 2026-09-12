package com.vein.billing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.alert.AlertRepository;
import com.vein.billing.EntitlementsDto.Status;
import com.vein.condition.ScannerRuleRepository;
import com.vein.user.User;
import com.vein.user.UserRepository;

/**
 * 사용자 티어 조회 + 엔타이틀먼트 파생 (Track C, R52). 게이트(예: 조건검색식 한도)에서 재사용.
 */
@Service
public class EntitlementsService {

    private final UserRepository userRepository;
    private final ScannerRuleRepository scannerRuleRepository;
    private final AlertRepository alertRepository;

    public EntitlementsService(UserRepository userRepository,
                               ScannerRuleRepository scannerRuleRepository,
                               AlertRepository alertRepository) {
        this.userRepository = userRepository;
        this.scannerRuleRepository = scannerRuleRepository;
        this.alertRepository = alertRepository;
    }

    @Transactional(readOnly = true)
    public String tierOf(Long userId) {
        return Entitlements.normalize(
                userRepository.findById(userId).map(User::getTier).orElse(Entitlements.FREE));
    }

    @Transactional(readOnly = true)
    public Status entitlements(Long userId) {
        int scannerUsed = (int) scannerRuleRepository.countByUserId(userId);
        int alertUsed = (int) alertRepository.countByUserId(userId);
        return Entitlements.forTier(tierOf(userId), scannerUsed, alertUsed);
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
