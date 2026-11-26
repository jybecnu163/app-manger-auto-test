package com.example.appservice.sdk.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 录制数据脱敏工具，提供 Header、查询参数和 JSON Body 的截断与掩码处理。
 */
public final class MaskUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MASK = "***";

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private MaskUtils() {
    }

    /**
     * 对敏感 Header 进行掩码。
     */
    public static String maskHeaders(Map<String, String> headers, List<String> maskNames) {
        if (headers == null) {
            return null;
        }
        Map<String, String> masked = new TreeMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            masked.put(entry.getKey(), containsIgnoreCase(maskNames, entry.getKey()) ? MASK : entry.getValue());
        }
        return toJson(masked);
    }

    /**
     * 将 Map 序列化为 JSON 字符串。
     */
    public static String toJson(Map<String, ?> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 将查询参数 Map 序列化为 JSON 字符串。
     */
    public static String queryParamsJson(Map<String, String[]> params) {
        Map<String, String> flat = new LinkedHashMap<>();
        if (params != null) {
            for (Map.Entry<String, String[]> entry : params.entrySet()) {
                String[] values = entry.getValue();
                flat.put(entry.getKey(), values == null || values.length == 0 ? "" : values[0]);
            }
        }
        return toJson(new TreeMap<>(flat));
    }

    /**
     * 对 Body 中敏感字段进行掩码。
     */
    public static String maskBody(String body, List<String> maskFields) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            maskJson(node, maskFields);
            return MAPPER.writeValueAsString(node);
        } catch (Exception ignored) {
            String result = body;
            if (maskFields != null) {
                for (String field : maskFields) {
                    String regex = "(?i)(\"" + Pattern.quote(field) + "\"\\s*:\\s*)(\"[^\"]*\"|-?\\d+(?:\\.\\d+)?|true|false|null)";
                    result = result.replaceAll(regex, "$1\"***\"");
                }
            }
            return result;
        }
    }

    /**
     * 按配置长度截断字符串。
     */
    public static String truncate(String value, int maxBytes) {
        if (value == null) {
            return null;
        }
        int limit = Math.max(16, maxBytes);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= limit) {
            return value;
        }
        String marker = "...[truncated]";
        int keep = Math.max(0, limit - marker.getBytes(StandardCharsets.UTF_8).length);
        return new String(bytes, 0, keep, StandardCharsets.UTF_8) + marker;
    }

    /**
     * 执行 mask json 相关逻辑。
     */
    private static void maskJson(JsonNode node, List<String> maskFields) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (containsIgnoreCase(maskFields, field.getKey())) {
                    object.put(field.getKey(), MASK);
                } else {
                    maskJson(field.getValue(), maskFields);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                maskJson(child, maskFields);
            }
        }
    }

    /**
     * 执行 contains ignore case 相关逻辑。
     */
    private static boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
