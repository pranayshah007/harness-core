/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import io.opentelemetry.api.trace.Span;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Provider
@Priority(Priorities.USER)
@Singleton
@Slf4j
public class TraceFilter implements ContainerResponseFilter {
  public static final String TRACE_ID_HEADER = "X-Harness-Trace-ID";

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    try {
      responseContext.getHeaders().add(TRACE_ID_HEADER, Span.current().getSpanContext().getTraceId());
    } catch (Exception e) {
      log.warn("Unable to add trace ID", e);
    }
  }
}
