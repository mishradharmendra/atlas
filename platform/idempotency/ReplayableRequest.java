package com.atlas.platform.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A request whose body can be read twice.
 *
 * <p>Spring's {@code ContentCachingRequestWrapper} records bytes as the handler reads them, which
 * suits logging after the fact and not this: the fingerprint has to be taken before the handler
 * runs, and reading it first left the handler with an empty body and every request answered 400.
 */
final class ReplayableRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    ReplayableRequest(HttpServletRequest request, int maxBytes) throws IOException {
        super(request);
        this.body = request.getInputStream().readNBytes(maxBytes);
    }

    byte[] body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream buffered = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return buffered.read();
            }

            @Override
            public boolean isFinished() {
                return buffered.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("this request is buffered, not streamed");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(
                new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
