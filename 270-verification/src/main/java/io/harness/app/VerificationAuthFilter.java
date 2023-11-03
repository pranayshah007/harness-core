/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.app;

import static io.harness.agent.AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.exception.WingsException.USER;

import static javax.ws.rs.Priorities.AUTHENTICATION;

import io.harness.exception.WingsException;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.security.VerificationServiceAuthenticationFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
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
public class VerificationAuthFilter extends VerificationServiceAuthenticationFilter implements ContainerRequestFilter {
  @Context private ResourceInfo resourceInfo;
  @Inject private VerificationManagerClient managerClient;

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    if (isDelegateRequest(containerRequestContext)) {
      MultivaluedMap<String, String> pathParameters = containerRequestContext.getUriInfo().getPathParameters();
      MultivaluedMap<String, String> queryParameters = containerRequestContext.getUriInfo().getQueryParameters();

      String accountId = getRequestParamFromContext("accountId", pathParameters, queryParameters);
      String header = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
      if (header.contains("Delegate")) {
        String delegateId = containerRequestContext.getHeaderString("delegateId");
        String delegateTokenName = containerRequestContext.getHeaderString("delegateTokenName");
        String agentMtlsAuthority = containerRequestContext.getHeaderString(HEADER_AGENT_MTLS_AUTHORITY);
        String token = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        validateDelegateToken(accountId, token, delegateId, delegateTokenName, agentMtlsAuthority);
      } else {
        throw new IllegalStateException("Invalid header:" + header);
      }

      return;
    }

    super.filter(containerRequestContext);
  }

  @Override
  public void validateDelegateToken(
      String accountId, String tokenString, String delegateId, String delegateTokenName, String agentMtlsAuthority) {
    try {
      if (managerClient
              .authenticateDelegateRequest(accountId, tokenString, delegateId, delegateTokenName, agentMtlsAuthority)
              .execute()
              .body()
              .getResource()) {
        return;
      }
    } catch (IOException e) {
      log.error("Can not validate delegate request", e);
    }
    throw new WingsException(INVALID_CREDENTIAL, USER);
  }
}
