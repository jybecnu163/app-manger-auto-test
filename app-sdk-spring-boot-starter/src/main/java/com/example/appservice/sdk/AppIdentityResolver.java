package com.example.appservice.sdk;

import org.springframework.util.StringUtils;

/**
 * 解析 SDK 身份信息，优先使用配置值，其次回落到本地 appid 文件。
 */
public class AppIdentityResolver {

    private final AppRegistryProperties properties;
    private final AppIdFileStore fileStore;

    public AppIdentityResolver(AppRegistryProperties properties, AppIdFileStore fileStore) {
        this.properties = properties;
        this.fileStore = fileStore;
    }

    /**
     * 返回当前应用 appId。
     */
    public String appId() {
        if (StringUtils.hasText(properties.getAppid())) {
            return properties.getAppid();
        }
        AppIdFileStore.StoredApp stored = stored();
        return stored == null ? null : stored.getAppId();
    }

    /**
     * 返回当前应用 token。
     */
    public String token() {
        if (StringUtils.hasText(properties.getToken())) {
            return properties.getToken();
        }
        AppIdFileStore.StoredApp stored = stored();
        return stored == null ? null : stored.getToken();
    }

    private AppIdFileStore.StoredApp stored() {
        if (fileStore == null) {
            return null;
        }
        return fileStore.read(fileStore.resolvePath(properties));
    }
}
