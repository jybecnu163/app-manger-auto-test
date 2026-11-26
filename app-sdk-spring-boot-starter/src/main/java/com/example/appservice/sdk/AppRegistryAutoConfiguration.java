package com.example.appservice.sdk;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * SDK 自动装配总入口，按配置条件注册注册、录制、回放和 Mock 相关 Bean。
 */
@AutoConfiguration
@EnableScheduling
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "app.registry", name = "server-url")
public class AppRegistryAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "app.registry")
    public AppRegistryProperties appRegistryProperties() {
        return new AppRegistryProperties();
    }

    @Bean
    public AppIdFileStore appIdFileStore() {
        return new AppIdFileStore();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry", name = "controller-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public ControllerLogAspect controllerLogAspect() {
        return new ControllerLogAspect();
    }

    @Bean
    public AppRegistryClient appRegistryClient(AppRegistryProperties properties) {
        return new AppRegistryClient(properties.getServerUrl());
    }

    @Bean
    public InterfaceScanner interfaceScanner(ObjectProvider<RequestMappingHandlerMapping> mappingProvider,
                                              AppRegistryProperties properties) {
        return new InterfaceScanner(mappingProvider, properties);
    }

    /**
     * 创建初始录制开关，真实状态后续由管理端或心跳响应同步。
     */
    @Bean
    public TrafficRecordSwitch trafficRecordSwitch(AppRegistryProperties properties) {
        return new TrafficRecordSwitch(properties.getTrafficRecord().isEnabled(), null);
    }

    @Bean
    public HeartbeatScheduler heartbeatScheduler(AppRegistryProperties properties,
                                                 AppRegistryClient client,
                                                 InterfaceScanner scanner,
                                                 AppIdFileStore fileStore,
                                                 Environment environment,
                                                 TrafficRecordSwitch recorderSwitch) {
        return new HeartbeatScheduler(properties, client, scanner, fileStore, environment, recorderSwitch);
    }

    @Bean
    public AppIdentityResolver appIdentityResolver(AppRegistryProperties properties,
                                                   AppIdFileStore fileStore) {
        return new AppIdentityResolver(properties, fileStore);
    }

    @Bean
    public TrafficRecordSwitchController trafficRecordSwitchController(AppIdentityResolver identity,
                                                                       TrafficRecordSwitch recorderSwitch) {
        return new TrafficRecordSwitchController(identity, recorderSwitch);
    }

    @Bean
    public TrafficRecordBuffer trafficRecordBuffer(AppRegistryProperties properties) {
        return new TrafficRecordBuffer(properties.getTrafficRecord().getQueueSize());
    }

    @Bean
    public TrafficCaptureFilter trafficCaptureFilter(AppRegistryProperties properties,
                                                     TrafficRecordBuffer buffer,
                                                     AppIdentityResolver identity,
                                                     TrafficRecordSwitch recorderSwitch) {
        return new TrafficCaptureFilter(properties, buffer, identity, recorderSwitch);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.external-call-record", name = "enabled", havingValue = "true")
    public ExternalCallRecordBuffer externalCallRecordBuffer(AppRegistryProperties properties) {
        return new ExternalCallRecordBuffer(properties.getTrafficRecord().getQueueSize());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.external-call-record", name = "enabled", havingValue = "true")
    public OutboundHttpRecordingInterceptor outboundHttpRecordingInterceptor(
            AppRegistryProperties properties, ExternalCallRecordBuffer buffer, AppIdentityResolver identity) {
        return new OutboundHttpRecordingInterceptor(properties, buffer, identity);
    }

    /**
     * 为普通 RestTemplate 添加出站调用录制拦截器。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.registry.external-call-record", name = "enabled", havingValue = "true")
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    public org.springframework.boot.web.client.RestTemplateCustomizer recordingRestTemplateCustomizer(
            OutboundHttpRecordingInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    @Bean
    public TrafficReporter trafficReporter(AppRegistryProperties properties,
                                           ObjectProvider<TrafficRecordBuffer> trafficBuffer,
                                           ObjectProvider<ExternalCallRecordBuffer> externalCallBuffer,
                                           AppIdentityResolver identity) {
        return new TrafficReporter(properties, trafficBuffer, externalCallBuffer, identity);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public NonceCache replayNonceCache(AppRegistryProperties properties) {
        long ttl = properties.getReplay().getMaxTimestampSkew().toMillis() * 2;
        return new NonceCache(ttl, 10000);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public MockResponseStore mockResponseStore(AppRegistryProperties properties,
                                               AppIdentityResolver identity) {
        return new MockResponseStore(properties, identity);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public ReplayRestTemplateInterceptor replayRestTemplateInterceptor(
            AppRegistryProperties properties, MockResponseStore store, AppIdentityResolver identity) {
        return new ReplayRestTemplateInterceptor(properties, store, identity);
    }

    /**
     * 为回放 RestTemplate 添加 Mock 拦截，优先级高于录制拦截器。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
    public org.springframework.boot.web.client.RestTemplateCustomizer replayRestTemplateCustomizer(
            ReplayRestTemplateInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public ReplayFilter replayFilter(AppRegistryProperties properties, NonceCache nonceCache,
                                     org.springframework.beans.factory.ObjectProvider<
                                             org.springframework.transaction.PlatformTransactionManager> managers,
                                     MockResponseStore mockStore) {
        return new ReplayFilter(properties, nonceCache, managers, mockStore);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public RequiresNewDetector requiresNewDetector(org.springframework.context.ApplicationContext context) {
        return new RequiresNewDetector(context);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public org.springframework.core.task.TaskDecorator replayTaskDecorator() {
        return new ReplayTaskDecorator();
    }

    /**
     * 为线程池设置回放上下文传播装饰器。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public static org.springframework.beans.factory.config.BeanPostProcessor replayTaskDecoratorPostProcessor(
            org.springframework.core.task.TaskDecorator replayTaskDecorator) {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor) {
                    executor.setTaskDecorator(replayTaskDecorator);
                }
                return bean;
            }
        };
    }
}
