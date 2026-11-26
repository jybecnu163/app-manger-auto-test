package com.example.appservice.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 请求指纹计算工具，按 method、URL、规范化 query 和 body 生成 MD5，用于流量去重与 Mock 匹配。
 */
public final class RequestHashUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RequestHashUtil() {
    }

    /**
     * 计算请求指纹；query 会按 key 排序，URL 中的查询串会被剔除。
     */
    public static String of(String method, String url, String queryParams, String body) {
        String normalizedMethod = method == null ? "" : method.trim().toUpperCase();
        int questionMark = url == null ? -1 : url.indexOf('?');
        String normalizedUrl = url == null ? "" : (questionMark >= 0 ? url.substring(0, questionMark) : url);
        String inlineQuery = questionMark >= 0 ? url.substring(questionMark + 1) : null;
        String normalizedQuery = normalizeQuery(queryParams, inlineQuery);
        String normalizedBody = body == null ? "" : body;
        String source = normalizedMethod + '\n' + normalizedUrl + '\n' + normalizedQuery + '\n' + normalizedBody;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算请求签名", e);
        }
    }

    private static String normalizeQuery(String queryParams, String inlineQuery) {
        if (hasText(queryParams)) {
            try {
                JsonNode node = MAPPER.readTree(queryParams);
                if (node.isObject()) {
                    TreeMap<String, String> sorted = new TreeMap<>();
                    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        sorted.put(field.getKey(), field.getValue().asText());
                    }
                    return join(sorted);
                }
            } catch (Exception ignored) {
                // 非 JSON 查询串走下面的原始字符串归一化
            }
        }
        if (!hasText(inlineQuery)) {
            return "";
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String pair : inlineQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            sorted.put(key, value);
        }
        return join(sorted);
    }

    private static String join(Map<String, String> sorted) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join("&", parts);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
