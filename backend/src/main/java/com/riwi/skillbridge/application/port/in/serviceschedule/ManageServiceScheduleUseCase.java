package com.riwi.skillbridge.application.port.in.serviceschedule;

import com.riwi.skillbridge.domain.model.ServiceSchedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManageServiceScheduleUseCase {
    ServiceSchedule createSchedule(ServiceSchedule serviceSchedule);
    Optional<ServiceSchedule> getScheduleById(UUID id);
    List<ServiceSchedule> getSchedulesByService(UUID serviceId);
    Optional<ServiceSchedule> updateSchedule(UUID id, ServiceSchedule serviceSchedule);
    boolean deleteSchedule(UUID id);
}
