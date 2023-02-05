package io.harness.dms.app;

import static io.harness.agent.AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.logging.AccountLogContext;
import io.harness.security.annotations.DelegateAuth;
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
    filterAndValidateDelegateRequest(containerRequestContext);
  }

  public void filterAndValidateDelegateRequest(ContainerRequestContext containerRequestContext) {
    if (delegateAPI()) {
      validateDelegateRequest(containerRequestContext);
      return;
    }

    if (delegateAuth2API()) {
      validateDelegateAuth2Request(containerRequestContext);
      return;
    }
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
    try (AccountLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String header = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
      if (header != null && header.contains("Delegate")) {
        final String delegateId = containerRequestContext.getHeaderString("delegateId");
        final String delegateTokeName = containerRequestContext.getHeaderString("delegateTokenName");
        final String agentMtlsAuthority = containerRequestContext.getHeaderString(HEADER_AGENT_MTLS_AUTHORITY);

        delegateAuthService.validateDelegateToken(accountId,
            substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "Delegate "), delegateId,
            delegateTokeName, agentMtlsAuthority, true);
      } else {
        throw new IllegalStateException("Invalid header:" + header);
      }
    }
  }

  protected void validateDelegateAuth2Request(ContainerRequestContext containerRequestContext) {
    MultivaluedMap<String, String> pathParameters = containerRequestContext.getUriInfo().getPathParameters();
    MultivaluedMap<String, String> queryParameters = containerRequestContext.getUriInfo().getQueryParameters();

    String accountId = getRequestParamFromContext("accountId", pathParameters, queryParameters);
    try (AccountLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String authHeader = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
      if (authHeader != null && authHeader.contains("Delegate")) {
        final String jwtToken =
            substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "Delegate ");
        final String delegateId = containerRequestContext.getHeaderString("delegateId");
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
}
