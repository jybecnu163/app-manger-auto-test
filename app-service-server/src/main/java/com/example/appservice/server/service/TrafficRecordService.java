package com.example.appservice.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.appservice.common.dto.TrafficRecordDTO;
import com.example.appservice.server.entity.ApiInterface;
import com.example.appservice.server.entity.TrafficRecord;
import com.example.appservice.server.mapper.ApiInterfaceMapper;
import com.example.appservice.server.mapper.TrafficRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrafficRecordService {

    private final TrafficRecordMapper trafficRecordMapper;
    private final ApiInterfaceMapper interfaceMapper;

    public void saveBatch(String appId, List<TrafficRecordDTO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (TrafficRecordDTO dto : records) {
            if (dto == null || !StringUtils.hasText(dto.getMethod()) || !StringUtils.hasText(dto.getPath())) {
                continue;
            }
            if (duplicate(appId, dto)) {
                continue;
            }
            TrafficRecord entity = new TrafficRecord();
            BeanUtils.copyProperties(dto, entity);
            entity.setId(null);
            entity.setAppId(appId);
            if (entity.getInterfaceId() == null) {
                entity.setInterfaceId(findInterfaceId(appId, dto.getMethod(), dto.getPath()));
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(LocalDateTime.now());
            }
            trafficRecordMapper.insert(entity);
        }
    }

    public List<TrafficRecord> list(String appId, Long interfaceId, String method, Integer status,
                                    boolean onlyFailures, LocalDateTime start, LocalDateTime end,
                                    String traceId) {
        LambdaQueryWrapper<TrafficRecord> query = new LambdaQueryWrapper<TrafficRecord>()
                .eq(TrafficRecord::getAppId, appId)
                .eq(interfaceId != null, TrafficRecord::getInterfaceId, interfaceId)
                .eq(StringUtils.hasText(method), TrafficRecord::getMethod, method)
                .eq(status != null, TrafficRecord::getResponseStatus, status)
                .eq(onlyFailures, TrafficRecord::isSuccess, false)
                .ge(start != null, TrafficRecord::getCreatedAt, start)
                .le(end != null, TrafficRecord::getCreatedAt, end)
                .like(StringUtils.hasText(traceId), TrafficRecord::getTraceId, traceId)
                .orderByDesc(TrafficRecord::getCreatedAt)
                .last("limit 500");
        return trafficRecordMapper.selectList(query);
    }

    public Long findInterfaceId(String appId, String method, String path) {
        List<ApiInterface> candidates = interfaceMapper.selectList(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getAppId, appId)
                .eq(ApiInterface::getMethod, method));
        AntPathMatcher matcher = new AntPathMatcher();
        return candidates.stream()
                .filter(item -> matcher.match(item.getPath(), path))
                .sorted(Comparator.comparingInt((ApiInterface item) -> item.getPath().length()).reversed())
                .map(ApiInterface::getId)
                .findFirst()
                .orElse(null);
    }

    private boolean duplicate(String appId, TrafficRecordDTO dto) {
        LambdaQueryWrapper<TrafficRecord> query = new LambdaQueryWrapper<TrafficRecord>()
                .eq(TrafficRecord::getAppId, appId)
                .eq(TrafficRecord::getMethod, dto.getMethod())
                .eq(TrafficRecord::getPath, dto.getPath());
        if (StringUtils.hasText(dto.getTraceId())) {
            query.eq(TrafficRecord::getTraceId, dto.getTraceId());
        }
        if (StringUtils.hasText(dto.getRequestHash())) {
            query.eq(TrafficRecord::getRequestHash, dto.getRequestHash());
        }
        return trafficRecordMapper.selectCount(query.last("limit 1")) > 0;
    }
}
