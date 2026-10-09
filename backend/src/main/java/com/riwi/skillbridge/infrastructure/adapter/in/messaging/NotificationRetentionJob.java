package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.in.PurgeExpiredNotificationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);

    private final PurgeExpiredNotificationsUseCase purgeExpiredNotificationsUseCase;

    public NotificationRetentionJob(PurgeExpiredNotificationsUseCase purgeExpiredNotificationsUseCase) {
        this.purgeExpiredNotificationsUseCase = purgeExpiredNotificationsUseCase;
    }

    @Scheduled(cron = "0 15 2 * * *", zone = "UTC")
    public void purgeExpiredNotifications() {
        int deleted = purgeExpiredNotificationsUseCase.purgeExpired();
        if (deleted > 0) {
            log.info("Expired notification records purged; count={}", deleted);
        }
    }
}
