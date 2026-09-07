package com.qfion.challenge.repo;

import com.qfion.challenge.entity.DailyAttemptLock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface DailyAttemptLockRepo extends JpaRepository<DailyAttemptLock, Long> {
    boolean existsByIdentityTypeAndIdentityHashAndAttemptDate(String identityType, String identityHash, LocalDate attemptDate);
}
