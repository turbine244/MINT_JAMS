package com.mintjams.social_insight.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FlaskRequestService {
    private final FlaskQueueService taskQueueService;

    // 의존성 주입
    public FlaskRequestService(FlaskQueueService taskQueueService) {
        this.taskQueueService = taskQueueService;
    }

    // 주기적으로 큐에서 작업을 처리
    @Scheduled(fixedRate = 5000) // 5초마다 실행
    public void processTasks() {
        if (taskQueueService.hasTasks()) {
            Runnable task = taskQueueService.getTask();
            task.run(); // 작업 실행
        } else {
            //System.out.println("큐에 처리할 작업이 없습니다.");
        }
    }
}
