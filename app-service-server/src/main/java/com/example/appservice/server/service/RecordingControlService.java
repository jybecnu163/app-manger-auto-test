package com.example.appservice.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.appservice.common.dto.Result;
import com.example.appservice.common.dto.TrafficRecordSwitchRequest;
import com.example.appservice.server.entity.App;
import com.example.appservice.server.entity.AppInstance;
import com.example.appservice.server.mapper.AppInstanceMapper;
import com.example.appservice.server.mapper.AppMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecordingControlService {

    private static final Logger log = LoggerFactory.getLogger(RecordingControlService.class);

    private final AppMapper appMapper;
    private final AppInstanceMapper instanceMapper;
    private final RestTemplate restTemplate = createRestTemplate();

    public void setEnabled(String appId, boolean enabled, LocalDateTime startAt) {
        App app = appMapper.selectOne(new LambdaQueryWrapper<App>()
                .eq(App::getAppId, appId)
                .last("limit 1"));
        if (app == null) {
            throw new IllegalArgumentException("app not found: " + appId);
        }
        appMapper.update(null, new LambdaUpdateWrapper<App>()
                .eq(App::getAppId, appId)
                .set(App::getTrafficRecordEnabled, enabled)
                .set(App::getTrafficRecordStartAt, enabled ? startAt : null)
                .set(App::getUpdatedAt, LocalDateTime.now()));

        List<AppInstance> instances = instanceMapper.selectList(new LambdaQueryWrapper<AppInstance>()
                .eq(AppInstance::getAppId, appId)
                .eq(AppInstance::getStatus, "UP"));

        TrafficRecordSwitchRequest payload = new TrafficRecordSwitchRequest();
        payload.setAppId(appId);
        payload.setToken(app.getToken());
        payload.setEnabled(enabled);
        payload.setStartAt(enabled ? startAt : null);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (AppInstance instance : instances) {
            try {
                String url = "http://" + instance.getIp() + ":" + instance.getPort()
                        + "/api/agent/traffic-record/enabled";
                ResponseEntity<Result<Void>> response = restTemplate.exchange(url, HttpMethod.POST,
                        new HttpEntity<>(payload, headers),
                        new ParameterizedTypeReference<Result<Void>>() {
                        });
                Result<Void> body = response.getBody();
                if (body == null || body.getCode() != 0) {
                    log.warn("下发录制开关失败，instance={}:{}，响应={}",
                            instance.getIp(), instance.getPort(), body);
                }
            } catch (Exception e) {
                log.warn("下发录制开关失败，instance={}:{}", instance.getIp(), instance.getPort(), e);
            }
        }
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }
}
