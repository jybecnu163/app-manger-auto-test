package com.example.appservice.sdk;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class CachedBodyHttpServletResponse extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final ServletOutputStream captureStream = new ServletOutputStream() {
        @Override
        public void write(int value) {
            buffer.write(value);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    };

    private PrintWriter writer;

    public CachedBodyHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return captureStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(captureStream,
                    CachedBodyHttpServletRequest.charsetOf(getCharacterEncoding())), true);
        }
        return writer;
    }

    public byte[] bodyBytes() {
        if (writer != null) {
            writer.flush();
        }
        return buffer.toByteArray();
    }

    public void copyBodyTo(HttpServletResponse response) throws IOException {
        ServletOutputStream output = response.getOutputStream();
        output.write(bodyBytes());
        output.flush();
    }
}
