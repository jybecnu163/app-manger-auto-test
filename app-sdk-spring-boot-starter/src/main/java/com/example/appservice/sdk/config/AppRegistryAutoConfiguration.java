package com.example.appservice.sdk.config;

import com.example.appservice.sdk.logging.ControllerLogAspect;
import com.example.appservice.sdk.record.*;
import com.example.appservice.sdk.registry.*;
import com.example.appservice.sdk.replay.*;
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

    /**
     * 绑定 app.registry.* 配置并注册配置 Bean。
     */
    @Bean
    @ConfigurationProperties(prefix = "app.registry")
    public AppRegistryProperties appRegistryProperties() {
        return new AppRegistryProperties();
    }

    /**
     * 注册本地凭据存储 Bean。
     */
    @Bean
    public AppIdFileStore appIdFileStore() {
        return new AppIdFileStore();
    }

    /**
     * 按配置注册 Controller 日志切面。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.registry", name = "controller-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public ControllerLogAspect controllerLogAspect() {
        return new ControllerLogAspect();
    }

    /**
     * 注册注册与心跳 HTTP 客户端 Bean。
     */
    @Bean
    public AppRegistryClient appRegistryClient(AppRegistryProperties properties) {
        return new AppRegistryClient(properties.getServerUrl());
    }

    /**
     * 注册 Spring MVC 接口扫描器 Bean。
     */
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
        // YAML 仅作为首次启动初始值，运行期状态由管理端或心跳响应同步。
        return new TrafficRecordSwitch(properties.getTrafficRecord().isEnabled(), null);
    }

    /**
     * 注册启动注册与定时心跳调度器 Bean。
     */
    @Bean
    public HeartbeatScheduler heartbeatScheduler(AppRegistryProperties properties,
                                                 AppRegistryClient client,
                                                 InterfaceScanner scanner,
                                                 AppIdFileStore fileStore,
                                                 Environment environment,
                                                 TrafficRecordSwitch recorderSwitch) {
        return new HeartbeatScheduler(properties, client, scanner, fileStore, environment, recorderSwitch);
    }

    /**
     * 注册 appId/token 解析器 Bean。
     */
    @Bean
    public AppIdentityResolver appIdentityResolver(AppRegistryProperties properties,
                                                   AppIdFileStore fileStore) {
        return new AppIdentityResolver(properties, fileStore);
    }

    /**
     * 注册录制开关下发控制器 Bean。
     */
    @Bean
    public TrafficRecordSwitchController trafficRecordSwitchController(AppIdentityResolver identity,
                                                                       TrafficRecordSwitch recorderSwitch) {
        return new TrafficRecordSwitchController(identity, recorderSwitch);
    }

    /**
     * 注册入站流量缓冲队列 Bean。
     */
    @Bean
    public TrafficRecordBuffer trafficRecordBuffer(AppRegistryProperties properties) {
        return new TrafficRecordBuffer(properties.getTrafficRecord().getQueueSize());
    }

    /**
     * 注册入站流量采集过滤器 Bean。
     */
    @Bean
    public TrafficCaptureFilter trafficCaptureFilter(AppRegistryProperties properties,
                                                     TrafficRecordBuffer buffer,
                                                     AppIdentityResolver identity,
                                                     TrafficRecordSwitch recorderSwitch) {
        return new TrafficCaptureFilter(properties, buffer, identity, recorderSwitch);
    }

    /**
     * 注册出站调用缓冲队列 Bean。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.registry.external-call-record", name = "enabled", havingValue = "true")
    public ExternalCallRecordBuffer externalCallRecordBuffer(AppRegistryProperties properties) {
        return new ExternalCallRecordBuffer(properties.getTrafficRecord().getQueueSize());
    }

    /**
     * 注册出站调用录制拦截器 Bean。
     */
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
    // 录制拦截器使用最低优先级，避免抢占回放 Mock 拦截。
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    public org.springframework.boot.web.client.RestTemplateCustomizer recordingRestTemplateCustomizer(
            OutboundHttpRecordingInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    /**
     * 注册流量批量上报器 Bean。
     */
    @Bean
    public TrafficReporter trafficReporter(AppRegistryProperties properties,
                                           ObjectProvider<TrafficRecordBuffer> trafficBuffer,
                                           ObjectProvider<ExternalCallRecordBuffer> externalCallBuffer,
                                           AppIdentityResolver identity) {
        return new TrafficReporter(properties, trafficBuffer, externalCallBuffer, identity);
    }

    /**
     * 注册回放 nonce 去重缓存 Bean。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public NonceCache replayNonceCache(AppRegistryProperties properties) {
        long ttl = properties.getReplay().getMaxTimestampSkew().toMillis() * 2;
        return new NonceCache(ttl, 10000);
    }

    /**
     * 注册 Mock 规则缓存 Bean。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public MockResponseStore mockResponseStore(AppRegistryProperties properties,
                                               AppIdentityResolver identity) {
        return new MockResponseStore(properties, identity);
    }

    /**
     * 注册回放态 Mock 拦截器 Bean。
     */
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
    // 回放拦截器使用最高优先级，确保先于录制拦截器接管 RestTemplate。
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
    public org.springframework.boot.web.client.RestTemplateCustomizer replayRestTemplateCustomizer(
            ReplayRestTemplateInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    /**
     * 注册 Dry-Run 回放入口过滤器 Bean。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public ReplayFilter replayFilter(AppRegistryProperties properties, NonceCache nonceCache,
                                     org.springframework.beans.factory.ObjectProvider<
                                             org.springframework.transaction.PlatformTransactionManager> managers,
                                     MockResponseStore mockStore) {
        return new ReplayFilter(properties, nonceCache, managers, mockStore);
    }

    /**
     * 注册 REQUIRES_NEW 风险扫描器 Bean。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.registry.replay", name = "enabled", havingValue = "true")
    public RequiresNewDetector requiresNewDetector(org.springframework.context.ApplicationContext context) {
        return new RequiresNewDetector(context);
    }

    /**
     * 创建回放上下文传播装饰器。
     */
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
            /**
             * 执行 post process before initialization 相关逻辑。
             */
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
