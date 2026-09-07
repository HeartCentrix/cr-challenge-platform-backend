package com.qfion.challenge.controller;

import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.entity.Question;
import com.qfion.challenge.repo.QuestionRepo;
import com.qfion.challenge.repo.TestcaseRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionRepo questionRepo;
    private final TestcaseRepo testcaseRepo;

    @GetMapping
    public List<Dto.QuestionSummary> active() {
        return questionRepo.findByIsActiveTrueOrderByIdAsc().stream()
                .map(q -> new Dto.QuestionSummary(q.getId(), q.getSlug(), q.getTitle(),
                        q.getDifficulty(), q.getLanguage(), q.getTimeLimitSeconds()))
                .toList();
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Dto.QuestionDetail> detail(@PathVariable String slug) {
        return questionRepo.findBySlugAndIsActiveTrue(slug)
                .map(this::toDetail)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Dto.QuestionDetail toDetail(Question q) {
        List<Dto.SampleTestcase> samples = testcaseRepo
                .findByQuestionIdAndIsSampleTrueOrderByOrdinalAsc(q.getId()).stream()
                .map(t -> new Dto.SampleTestcase(t.getStdin(), t.getExpectedOutput()))
                .toList();
        // Hidden test cases and the reference solution are deliberately never returned.
        return new Dto.QuestionDetail(q.getId(), q.getSlug(), q.getTitle(), q.getPrompt(), q.getDifficulty(),
                q.getLanguage(), q.getJudgeLanguageId(), q.getStarterCode(), q.getTimeLimitSeconds(), samples);
    }
}
