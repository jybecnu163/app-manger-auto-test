package com.example.appservice.sdk.replay;

/**
 * Mock 未命中异常，保证回放失败时不会回退到真实外部调用。
 */
public class ReplayMockMissingException extends RuntimeException {

    /**
     * 构造 ReplayMockMissingException 实例。
     */
    public ReplayMockMissingException(String message) {
        super(message);
    }
}
