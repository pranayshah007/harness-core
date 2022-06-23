/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.exception.WingsException.USER;

import static javax.ws.rs.Priorities.AUTHORIZATION;
import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.cvng.client.VerificationManagerClient;
import io.harness.exception.WingsException;
import io.harness.security.VerificationServiceAuthenticationFilter;
import io.harness.security.annotations.NextGenManagerAuth;

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
import lombok.extern.slf4j.Slf4j;

@Singleton
@Priority(AUTHORIZATION)
@Slf4j
public class CVNGAuthenticationFilter
    extends VerificationServiceAuthenticationFilter implements ContainerRequestFilter {
  @Context private ResourceInfo resourceInfo;
  @Inject private VerificationManagerClient managerClient;

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    if (authenticationExemptedRequests(containerRequestContext)) {
      return;
    }

    if (isLearningEngineServiceRequest(containerRequestContext)) {
      validateLearningEngineServiceToken(
          substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "LearningEngine "));
      return;
    }

    // this is already authenticated by common JWTAuthenticationFilter
    if (isNextGenServiceAPI()) {
      return;
    }

    if (isDelegateRequest(containerRequestContext)) {
      String accountId = containerRequestContext.getUriInfo().getQueryParameters().getFirst("accountId");
      String token = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
      String delegateId = containerRequestContext.getHeaderString("delegateId");
      String delegateTokenName = containerRequestContext.getHeaderString("delegateTokenName");
      validateDelegateToken(accountId, token, delegateId, delegateTokenName);
      return;
    }

    throw new WingsException(INVALID_CREDENTIAL, USER);
  }

  @Override
  public void validateDelegateToken(String accountId, String tokenString, String delegateId, String delegateTokenName) {
    try {
      if (managerClient.authenticateDelegateRequest(accountId, tokenString, delegateId, delegateTokenName)
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

  protected boolean isNextGenServiceAPI() {
    Class<?> resourceClass = this.resourceInfo.getResourceClass();
    Method resourceMethod = this.resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(NextGenManagerAuth.class) != null
        || resourceClass.getAnnotation(NextGenManagerAuth.class) != null;
  }
}
