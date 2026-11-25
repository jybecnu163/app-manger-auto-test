package com.example.appservice.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.appservice.common.dto.HeartbeatRequest;
import com.example.appservice.common.dto.InstanceInfoDTO;
import com.example.appservice.common.dto.InterfaceMetaDTO;
import com.example.appservice.common.dto.RegisterRequest;
import com.example.appservice.common.dto.RegisterResponse;
import com.example.appservice.server.entity.ApiInterface;
import com.example.appservice.server.entity.App;
import com.example.appservice.server.entity.AppInstance;
import com.example.appservice.server.mapper.ApiInterfaceMapper;
import com.example.appservice.server.mapper.AppInstanceMapper;
import com.example.appservice.server.mapper.AppMapper;
import com.example.appservice.server.vo.AppDetailVO;
import com.example.appservice.server.vo.AppListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppService {

    private static final long OFFLINE_THRESHOLD_SECONDS = 90;

    private final AppMapper appMapper;
    private final AppInstanceMapper instanceMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final TestcaseService testcaseService;

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        App app = null;
        if (StringUtils.hasText(req.getAppId())) {
            app = appMapper.selectOne(new LambdaQueryWrapper<App>()
                    .eq(App::getAppId, req.getAppId())
                    .last("limit 1"));
        }
        if (app == null) {
            app = appMapper.selectOne(new LambdaQueryWrapper<App>()
                    .eq(App::getName, req.getSystemName())
                    .eq(App::getEnv, req.getEnv())
                    .last("limit 1"));
        }
        if (app == null) {
            app = new App();
            app.setAppId(StringUtils.hasText(req.getAppId())
                    ? req.getAppId()
                    : "APP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            app.setName(req.getSystemName());
            app.setEnv(req.getEnv());
            app.setToken(StringUtils.hasText(req.getToken())
                    ? req.getToken()
                    : UUID.randomUUID().toString().replace("-", ""));
            LocalDateTime now = LocalDateTime.now();
            app.setCreatedAt(now);
            app.setUpdatedAt(now);
            appMapper.insert(app);
        } else if (StringUtils.hasText(req.getAppId()) && !req.getAppId().equals(app.getAppId())) {
            String oldAppId = app.getAppId();
            app.setAppId(req.getAppId());
            if (StringUtils.hasText(req.getToken())) {
                app.setToken(req.getToken());
            }
            app.setUpdatedAt(LocalDateTime.now());
            appMapper.updateById(app);
            instanceMapper.delete(new LambdaQueryWrapper<AppInstance>().eq(AppInstance::getAppId, oldAppId));
            interfaceMapper.delete(new LambdaQueryWrapper<ApiInterface>().eq(ApiInterface::getAppId, oldAppId));
        } else if (StringUtils.hasText(req.getToken()) && !req.getToken().equals(app.getToken())) {
            app.setToken(req.getToken());
            app.setUpdatedAt(LocalDateTime.now());
            appMapper.updateById(app);
        }

        RegisterResponse response = new RegisterResponse();
        response.setAppId(app.getAppId());
        response.setToken(app.getToken());
        return response;
    }

    public App authenticate(String appId, String token) {
        App app = appMapper.selectOne(new LambdaQueryWrapper<App>()
                .eq(App::getAppId, appId)
                .last("limit 1"));
        if (app == null) {
            throw new IllegalArgumentException("app not found: " + appId);
        }
        if (!app.getToken().equals(token)) {
            throw new IllegalArgumentException("invalid token for app: " + appId);
        }
        return app;
    }

    @Transactional
    public void heartbeat(HeartbeatRequest req) {
        App app = appMapper.selectOne(new LambdaQueryWrapper<App>()
                .eq(App::getAppId, req.getAppId())
                .last("limit 1"));
        if (app == null) {
            throw new IllegalArgumentException("app not found: " + req.getAppId());
        }
        if (!app.getToken().equals(req.getToken())) {
            throw new IllegalArgumentException("invalid token for app: " + req.getAppId());
        }

        InstanceInfoDTO info = req.getInstance();
        if (info != null) {
            AppInstance instance = new AppInstance();
            instance.setAppId(req.getAppId());
            instance.setInstanceKey(info.getInstanceKey());
            instance.setIp(info.getIp());
            instance.setPort(info.getPort());
            instance.setHostname(info.getHostname());
            instance.setStatus(req.getStatus() == null ? "UP" : req.getStatus());
            instance.setLastHeartbeatAt(LocalDateTime.now());
            instance.setStartTime(info.getStartTime());
            instanceMapper.upsert(instance);
        }

        if (!Objects.equals(req.getInterfacesHash(), app.getInterfacesHash())) {
            testcaseService.deleteByAppId(req.getAppId());
            interfaceMapper.delete(new LambdaQueryWrapper<ApiInterface>()
                    .eq(ApiInterface::getAppId, req.getAppId()));
            if (req.getInterfaces() != null) {
                for (InterfaceMetaDTO meta : req.getInterfaces()) {
                    ApiInterface entity = new ApiInterface();
                    entity.setAppId(req.getAppId());
                    entity.setMethod(meta.getMethod());
                    entity.setPath(meta.getPath());
                    entity.setController(meta.getController());
                    entity.setOperation(meta.getOperation());
                    entity.setRequestSchema(meta.getRequestSchema());
                    entity.setResponseSchema(meta.getResponseSchema());
                    entity.setSchemaHash(meta.getSchemaHash());
                    entity.setUpdatedAt(LocalDateTime.now());
                    interfaceMapper.insert(entity);
                    testcaseService.generateAutoCases(req.getAppId(), entity);
                }
            }
            app.setInterfacesHash(req.getInterfacesHash());
            app.setUpdatedAt(LocalDateTime.now());
            appMapper.updateById(app);
        }
    }

    public List<AppListItemVO> listApps() {
        List<AppListItemVO> result = new ArrayList<>();
        for (App app : appMapper.selectList(null)) {
            List<AppInstance> instances = refreshInstances(app.getAppId());
            AppListItemVO item = new AppListItemVO();
            item.setAppId(app.getAppId());
            item.setName(app.getName());
            item.setEnv(app.getEnv());
            item.setTotalInstances(instances.size());
            item.setOnlineInstances(instances.stream().filter(i -> "UP".equals(i.getStatus())).count());
            Long count = interfaceMapper.selectCount(new LambdaQueryWrapper<ApiInterface>()
                    .eq(ApiInterface::getAppId, app.getAppId()));
            item.setInterfaceCount(count == null ? 0 : count);
            item.setLastHeartbeatAt(instances.stream()
                    .map(AppInstance::getLastHeartbeatAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
            result.add(item);
        }
        return result;
    }

    public AppDetailVO detail(String appId) {
        App app = appMapper.selectOne(new LambdaQueryWrapper<App>()
                .eq(App::getAppId, appId)
                .last("limit 1"));
        if (app == null) {
            throw new IllegalArgumentException("app not found: " + appId);
        }
        AppDetailVO vo = new AppDetailVO();
        vo.setAppId(app.getAppId());
        vo.setName(app.getName());
        vo.setEnv(app.getEnv());
        vo.setToken(app.getToken());
        Long count = interfaceMapper.selectCount(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getAppId, appId));
        vo.setInterfaceCount(count == null ? 0 : count);
        for (AppInstance instance : refreshInstances(appId)) {
            if ("UP".equals(instance.getStatus())) {
                vo.getInstances().add(toInstanceDTO(instance));
            }
        }
        return vo;
    }

    public List<ApiInterface> listInterfaces(String appId) {
        return interfaceMapper.selectList(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getAppId, appId)
                .orderByAsc(ApiInterface::getController)
                .orderByAsc(ApiInterface::getPath)
                .orderByAsc(ApiInterface::getMethod));
    }

    public AppInstance findOnlineInstance(String appId) {
        refreshInstances(appId);
        return instanceMapper.selectOne(new LambdaQueryWrapper<AppInstance>()
                .eq(AppInstance::getAppId, appId)
                .eq(AppInstance::getStatus, "UP")
                .orderByDesc(AppInstance::getLastHeartbeatAt)
                .last("limit 1"));
    }

    private List<AppInstance> refreshInstances(String appId) {
        List<AppInstance> instances = instanceMapper.selectList(new LambdaQueryWrapper<AppInstance>()
                .eq(AppInstance::getAppId, appId));
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(OFFLINE_THRESHOLD_SECONDS);
        for (AppInstance instance : instances) {
            if ("UP".equals(instance.getStatus())
                    && (instance.getLastHeartbeatAt() == null || instance.getLastHeartbeatAt().isBefore(threshold))) {
                instance.setStatus("OFFLINE");
                instanceMapper.updateById(instance);
            }
        }
        return instances;
    }

    private InstanceInfoDTO toInstanceDTO(AppInstance instance) {
        InstanceInfoDTO dto = new InstanceInfoDTO();
        dto.setInstanceKey(instance.getInstanceKey());
        dto.setIp(instance.getIp());
        dto.setPort(instance.getPort());
        dto.setHostname(instance.getHostname());
        dto.setStartTime(instance.getStartTime());
        return dto;
    }
}
