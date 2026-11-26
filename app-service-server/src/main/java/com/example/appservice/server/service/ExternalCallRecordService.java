package com.example.appservice.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.appservice.common.dto.ExternalCallRecordDTO;
import com.example.appservice.server.entity.ExternalCallRecord;
import com.example.appservice.server.mapper.ExternalCallRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出站 HTTP 调用记录的持久化与查询服务。
 */
@Service
@RequiredArgsConstructor
public class ExternalCallRecordService {

    private final ExternalCallRecordMapper externalCallRecordMapper;

    /**
     * 按方法、URL、请求指纹和时间去重后批量保存外部调用。
     */
    public void saveBatch(String appId, List<ExternalCallRecordDTO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (ExternalCallRecordDTO dto : records) {
            if (dto == null || !StringUtils.hasText(dto.getMethod()) || !StringUtils.hasText(dto.getUrl())) {
                continue;
            }
            LocalDateTime createdAt = dto.getCreatedAt() == null ? LocalDateTime.now() : dto.getCreatedAt();
            if (duplicate(appId, dto, createdAt)) {
                continue;
            }
            ExternalCallRecord entity = new ExternalCallRecord();
            BeanUtils.copyProperties(dto, entity);
            entity.setId(null);
            entity.setAppId(appId);
            entity.setCreatedAt(createdAt);
            externalCallRecordMapper.insert(entity);
        }
    }

    /**
     * 查询应用最近出站调用记录，供 Mock 生成使用。
     */
    public List<ExternalCallRecord> listByApp(String appId) {
        return externalCallRecordMapper.selectList(new LambdaQueryWrapper<ExternalCallRecord>()
                .eq(ExternalCallRecord::getAppId, appId)
                .orderByDesc(ExternalCallRecord::getCreatedAt)
                .last("limit 200"));
    }

    private boolean duplicate(String appId, ExternalCallRecordDTO dto, LocalDateTime createdAt) {
        return externalCallRecordMapper.selectCount(new LambdaQueryWrapper<ExternalCallRecord>()
                .eq(ExternalCallRecord::getAppId, appId)
                .eq(ExternalCallRecord::getMethod, dto.getMethod())
                .eq(ExternalCallRecord::getUrl, dto.getUrl())
                .eq(ExternalCallRecord::getRequestHash, dto.getRequestHash())
                .eq(ExternalCallRecord::getCreatedAt, createdAt)
                .last("limit 1")) > 0;
    }
}
