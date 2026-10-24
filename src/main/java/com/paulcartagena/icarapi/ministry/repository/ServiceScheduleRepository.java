package com.paulcartagena.icarapi.ministry.repository;

import com.paulcartagena.icarapi.ministry.entity.ServiceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceScheduleRepository extends JpaRepository<ServiceSchedule, UUID> {

    List<ServiceSchedule> findByMinistryId(UUID ministryId);

    // Scoped to the ministry so /ministries/{id}/schedules/{scheduleId} can't touch another ministry's schedule
    Optional<ServiceSchedule> findByIdAndMinistryId(UUID id, UUID ministryId);

    // Same ministry, day and time = duplicated slot (V14 uq_service_schedule_slot)
    boolean existsByMinistryIdAndDayOfWeekAndStartTime(UUID ministryId, DayOfWeek dayOfWeek, LocalTime startTime);

    boolean existsByMinistryIdAndDayOfWeekAndStartTimeAndIdNot(UUID ministryId, DayOfWeek dayOfWeek,
                                                               LocalTime startTime, UUID id);
}
