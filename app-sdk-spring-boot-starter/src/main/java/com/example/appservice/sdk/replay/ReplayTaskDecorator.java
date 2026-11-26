package com.example.appservice.sdk.replay;
import com.example.appservice.sdk.context.ReplayContext;
import com.example.appservice.sdk.context.ReplayInfo;

import org.springframework.core.task.TaskDecorator;

/**
 * 线程池任务装饰器，将回放上下文传播到异步任务并在结束后清理。
 */
public class ReplayTaskDecorator implements TaskDecorator {

    /**
     * 包装 Runnable，复制回放上下文并在任务完成后清理。
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        // 复制回放上下文到异步任务，保证子线程中的外部调用仍能命中 Mock。
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
