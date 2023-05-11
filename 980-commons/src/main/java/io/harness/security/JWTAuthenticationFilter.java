/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.exception.WingsException.USER;

import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.StringUtils.SPACE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.GlobalContextManager;
import io.harness.security.dto.Principal;

import com.auth0.jwt.interfaces.Claim;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(PL)
public abstract class JWTAuthenticationFilter implements ContainerRequestFilter, ContainerResponseFilter {
  @Context private ResourceInfo resourceInfo;
  private final Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate;
  private final Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping;
  private final Map<String, String> serviceToSecretMapping;
  public static final String X_SOURCE_PRINCIPAL = "X-Source-Principal";

  protected JWTAuthenticationFilter(Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate,
      Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping) {
    this.predicate = predicate;
    this.serviceToJWTTokenHandlerMapping =
        serviceToJWTTokenHandlerMapping == null ? emptyMap() : serviceToJWTTokenHandlerMapping;
    this.serviceToSecretMapping = serviceToSecretMapping;
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    filter(containerRequestContext, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
  }

  protected boolean testRequestPredicate(ContainerRequestContext containerRequestContext) {
    return predicate.test(Pair.of(resourceInfo, containerRequestContext));
  }

  public static void setSourcePrincipalInContext(ContainerRequestContext containerRequestContext,
      Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping,
      Principal principalInContext) {
    Optional<String> sourcePrincipalServiceId =
        JWTTokenServiceUtils.extractSource(X_SOURCE_PRINCIPAL, containerRequestContext);
    if (sourcePrincipalServiceId.isPresent()) {
      String secret = JWTTokenServiceUtils.extractSecret(serviceToSecretMapping, sourcePrincipalServiceId.get());
      Optional<String> sourcePrincipalToken = JWTTokenServiceUtils.extractToken(
          X_SOURCE_PRINCIPAL, containerRequestContext, sourcePrincipalServiceId.get() + SPACE);
      if (sourcePrincipalToken.isPresent()) {
        Pair<Boolean, Map<String, Claim>> validate =
            serviceToJWTTokenHandlerMapping
                .getOrDefault(sourcePrincipalServiceId.get(),
                    (serviceToken, serviceSecret)
                        -> getIsServiceAuthorizationValid(serviceToken, serviceSecret, sourcePrincipalServiceId.get()))
                .validate(sourcePrincipalToken.get(), secret);
        if (Boolean.TRUE.equals(validate.getLeft())) {
          SourcePrincipalContextBuilder.setSourcePrincipal(
              SecurityContextBuilder.getPrincipalFromClaims(validate.getRight()));
          return;
        }
      }
    }
    SourcePrincipalContextBuilder.setSourcePrincipal(principalInContext);
  }

  public static void filter(ContainerRequestContext containerRequestContext,
      Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping) {
    String sourceServiceId = JWTTokenServiceUtils.extractSource(containerRequestContext);
    String secret = JWTTokenServiceUtils.extractSecret(serviceToSecretMapping, sourceServiceId);
    String token = JWTTokenServiceUtils.extractToken(containerRequestContext, sourceServiceId + SPACE);
    Pair<Boolean, Map<String, Claim>> validate =
        serviceToJWTTokenHandlerMapping
            .getOrDefault(sourceServiceId,
                (serviceToken,
                    serviceSecret) -> getIsServiceAuthorizationValid(serviceToken, serviceSecret, sourceServiceId))
            .validate(token, secret);
    if (Boolean.TRUE.equals(validate.getLeft())) {
      SecurityContextBuilder.setContext(validate.getRight());
      setSourcePrincipalInContext(containerRequestContext, serviceToJWTTokenHandlerMapping, serviceToSecretMapping,
          SecurityContextBuilder.getPrincipal());
      return;
    }
    throw new InvalidRequestException(INVALID_TOKEN.name(), INVALID_TOKEN, USER);
  }

  @NotNull
  private static Pair<Boolean, Map<String, Claim>> getIsServiceAuthorizationValid(
      String serviceToken, String serviceSecret, String sourceServiceId) {
    try {
      return JWTTokenServiceUtils.isServiceAuthorizationValid(serviceToken, serviceSecret);
    } catch (Exception ex) {
      log.error("sourceServiceId: " + sourceServiceId, ex);
      throw ex;
    }
  }

  @Override
  public void filter(
      ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) {
    if (predicate.test(Pair.of(resourceInfo, containerRequestContext))) {
      GlobalContextManager.unset();
    }
  }
}
