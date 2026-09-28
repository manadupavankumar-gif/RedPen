package com.resumeai.repository;

import com.resumeai.model.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    List<Analysis> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Analysis> findByIdAndUserId(Long id, Long userId);
    Optional<Analysis> findFirstByUserIdAndInputHashOrderByCreatedAtDesc(Long userId, String inputHash);
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);
    Optional<Analysis> findByShareToken(String shareToken);
}
