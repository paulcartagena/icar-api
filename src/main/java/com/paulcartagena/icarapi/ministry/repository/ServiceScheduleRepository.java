package com.paulcartagena.icarapi.ministry.repository;

import com.paulcartagena.icarapi.ministry.entity.ServiceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceScheduleRepository extends JpaRepository<ServiceSchedule, UUID> {

    List<ServiceSchedule> findByMinistryId(UUID ministryId);
}
