/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.NGCommonEntityConstants.ACCOUNT_HEADER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.exception.WingsException.USER;

import static javax.ws.rs.Priorities.AUTHENTICATION;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.annotations.ScimAPI;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.token.remote.TokenClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(PL)
@Singleton
@Priority(AUTHENTICATION)
@Slf4j
public class NextGenAuthenticationFilter extends JWTAuthenticationFilter {
  public static final String X_API_KEY = "X-Api-Key";
  public static final String AUTHORIZATION_HEADER = "Authorization";

  private final TokenClient tokenClient;
  @Context @Setter @VisibleForTesting private ResourceInfo resourceInfo;

  public NextGenAuthenticationFilter(Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate,
      Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping,
      @Named("PRIVILEGED") TokenClient tokenClient) {
    super(predicate, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
    this.tokenClient = tokenClient;
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    if (!super.testRequestPredicate(containerRequestContext)) {
      // Predicate testing failed with the current request context
      return;
    }
    boolean isScimCall = isScimAPI();
    Optional<String> apiKeyOptional =
        isScimCall ? getApiKeyForScim(containerRequestContext) : getApiKeyFromHeaders(containerRequestContext);

    if (apiKeyOptional.isPresent()) {
      Optional<String> accountIdentifierOptional = getAccountIdentifierFrom(containerRequestContext);
      if (accountIdentifierOptional.isEmpty()) {
        throw new InvalidRequestException("Account detail is not present in the request");
      }
      String accountIdentifier = accountIdentifierOptional.get();
      TokenDTO tokenDTO = null;
      try {
        tokenDTO = NGRestUtils.getResponse(tokenClient.validateApiKey(
            accountIdentifier, RequestBody.create(MediaType.get("text/plain"), apiKeyOptional.get())));
      } catch (InvalidRequestException ire) {
        logAndThrowTokenException(
            String.format(
                "Invalid call: Invalid token or token expired. Account id for API called: %s", accountIdentifier),
            INVALID_TOKEN, ire);
      } catch (Exception exc) {
        logAndThrowTokenException(
            String.format("Error fetching ApiKey token details for account: %s", accountIdentifier), INVALID_TOKEN,
            exc);
      }
      if (tokenDTO != null) {
        Principal principal = getPrincipal(tokenDTO);
        io.harness.security.SecurityContextBuilder.setContext(principal);
        SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      } else {
        throw new InvalidRequestException(
            String.format("Invalid API key or incorrect JWT token request in account: %s", accountIdentifier),
            INVALID_TOKEN, USER);
      }
    } else {
      super.filter(containerRequestContext);
    }
  }

  private boolean isScimAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(ScimAPI.class) != null || resourceClass.getAnnotation(ScimAPI.class) != null;
  }

  private Optional<String> getApiKeyFromHeaders(ContainerRequestContext containerRequestContext) {
    String apiKey = containerRequestContext.getHeaderString(X_API_KEY);
    return StringUtils.isEmpty(apiKey) ? Optional.empty() : Optional.of(apiKey);
  }

  private Optional<String> getApiKeyForScim(ContainerRequestContext containerRequestContext) {
    String apiKey = getBearerToken(containerRequestContext.getHeaderString(AUTHORIZATION_HEADER));
    return StringUtils.isEmpty(apiKey) ? Optional.empty() : Optional.of(apiKey);
  }

  private String getBearerToken(String authorizationHeader) {
    String bearerPrefix = "Bearer ";
    if (!authorizationHeader.contains(bearerPrefix)) {
      throw new UnauthorizedException("Bearer prefix not found", USER);
    }
    return authorizationHeader.substring(bearerPrefix.length()).trim();
  }

  private Optional<String> getAccountIdentifierFrom(ContainerRequestContext containerRequestContext) {
    String accountIdentifier = containerRequestContext.getHeaderString(ACCOUNT_HEADER);

    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT);
    }
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    return StringUtils.isEmpty(accountIdentifier) ? Optional.empty() : Optional.of(accountIdentifier);
  }

  private Principal getPrincipal(TokenDTO tokenDTO) {
    Principal principal = null;
    if (tokenDTO.getApiKeyType() == ApiKeyType.SERVICE_ACCOUNT) {
      principal = new ServiceAccountPrincipal(
          tokenDTO.getParentIdentifier(), tokenDTO.getEmail(), tokenDTO.getUsername(), tokenDTO.getAccountIdentifier());
    }
    if (tokenDTO.getApiKeyType() == ApiKeyType.USER) {
      principal = new UserPrincipal(
          tokenDTO.getParentIdentifier(), tokenDTO.getEmail(), tokenDTO.getUsername(), tokenDTO.getAccountIdentifier());
    }
    return principal;
  }

  private void logAndThrowTokenException(String errorMessage, ErrorCode errorCode, Throwable th) {
    log.warn(errorMessage, th);
    throw new InvalidRequestException(errorMessage, errorCode, USER);
  }
}
