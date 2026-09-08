package com.qfion.challenge.repo;

import com.qfion.challenge.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface QuestionRepo extends JpaRepository<Question, Long> {
    @Query(value = "SELECT * FROM challenge_platform.question WHERE is_active = true ORDER BY random() LIMIT 1",
            nativeQuery = true)
    Optional<Question> findRandomActive();
    Optional<Question> findBySlug(String slug);
    Optional<Question> findBySlugAndIsActiveTrue(String slug);
}
