package com.riwi.skillbridge.application.jobs;

import com.riwi.skillbridge.application.service.MeetingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantiene el ciclo de vida de las reuniones sin depender del navegador:
 *  - Reuniones PROGRAMADA cuya ventana de inicio expiró → NO_REALIZADA (Regla 5).
 *  - Reuniones EN_CURSO que alcanzaron su límite máximo → cierre controlado con
 *    cierre de sala y revocación del acceso (Regla 4).
 */
@Component
public class MeetingStatusJob {

    private static final Logger log = LoggerFactory.getLogger(MeetingStatusJob.class);
    private final MeetingService meetingService;

    public MeetingStatusJob(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    // Cada minuto: reconciliación de estados de reuniones
    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void run() {
        int expired = meetingService.markExpiredMeetings();
        int closed = meetingService.forceCloseTimedOutMeetings();
        if (expired > 0) log.info("Auto-NO_REALIZADA: {} reuniones marcadas", expired);
        if (closed > 0) log.info("Cierre por límite: {} reuniones cerradas", closed);
    }
}
