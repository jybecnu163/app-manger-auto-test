package com.example.appservice.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.appservice.common.dto.MockResponseDTO;
import com.example.appservice.common.util.RequestHashUtil;
import com.example.appservice.server.entity.ExternalCallRecord;
import com.example.appservice.server.entity.MockResponse;
import com.example.appservice.server.entity.TrafficRecord;
import com.example.appservice.server.mapper.ExternalCallRecordMapper;
import com.example.appservice.server.mapper.MockResponseMapper;
import com.example.appservice.server.mapper.TrafficRecordMapper;
import com.example.appservice.server.vo.MockResponseSaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MockResponseService {

    private final MockResponseMapper mockResponseMapper;
    private final ExternalCallRecordMapper externalCallRecordMapper;
    private final TrafficRecordMapper trafficRecordMapper;

    public List<MockResponseDTO> listByApp(String appId) {
        return mockResponseMapper.selectList(new LambdaQueryWrapper<MockResponse>()
                        .eq(MockResponse::getAppId, appId)
                        .eq(MockResponse::isEnabled, true))
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<MockResponseDTO> listByInterface(String appId, Long interfaceId) {
        LambdaQueryWrapper<MockResponse> query = new LambdaQueryWrapper<MockResponse>()
                .eq(MockResponse::getInterfaceId, interfaceId);
        if (StringUtils.hasText(appId)) {
            query.eq(MockResponse::getAppId, appId);
        }
        return mockResponseMapper.selectList(query.orderByDesc(MockResponse::getUpdatedAt))
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public MockResponseDTO create(MockResponseSaveRequest request) {
        if (request.getExternalCallRecordId() != null) {
            return generateFromExternalCall(request.getAppId(), request.getExternalCallRecordId());
        }
        if (!StringUtils.hasText(request.getMethod()) || !StringUtils.hasText(request.getUrlPattern())) {
            throw new IllegalArgumentException("method 与 urlPattern 不能为空");
        }
        MockResponse entity = new MockResponse();
        BeanUtils.copyProperties(request, entity);
        entity.setId(null);
        entity.setMatchMode("EXACT");
        if (!StringUtils.hasText(entity.getRequestHash())) {
            entity.setRequestHash(RequestHashUtil.of(request.getMethod(), request.getUrlPattern(),
                    null, request.getMockBody()));
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mockResponseMapper.insert(entity);
        return toDTO(entity);
    }

    public MockResponseDTO generateFromExternalCall(String appId, Long externalCallRecordId) {
        ExternalCallRecord record = externalCallRecordMapper.selectById(externalCallRecordId);
        if (record == null) {
            throw new IllegalArgumentException("外部调用记录不存在：" + externalCallRecordId);
        }
        if (StringUtils.hasText(appId) && !appId.equals(record.getAppId())) {
            throw new IllegalArgumentException("外部调用记录不属于该应用");
        }

        LambdaQueryWrapper<MockResponse> query = new LambdaQueryWrapper<MockResponse>()
                .eq(MockResponse::getAppId, record.getAppId())
                .eq(MockResponse::getRequestHash, record.getRequestHash())
                .last("limit 1");
        MockResponse entity = mockResponseMapper.selectOne(query);
        if (entity == null) {
            entity = new MockResponse();
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setAppId(record.getAppId());
        entity.setInterfaceId(findInterfaceId(record));
        entity.setMethod(record.getMethod());
        entity.setUrlPattern(record.getUrl());
        entity.setMatchMode("EXACT");
        entity.setRequestHash(record.getRequestHash());
        entity.setMockStatus(record.getResponseStatus() == null ? 200 : record.getResponseStatus());
        entity.setMockHeaders(record.getResponseHeaders());
        entity.setMockBody(record.getResponseBody());
        entity.setEnabled(true);
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getId() == null) {
            mockResponseMapper.insert(entity);
        } else {
            mockResponseMapper.updateById(entity);
        }
        return toDTO(entity);
    }

    public void setEnabled(Long id, boolean enabled) {
        MockResponse entity = mockResponseMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Mock 规则不存在：" + id);
        }
        entity.setEnabled(enabled);
        entity.setUpdatedAt(LocalDateTime.now());
        mockResponseMapper.updateById(entity);
    }

    private Long findInterfaceId(ExternalCallRecord record) {
        if (!StringUtils.hasText(record.getTraceId())) {
            return null;
        }
        TrafficRecord traffic = trafficRecordMapper.selectOne(new LambdaQueryWrapper<TrafficRecord>()
                .eq(TrafficRecord::getAppId, record.getAppId())
                .eq(TrafficRecord::getTraceId, record.getTraceId())
                .last("limit 1"));
        return traffic == null ? null : traffic.getInterfaceId();
    }

    private MockResponseDTO toDTO(MockResponse entity) {
        MockResponseDTO dto = new MockResponseDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
