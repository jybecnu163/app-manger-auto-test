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

    @Bean
    public HeartbeatScheduler heartbeatScheduler(AppRegistryProperties properties,
                                                 AppRegistryClient client,
                                                 InterfaceScanner scanner,
                                                 AppIdFileStore fileStore,
                                                 Environment environment) {
        return new HeartbeatScheduler(properties, client, scanner, fileStore, environment);
    }

    @Bean
    public AppIdentityResolver appIdentityResolver(AppRegistryProperties properties,
                                                   AppIdFileStore fileStore) {
        return new AppIdentityResolver(properties, fileStore);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.traffic-record", name = "enabled", havingValue = "true")
    public TrafficRecordBuffer trafficRecordBuffer(AppRegistryProperties properties) {
        return new TrafficRecordBuffer(properties.getTrafficRecord().getQueueSize());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.registry.traffic-record", name = "enabled", havingValue = "true")
    public TrafficCaptureFilter trafficCaptureFilter(AppRegistryProperties properties,
                                                     TrafficRecordBuffer buffer,
                                                     AppIdentityResolver identity) {
        return new TrafficCaptureFilter(properties, buffer, identity);
    }
}
