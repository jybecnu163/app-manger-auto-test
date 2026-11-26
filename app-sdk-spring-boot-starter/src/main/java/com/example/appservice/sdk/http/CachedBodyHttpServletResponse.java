package com.example.appservice.sdk.http;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * 可缓存响应体的 HttpServletResponse 包装器，便于录制后回写。
 */
public class CachedBodyHttpServletResponse extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final ServletOutputStream captureStream = new ServletOutputStream() {
        /**
         * 将一个字节写入内存响应缓冲。
         */
        @Override
        public void write(int value) {
            buffer.write(value);
        }

        /**
         * 判断 ready。
         */
        @Override
        public boolean isReady() {
            return true;
        }

        /**
         * 设置 write listener。
         */
        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    };

    private PrintWriter writer;

    /**
     * 构造 CachedBodyHttpServletResponse 实例。
     */
    public CachedBodyHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    /**
     * 返回写入内存缓冲的 Servlet 输出流。
     */
    @Override
    public ServletOutputStream getOutputStream() {
        return captureStream;
    }

    /**
     * 返回写入内存缓冲的字符输出流。
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(captureStream,
                    CachedBodyHttpServletRequest.charsetOf(getCharacterEncoding())), true);
        }
        return writer;
    }

    /**
     * 返回缓存的请求或响应原始字节。
     */
    public byte[] bodyBytes() {
        if (writer != null) {
            writer.flush();
        }
        return buffer.toByteArray();
    }

    /**
     * 将缓存的响应体回写到真实响应。
     */
    public void copyBodyTo(HttpServletResponse response) throws IOException {
        ServletOutputStream output = response.getOutputStream();
        output.write(bodyBytes());
        output.flush();
    }
}
