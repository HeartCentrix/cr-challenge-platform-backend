package com.qfion.challenge.repo;

import com.qfion.challenge.entity.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttemptRepo extends JpaRepository<Attempt, Long> {

    @Query(value = "SELECT count(*) FROM challenge_platform.attempt WHERE submitted_at >= now() - make_interval(days => :days)", nativeQuery = true)
    long countSince(int days);

    @Query(value = "SELECT coalesce(avg(score), 0) FROM challenge_platform.attempt", nativeQuery = true)
    Double averageScore();
}
