package com.qfion.challenge.repo;

import com.qfion.challenge.entity.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/** Reads the leaderboard view. Bound to Attempt only because Spring Data needs an entity. */
public interface LeaderboardRepo extends JpaRepository<Attempt, Long> {

    // Use the saved name, not the view's email-derived fallback, for public display.
    @Query(value = "SELECT l.rank, c.full_name FROM challenge_platform.leaderboard l "
            + "JOIN challenge_platform.candidate c ON c.id = l.candidate_id ORDER BY l.rank ASC LIMIT :limit",
           nativeQuery = true)
    List<Object[]> topCandidates(int limit);
}
