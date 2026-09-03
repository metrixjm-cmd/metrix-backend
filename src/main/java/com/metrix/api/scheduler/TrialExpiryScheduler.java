package com.metrix.api.scheduler;

import com.metrix.api.platform.service.PlatformAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrialExpiryScheduler {

    private final PlatformAdminService platformAdminService;

    @Scheduled(cron = "0 15 * * * *")
    public void expireTrials() {
        int expired = platformAdminService.expireDueTrials();
        if (expired > 0) {
            log.info("[Trial] {} instancias suspendidas por prueba vencida", expired);
        }
    }
}
