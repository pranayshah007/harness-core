/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.request;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public abstract class AbstractRequestLoggingFilter implements ContainerRequestFilter {
  private final List<String> allowedHeadersForPrint = List.of("Harness-Account", "Content-Type");

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    log.info("Received request - Method [{}] | URL [{}] | Headers [{}]", containerRequestContext.getMethod(),
        containerRequestContext.getUriInfo().getRequestUri(), headers(containerRequestContext));
  }

  protected Map<String, List<String>> headers(ContainerRequestContext containerRequestContext) {
    Map<String, List<String>> headers = new HashMap<>();
    containerRequestContext.getHeaders().forEach((k, v) -> {
      if (allowedHeadersForPrint.stream().anyMatch(k::equalsIgnoreCase)) {
        headers.put(k, v);
      }
    });
    return headers;
  }
}
