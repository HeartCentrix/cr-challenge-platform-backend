package com.qfion.challenge.repo;

import com.qfion.challenge.entity.Testcase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TestcaseRepo extends JpaRepository<Testcase, Long> {
    List<Testcase> findByQuestionIdOrderByOrdinalAsc(Long questionId);
    List<Testcase> findByQuestionIdAndIsSampleTrueOrderByOrdinalAsc(Long questionId);
    long countByQuestionId(Long questionId);
}
