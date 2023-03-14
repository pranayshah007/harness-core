/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.app;

import static io.harness.agent.AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.delegate.task.DelegateLogContext;
import io.harness.exception.InvalidRequestException;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.security.annotations.PublicApiWithWhitelist;
import io.harness.service.intfc.DelegateAuthService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Method;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Priority(AUTHENTICATION)
@Slf4j
public class DelegateServiceAuthFilter implements ContainerRequestFilter {
  @Context private ResourceInfo resourceInfo;

  private DelegateAuthService delegateAuthService;

  @Inject
  public DelegateServiceAuthFilter(DelegateAuthService delegateAuthService) {
    this.delegateAuthService = delegateAuthService;
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    if (authenticationExemptedRequests(containerRequestContext)) {
      return;
    }
    if (delegateAPI() || delegateAuth2API()) {
      validateDelegateRequest(containerRequestContext);
      return;
    }
    // api-key auth TBA

    // If no auth is satisfied throw exception
    throw new InvalidRequestException("Invalid request", USER);
  }

  protected boolean delegateAuth2API() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(io.harness.security.annotations.DelegateAuth2.class) != null
        || resourceClass.getAnnotation(io.harness.security.annotations.DelegateAuth2.class) != null;
  }

  protected boolean delegateAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(DelegateAuth.class) != null
        || resourceClass.getAnnotation(DelegateAuth.class) != null;
  }

  protected void validateDelegateRequest(ContainerRequestContext containerRequestContext) {
    MultivaluedMap<String, String> pathParameters = containerRequestContext.getUriInfo().getPathParameters();
    MultivaluedMap<String, String> queryParameters = containerRequestContext.getUriInfo().getQueryParameters();

    String accountId = getRequestParamFromContext("accountId", pathParameters, queryParameters);
    final String delegateId = containerRequestContext.getHeaderString("delegateId");
    try (DelegateLogContext ignore = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      String authHeader = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
      if (authHeader != null && authHeader.contains("Delegate")) {
        final String jwtToken =
            substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "Delegate ");
        final String delegateTokeName = containerRequestContext.getHeaderString("delegateTokenName");
        final String agentMtlsAuthority = containerRequestContext.getHeaderString(HEADER_AGENT_MTLS_AUTHORITY);

        delegateAuthService.validateDelegateToken(
            accountId, jwtToken, delegateId, delegateTokeName, agentMtlsAuthority, true);
      } else {
        throw new IllegalStateException("Invalid authentication header:" + authHeader);
      }
    }
  }

  private String getRequestParamFromContext(
      String key, MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
    return queryParameters.getFirst(key) != null ? queryParameters.getFirst(key) : pathParameters.getFirst(key);
  }

  protected boolean authenticationExemptedRequests(ContainerRequestContext requestContext) {
    return requestContext.getMethod().equals(OPTIONS) || publicAPI() || isPublicApiWithWhitelist()
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("api/version")
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("api/swagger")
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("api/swagger.json");
  }

  private boolean isPublicApiWithWhitelist() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(PublicApiWithWhitelist.class) != null
        || resourceClass.getAnnotation(PublicApiWithWhitelist.class) != null;
  }

  protected boolean publicAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(PublicApi.class) != null
        || resourceClass.getAnnotation(PublicApi.class) != null;
  }
}
