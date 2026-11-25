package com.example.appservice.sdk;

import org.springframework.util.StringUtils;

public class AppIdentityResolver {

    private final AppRegistryProperties properties;
    private final AppIdFileStore fileStore;

    public AppIdentityResolver(AppRegistryProperties properties, AppIdFileStore fileStore) {
        this.properties = properties;
        this.fileStore = fileStore;
    }

    public String appId() {
        if (StringUtils.hasText(properties.getAppid())) {
            return properties.getAppid();
        }
        AppIdFileStore.StoredApp stored = stored();
        return stored == null ? null : stored.getAppId();
    }

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
