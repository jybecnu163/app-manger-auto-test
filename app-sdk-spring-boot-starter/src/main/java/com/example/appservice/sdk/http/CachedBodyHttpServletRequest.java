package com.example.appservice.sdk.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 可重复读取请求体的 HttpServletRequest 包装器。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    /**
     * 构造 CachedBodyHttpServletRequest 实例。
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream().readAllBytes();
    }

    /**
     * 返回可重复读取的 Servlet 输入流。
     */
    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            /**
             * 判断 finished。
             */
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            /**
             * 判断 ready。
             */
            @Override
            public boolean isReady() {
                return true;
            }

            /**
             * 设置 read listener。
             */
            @Override
            public void setReadListener(ReadListener readListener) {
            }

            /**
             * 读取本地凭据文件；文件不存在或损坏时返回空值。
             */
            @Override
            public int read() {
                return input.read();
            }

            /**
             * 读取本地凭据文件；文件不存在或损坏时返回空值。
             */
            @Override
            public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
            }
        };
    }

    /**
     * 返回可重复读取的字符输入流。
     */
    @Override
    public BufferedReader getReader() {
        Charset charset = charsetOf(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    /**
     * 返回缓存的请求或响应原始字节。
     */
    public byte[] bodyBytes() {
        return body;
    }

    static Charset charsetOf(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
