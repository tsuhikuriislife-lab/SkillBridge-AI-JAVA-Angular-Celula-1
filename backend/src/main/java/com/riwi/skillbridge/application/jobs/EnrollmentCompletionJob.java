package com.riwi.skillbridge.application.jobs;

import com.riwi.skillbridge.application.service.ServiceEnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentCompletionJob.class);
    private final ServiceEnrollmentService enrollmentService;

    public EnrollmentCompletionJob(ServiceEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    // Todos los días a las 2:00 AM → inscripciones con endDate vencido pasan a COMPLETED
    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        int completed = enrollmentService.completeEndedEnrollments();
        if (completed > 0) {
            log.info("Auto-COMPLETED: {} inscripciones finalizadas", completed);
        }
    }
}
