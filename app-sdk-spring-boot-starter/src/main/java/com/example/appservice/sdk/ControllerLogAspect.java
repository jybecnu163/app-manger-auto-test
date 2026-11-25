package com.example.appservice.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Aspect
public class ControllerLogAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerLogAspect.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        Object[] args = joinPoint.getArgs();

        log.info("[CTRL-IN] {} args={}", name, formatArgs(method, args));
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.info("[CTRL-OUT] {} cost={}ms result={}", name,
                    System.currentTimeMillis() - start, formatValue(result));
            return result;
        } catch (Throwable e) {
            log.warn("[CTRL-ERR] {} cost={}ms", name, System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    private String formatArgs(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        Parameter[] parameters = method.getParameters();
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            String parameterName = i < parameters.length ? parameters[i].getName() : "arg" + i;
            builder.append(parameterName).append('=').append(formatValue(args[i]));
        }
        return builder.append('}').toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ServletRequest || value instanceof ServletResponse || value instanceof HttpSession) {
            return value.getClass().getSimpleName();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
