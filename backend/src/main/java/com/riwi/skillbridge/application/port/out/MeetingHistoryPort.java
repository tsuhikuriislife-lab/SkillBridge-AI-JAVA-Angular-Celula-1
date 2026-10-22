package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.MeetingHistoryEntry;

import java.util.List;
import java.util.UUID;

public interface MeetingHistoryPort {

    MeetingHistoryEntry save(MeetingHistoryEntry entry);

    List<MeetingHistoryEntry> findByMeetingId(UUID meetingId);
}
