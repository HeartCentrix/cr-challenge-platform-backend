package com.qfion.challenge.repo;

import com.qfion.challenge.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface QuestionRepo extends JpaRepository<Question, Long> {
    @Query(value = "SELECT q.* FROM challenge_platform.question q JOIN challenge_platform.followup_eligible_question e ON e.id=q.id ORDER BY random() LIMIT 1",
            nativeQuery = true)
    Optional<Question> findRandomActive();
    Optional<Question> findBySlug(String slug);
    // Retained for already-issued legacy rounds and historical submission processing.
    Optional<Question> findBySlugAndIsActiveTrue(String slug);
    @Query(value="SELECT q.* FROM challenge_platform.question q JOIN challenge_platform.followup_eligible_question e ON e.id=q.id WHERE q.slug=:slug",nativeQuery=true)
    Optional<Question> findEligibleBySlug(@org.springframework.data.repository.query.Param("slug") String slug);
}
