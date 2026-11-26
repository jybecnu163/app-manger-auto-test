package com.example.appservice.sdk.registry;
import com.example.appservice.sdk.config.AppRegistryProperties;

import com.example.appservice.common.dto.InterfaceMetaDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 扫描 Spring MVC 路由并推导请求/响应 Schema，供管理端自动生成测试表单。
 */
public class InterfaceScanner {

    private final ObjectProvider<RequestMappingHandlerMapping> mappingProvider;
    private final AppRegistryProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造 InterfaceScanner 实例。
     */
    public InterfaceScanner(ObjectProvider<RequestMappingHandlerMapping> mappingProvider,
                            AppRegistryProperties properties) {
        this.mappingProvider = mappingProvider;
        this.properties = properties;
    }

    /**
     * 扫描 RequestMappingHandlerMapping 并返回去重后的接口元数据。
     */
    public List<InterfaceMetaDTO> scan() {
        RequestMappingHandlerMapping mapping = mappingProvider.getIfAvailable();
        if (mapping == null) {
            return new ArrayList<>();
        }

        Map<String, InterfaceMetaDTO> result = new LinkedHashMap<>();
        mapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            List<String> methods = methodsOf(info);
            for (String path : pathsOf(info)) {
                if (isExcluded(path)) {
                    continue;
                }
                for (String method : methods) {
                    InterfaceMetaDTO meta = buildMeta(method, path, info, handlerMethod);
                    result.putIfAbsent(method + " " + path, meta);
                }
            }
        });
        return new ArrayList<>(result.values());
    }

    /**
     * 执行 build meta 相关逻辑。
     */
    private InterfaceMetaDTO buildMeta(String method, String path,
                                       RequestMappingInfo info,
                                       HandlerMethod handlerMethod) {
        Map<String, Object> request = new LinkedHashMap<>();
        Map<String, Object> pathParams = new LinkedHashMap<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        List<String> requiredPath = new ArrayList<>();
        List<String> requiredQuery = new ArrayList<>();
        List<String> requiredHeaders = new ArrayList<>();
        List<String> requiredBody = new ArrayList<>();
        Object body = null;

        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
            if (pathVariable != null) {
                String name = parameterName(pathVariable.value(), parameter);
                pathParams.put(name, schemaOf(parameter.getParameterType(), 1, new HashSet<>()));
                requiredPath.add(name);
                continue;
            }
            RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
            if (requestParam != null) {
                String name = parameterName(requestParam.value(), parameter);
                queryParams.put(name, schemaOf(parameter.getParameterType(), 1, new HashSet<>()));
                if (requestParam.required()) {
                    requiredQuery.add(name);
                }
                continue;
            }
            RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
            if (requestHeader != null) {
                String name = parameterName(requestHeader.value(), parameter);
                headers.put(name, schemaOf(parameter.getParameterType(), 1, new HashSet<>()));
                if (requestHeader.required()) {
                    requiredHeaders.add(name);
                }
                continue;
            }
            RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
            if (requestBody != null) {
                body = schemaOf(parameter.getGenericParameterType(), 1, new HashSet<>());
                if (requestBody.required()) {
                    requiredBody.add("body");
                }
            }
        }

        Map<String, Object> required = new LinkedHashMap<>();
        required.put("pathParams", requiredPath);
        required.put("queryParams", requiredQuery);
        required.put("headers", requiredHeaders);
        required.put("body", requiredBody);

        request.put("pathParams", pathParams);
        request.put("queryParams", queryParams);
        request.put("headers", headers);
        request.put("body", body == null ? new LinkedHashMap<>() : body);
        request.put("required", required);

        String requestJson = toJson(request);
        Type returnType = ResolvableType
                .forMethodReturnType(handlerMethod.getMethod(), handlerMethod.getBeanType())
                .getType();
        String responseJson = toJson(schemaOf(returnType, 1, new HashSet<>()));

        InterfaceMetaDTO meta = new InterfaceMetaDTO();
        meta.setMethod(method);
        meta.setPath(path);
        meta.setController(handlerMethod.getBeanType().getSimpleName());
        meta.setOperation(handlerMethod.getMethod().getName());
        meta.setRequestSchema(requestJson);
        meta.setResponseSchema(responseJson);
        // Schema 指纹用于管理端判断接口定义是否变化，避免重复生成自动用例。
        meta.setSchemaHash(DigestUtils.md5DigestAsHex(
                (method + path + requestJson + responseJson).getBytes(StandardCharsets.UTF_8)));
        return meta;
    }

    /**
     * 执行 parameter name 相关逻辑。
     */
    private String parameterName(String annotationValue, MethodParameter parameter) {
        if (StringUtils.hasText(annotationValue)) {
            return annotationValue;
        }
        String name = parameter.getParameterName();
        return StringUtils.hasText(name) ? name : "arg" + parameter.getParameterIndex();
    }

    /**
     * 执行 methods of 相关逻辑。
     */
    private List<String> methodsOf(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return List.of("GET");
        }
        List<String> result = new ArrayList<>();
        methods.forEach(method -> result.add(method.name()));
        return result;
    }

    /**
     * 执行 paths of 相关逻辑。
     */
    private Set<String> pathsOf(RequestMappingInfo info) {
        Set<String> paths = new LinkedHashSet<>();
        if (info.getPatternsCondition() != null) {
            paths.addAll(info.getPatternsCondition().getPatterns());
        }
        if (info.getPathPatternsCondition() != null) {
            info.getPathPatternsCondition().getPatterns()
                    .forEach(pattern -> paths.add(pattern.getPatternString()));
        }
        if (paths.isEmpty()) {
            paths.add("/");
        }
        return paths;
    }

    /**
     * 判断 excluded。
     */
    private boolean isExcluded(String path) {
        for (String pattern : properties.getExcludePaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将 Map 序列化为 JSON 字符串。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * 根据 Java 类型推导 JSON Schema。
     */
    private Object schemaOf(Type type, int depth, Set<Class<?>> visiting) {
        Class<?> clazz = resolveClass(type);
        Object simple = simpleType(clazz);
        if (simple != null) {
            return simple;
        }
        if (depth > 3) {
            return "object";
        }
        if (clazz.isArray()) {
            Map<String, Object> array = new LinkedHashMap<>();
            array.put("type", "array");
            array.put("items", schemaOf(clazz.getComponentType(), depth + 1, visiting));
            return array;
        }
        if (Collection.class.isAssignableFrom(clazz)) {
            Map<String, Object> array = new LinkedHashMap<>();
            array.put("type", "array");
            array.put("items", schemaOf(resolveGeneric(type, 0), depth + 1, visiting));
            return array;
        }
        if (Map.class.isAssignableFrom(clazz)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "object");
            map.put("additionalProperties", schemaOf(resolveGeneric(type, 1), depth + 1, visiting));
            return map;
        }
        if (visiting.contains(clazz)) {
            return "object (circular)";
        }

        visiting.add(clazz);
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                properties.put(field.getName(), schemaOf(field.getGenericType(), depth + 1, visiting));
            }
            current = current.getSuperclass();
        }
        object.put("properties", properties);
        visiting.remove(clazz);
        return object;
    }

    /**
     * 解析配置类并计算 Schema。
     */
    private Class<?> resolveClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return resolveClass(((ParameterizedType) type).getRawType());
        }
        if (type instanceof GenericArrayType) {
            return Object[].class;
        }
        if (type instanceof TypeVariable<?>) {
            Type[] bounds = ((TypeVariable<?>) type).getBounds();
            return bounds.length == 0 ? Object.class : resolveClass(bounds[0]);
        }
        if (type instanceof WildcardType) {
            Type[] upper = ((WildcardType) type).getUpperBounds();
            return upper.length == 0 ? Object.class : resolveClass(upper[0]);
        }
        return Object.class;
    }

    /**
     * 执行 resolve generic 相关逻辑。
     */
    private Type resolveGeneric(Type type, int index) {
        if (type instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            if (arguments.length > index) {
                return arguments[index];
            }
        }
        return Object.class;
    }

    /**
     * 执行 simple type 相关逻辑。
     */
    private Object simpleType(Class<?> clazz) {
        if (clazz == String.class || clazz == Character.class || clazz == char.class || clazz == UUID.class) {
            return "string";
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return "boolean";
        }
        if (clazz == Byte.class || clazz == byte.class
                || clazz == Short.class || clazz == short.class
                || clazz == Integer.class || clazz == int.class
                || clazz == Long.class || clazz == long.class) {
            return "integer";
        }
        if (clazz == Float.class || clazz == float.class
                || clazz == Double.class || clazz == double.class
                || clazz == BigDecimal.class) {
            return "number";
        }
        if (clazz.isEnum()) {
            Map<String, Object> enumSchema = new LinkedHashMap<>();
            enumSchema.put("type", "enum");
            List<String> values = new ArrayList<>();
            for (Object constant : clazz.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
            enumSchema.put("values", values);
            return enumSchema;
        }
        if (Date.class.isAssignableFrom(clazz) || Temporal.class.isAssignableFrom(clazz)) {
            return "date-time";
        }
        return null;
    }
}
