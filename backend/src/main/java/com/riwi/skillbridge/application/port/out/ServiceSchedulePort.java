package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.ServiceSchedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceSchedulePort {

    ServiceSchedule save(ServiceSchedule serviceSchedule);

    Optional<ServiceSchedule> findById(UUID id);

    List<ServiceSchedule> findByServiceId(UUID serviceId);

    void deleteById(UUID id);
}
