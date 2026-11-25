package com.example.appservice.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.appservice.server.entity.AppInstance;
import org.apache.ibatis.annotations.Update;

public interface AppInstanceMapper extends BaseMapper<AppInstance> {

    @Update("INSERT INTO app_instance (app_id, instance_key, ip, port, hostname, status, last_heartbeat_at, start_time) "
            + "VALUES (#{appId}, #{instanceKey}, #{ip}, #{port}, #{hostname}, #{status}, #{lastHeartbeatAt}, #{startTime}) "
            + "ON DUPLICATE KEY UPDATE ip = VALUES(ip), port = VALUES(port), hostname = VALUES(hostname), "
            + "status = VALUES(status), last_heartbeat_at = VALUES(last_heartbeat_at), start_time = VALUES(start_time)")
    int upsert(AppInstance instance);
}
