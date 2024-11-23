package com.mintjams.social_insight.service;

import java.util.LinkedList;
import java.util.Queue;
import org.springframework.stereotype.Service;

@Service
public class FlaskQueueService {

    // 작업을 저장할 큐
    private final Queue<Runnable> taskQueue = new LinkedList<>();

    // 작업을 큐에 추가
    public void addTask(Runnable task) {
        taskQueue.offer(task);
    }

    // 큐에서 작업을 하나 가져옴
    public Runnable getTask() {
        return taskQueue.poll();
    }

    // 큐에 작업이 남아있는지 확인
    public boolean hasTasks() {
        return !taskQueue.isEmpty();
    }
}