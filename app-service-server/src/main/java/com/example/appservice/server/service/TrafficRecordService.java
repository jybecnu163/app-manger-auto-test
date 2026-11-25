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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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

    public Long findInterfaceId(String appId, String method, String path) {
        ApiInterface apiInterface = interfaceMapper.selectOne(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getAppId, appId)
                .eq(ApiInterface::getMethod, method)
                .eq(ApiInterface::getPath, path)
                .last("limit 1"));
        return apiInterface == null ? null : apiInterface.getId();
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
