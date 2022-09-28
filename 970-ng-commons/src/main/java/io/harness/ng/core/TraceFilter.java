package io.harness.ng.core;

import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;

@Slf4j
public class TraceFilter implements ContainerResponseFilter {
    public static final String TRACE_ID_HEADER = "X-Harness-Trace-ID";

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) throws IOException {
        try {
            containerResponseContext.getHeaders().add(TRACE_ID_HEADER, Span.current().getSpanContext().getTraceId());
        } catch (Exception e) {
            log.warn("Unable to add trace ID", e);
        }
    }
}
