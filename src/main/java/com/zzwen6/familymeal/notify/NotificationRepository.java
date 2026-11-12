package com.zzwen6.familymeal.notify;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByFamilyIdOrderByIdDesc(Long familyId, Pageable page);
    List<Notification> findByGatheringIdOrderByIdDesc(Long gatheringId, Pageable page);
}
