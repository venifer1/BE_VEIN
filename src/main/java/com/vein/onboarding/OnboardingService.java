package com.vein.onboarding;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.alert.AlertRepository;
import com.vein.condition.ScannerRuleRepository;
import com.vein.onboarding.OnboardingDto.Status;
import com.vein.paper.PaperAccountRepository;
import com.vein.user.UserRepository;
import com.vein.watchlist.WatchlistItemRepository;
import com.vein.watchlist.WatchlistRepository;

/**
 * 온보딩 상태 조립 + 닫힘 처리 (Track B #2). 스텝 완료 여부는 각 기능의 실제 데이터 존재
 * 여부에서 파생한다(저장하지 않음). 닫힘만 사용자 행에 기록한다.
 */
@Service
public class OnboardingService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final AlertRepository alertRepository;
    private final PaperAccountRepository paperAccountRepository;
    private final ScannerRuleRepository scannerRuleRepository;
    private final UserRepository userRepository;

    public OnboardingService(WatchlistRepository watchlistRepository,
                             WatchlistItemRepository watchlistItemRepository,
                             AlertRepository alertRepository,
                             PaperAccountRepository paperAccountRepository,
                             ScannerRuleRepository scannerRuleRepository,
                             UserRepository userRepository) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.alertRepository = alertRepository;
        this.paperAccountRepository = paperAccountRepository;
        this.scannerRuleRepository = scannerRuleRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Status status(Long userId) {
        boolean hasWatchlistItem = watchlistRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(w -> watchlistItemRepository.countByIdWatchlistId(w.getId()) > 0)
                .orElse(false);
        boolean hasAlert = alertRepository.existsByUserId(userId);
        boolean hasPaper = paperAccountRepository.existsByUserId(userId);
        boolean hasScanner = scannerRuleRepository.existsByUserId(userId);
        boolean dismissed = userRepository.findById(userId)
                .map(u -> u.getOnboardingDismissedAt() != null)
                .orElse(false);
        return OnboardingSteps.build(hasWatchlistItem, hasAlert, hasPaper, hasScanner, dismissed);
    }

    @Transactional
    public void dismiss(Long userId) {
        userRepository.findById(userId).ifPresent(u -> {
            u.dismissOnboarding(Instant.now());
            userRepository.save(u);
        });
    }
}
