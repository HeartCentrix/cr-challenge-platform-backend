package com.qfion.challenge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="challenge.sessions.workers-enabled",havingValue="true",matchIfMissing=true)
public class ChallengeSessionWorker {
    private final ChallengeSessionService sessions;
    private final SubmissionService submissions;
    @Scheduled(initialDelay=10000,fixedDelay=1000) public void expire() {
        try { for (int i=0;i<10 && sessions.expireOne();i++) { /* bounded expiry batch */ } }
        catch (RuntimeException error) { log.warn("Challenge session expiry will be retried."); }
    }
    @Scheduled(initialDelay=10000,fixedDelay=1000) public void grade() {
        try { submissions.gradeNextQueued(); }
        catch (RuntimeException error) { log.warn("Queued challenge grading will be retried."); }
    }
}
