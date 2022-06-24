/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.app;

import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.security.DelegateTokenAuthenticator;
import io.harness.security.VerificationServiceAuthenticationFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
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
  @Inject private DelegateTokenAuthenticator delegateTokenAuthenticator;

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
        String token = substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "Delegate ");
        validateDelegateToken(accountId, token, delegateId, delegateTokenName);
      } else {
        throw new IllegalStateException("Invalid header:" + header);
      }

      return;
    }

    super.filter(containerRequestContext);
  }

  @Override
  public void validateDelegateToken(String accountId, String tokenString, String delegateId, String delegateTokenName) {
    delegateTokenAuthenticator.validateDelegateToken(accountId, tokenString, delegateId, delegateTokenName, false);
  }
}
