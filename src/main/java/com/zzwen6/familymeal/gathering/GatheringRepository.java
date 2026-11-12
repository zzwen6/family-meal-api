package com.zzwen6.familymeal.gathering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GatheringRepository extends JpaRepository<Gathering, Long> {
    Optional<Gathering> findByInviteToken(String token);
}
