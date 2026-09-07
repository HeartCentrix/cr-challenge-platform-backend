package com.qfion.challenge.repo;

import com.qfion.challenge.entity.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/** Reads the leaderboard view. Bound to Attempt only because Spring Data needs an entity. */
public interface LeaderboardRepo extends JpaRepository<Attempt, Long> {

    @Query(value = "SELECT candidate_id, display_name, total_score, testcases_cleared, questions_solved, "
                 + "total_time_ms, rank FROM challenge_platform.leaderboard ORDER BY rank ASC LIMIT :limit",
           nativeQuery = true)
    List<Object[]> topCandidates(int limit);
}
