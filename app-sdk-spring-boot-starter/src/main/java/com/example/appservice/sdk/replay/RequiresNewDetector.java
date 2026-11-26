package com.example.appservice.sdk.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 启动后扫描 REQUIRES_NEW 事务方法并告警，提示 Dry-Run 可能无法回滚。
 */
public class RequiresNewDetector {

    private static final Logger log = LoggerFactory.getLogger(RequiresNewDetector.class);

    private final ApplicationContext applicationContext;

    /**
     * 构造 RequiresNewDetector 实例。
     */
    public RequiresNewDetector(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 扫描 Spring Bean 方法并输出风险告警。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void detect() {
        Set<String> reported = new HashSet<>();
        for (String name : applicationContext.getBeanDefinitionNames()) {
            try {
                Object bean = applicationContext.getBean(name);
                Class<?> type = ClassUtils.getUserClass(bean);
                for (Method method : type.getMethods()) {
                    Transactional tx = AnnotationUtils.findAnnotation(method, Transactional.class);
                    // REQUIRES_NEW 会开启独立事务，Dry-Run 外层回滚无法覆盖其中的写入。
                    if (tx == null || tx.propagation() != Propagation.REQUIRES_NEW) {
                        continue;
                    }
                    String key = type.getName() + "#" + method.getName();
                    if (reported.add(key)) {
                        log.warn("Dry-Run 风险：{} 使用 @Transactional(REQUIRES_NEW)，其数据库写入不会被回放事务回滚", key);
                    }
                }
            } catch (Exception ignored) {
                // 部分 Bean 延迟初始化或不可达，跳过即可
            }
        }
    }
}
