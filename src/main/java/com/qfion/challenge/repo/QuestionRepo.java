package com.qfion.challenge.repo;

import com.qfion.challenge.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuestionRepo extends JpaRepository<Question, Long> {
    List<Question> findByIsActiveTrueOrderByIdAsc();
    Optional<Question> findBySlug(String slug);
    Optional<Question> findBySlugAndIsActiveTrue(String slug);
}
