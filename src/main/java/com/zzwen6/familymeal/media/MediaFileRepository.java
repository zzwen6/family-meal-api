package com.zzwen6.familymeal.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {
    Optional<MediaFile> findByStoredName(String storedName);
    Optional<MediaFile> findByFamilyIdAndStoredName(Long familyId, String storedName);
}
