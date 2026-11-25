package com.example.appservice.sdk;

import org.springframework.core.task.TaskDecorator;

public class ReplayTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        ReplayInfo info = ReplayContext.get();
        boolean replay = ReplayContext.isReplay();
        return () -> {
            try {
                if (info != null) {
                    ReplayContext.set(info);
                } else {
                    ReplayContext.setReplay(replay);
                }
                runnable.run();
            } finally {
                ReplayContext.clear();
            }
        };
    }
}
