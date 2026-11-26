package com.example.appservice.sdk.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 可重复读取响应体的 ClientHttpResponse 包装器。
 */
public class CachedClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse delegate;
    private final byte[] body;

    /**
     * 构造 CachedClientHttpResponse 实例。
     */
    public CachedClientHttpResponse(ClientHttpResponse delegate) throws IOException {
        this.delegate = delegate;
        this.body = delegate.getBody().readAllBytes();
    }

    /**
     * 返回模拟或缓存响应的状态码。
     */
    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return delegate.getStatusCode();
    }

    /**
     * 返回模拟或缓存响应的状态文本。
     */
    @Override
    public String getStatusText() throws IOException {
        return delegate.getStatusText();
    }

    /**
     * 关闭响应资源。
     */
    @Override
    public void close() {
        delegate.close();
    }

    /**
     * 返回可重复读取的响应体输入流。
     */
    @Override
    public InputStream getBody() {
        return new ByteArrayInputStream(body);
    }

    /**
     * 返回模拟或缓存响应的头信息。
     */
    @Override
    public HttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    /**
     * 返回缓存的请求或响应原始字节。
     */
    public byte[] bodyBytes() {
        return body;
    }
}
