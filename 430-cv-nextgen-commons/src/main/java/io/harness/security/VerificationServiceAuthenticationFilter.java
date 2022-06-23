/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.validation.Validator.notNullCheck;

import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.beans.ClientType;
import io.harness.cvng.core.services.api.VerificationServiceSecretManager;
import io.harness.cvng.utils.CVNextGenCache;
import io.harness.entity.ServiceSecretKey.ServiceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.annotations.HarnessApiKeyAuth;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.security.annotations.PublicApi;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Priority(AUTHENTICATION)
@Slf4j
public abstract class VerificationServiceAuthenticationFilter implements ContainerRequestFilter {
  private String PREFIX_BEARER = "Bearer";
  @Context private ResourceInfo resourceInfo;
  @Inject private CVNextGenCache cvNextGenCache;
  @Inject private VerificationServiceSecretManager verificationServiceSecretManager;
  @Inject private HPersistence hPersistence;

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    if (authenticationExemptedRequests(containerRequestContext)) {
      return;
    }

    if (isHarnessClientApi(resourceInfo)) {
      String apiKeyFromHeader = extractToken(containerRequestContext, PREFIX_BEARER);

      ClientType[] clientTypes = getClientTypesFromHarnessApiKeyAuth(resourceInfo);
      if (isEmpty(clientTypes)) {
        throw new WingsException(INVALID_TOKEN, USER);
      }
      if (!validateHarnessClientApiRequest(clientTypes[0], apiKeyFromHeader)) {
        throw new WingsException(INVALID_CREDENTIAL, USER);
      }
      return;
    }

    String authorization = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorization == null) {
      throw new WingsException(INVALID_TOKEN, USER);
    }

    if (isDelegateRequest(containerRequestContext)) {
      validateDelegateRequest(containerRequestContext);
      return;
    }

    if (isLearningEngineServiceRequest(containerRequestContext)) {
      validateLearningEngineServiceToken(
          substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "LearningEngine "));
      return;
    }

    throw new WingsException(INVALID_CREDENTIAL, USER);
  }

  @VisibleForTesting
  protected ClientType[] getClientTypesFromHarnessApiKeyAuth(ResourceInfo resourceInfo) {
    Method resourceMethod = resourceInfo.getResourceMethod();
    HarnessApiKeyAuth methodAnnotations = resourceMethod.getAnnotation(HarnessApiKeyAuth.class);
    if (null != methodAnnotations) {
      return methodAnnotations.clientTypes();
    }

    Class<?> resourceClass = resourceInfo.getResourceClass();
    HarnessApiKeyAuth classAnnotations = resourceClass.getAnnotation(HarnessApiKeyAuth.class);
    if (null != classAnnotations) {
      return classAnnotations.clientTypes();
    }

    return null;
  }

  @VisibleForTesting
  protected boolean isHarnessClientApi(ResourceInfo resourceInfo) {
    return resourceInfo.getResourceMethod().getAnnotation(HarnessApiKeyAuth.class) != null
        || resourceInfo.getResourceClass().getAnnotation(HarnessApiKeyAuth.class) != null;
  }

  protected boolean authenticationExemptedRequests(ContainerRequestContext requestContext) {
    return requestContext.getMethod().equals(OPTIONS) || publicAPI()
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("/version")
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("/swagger")
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("/swagger.json");
  }

  protected boolean isDelegateRequest(ContainerRequestContext requestContext) {
    return delegateAPI() && startsWith(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "Delegate ");
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
    String header = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (header.contains("Delegate")) {
      String delegateId = containerRequestContext.getHeaderString("delegateId");
      String delegateTokenName = containerRequestContext.getHeaderString("delegateTokenName");
      validateDelegateToken(accountId,
          substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "Delegate "), delegateId,
          delegateTokenName);
    } else {
      throw new IllegalStateException("Invalid header:" + header);
    }
  }

  protected String getRequestParamFromContext(
      String key, MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
    return queryParameters.getFirst(key) != null ? queryParameters.getFirst(key) : pathParameters.getFirst(key);
  }

  protected boolean publicAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(PublicApi.class) != null
        || resourceClass.getAnnotation(PublicApi.class) != null;
  }

  protected boolean isLearningEngineServiceRequest(ContainerRequestContext requestContext) {
    return learningEngineServiceAPI()
        && startsWith(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "LearningEngine ");
  }

  protected boolean learningEngineServiceAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(LearningEngineAuth.class) != null
        || resourceClass.getAnnotation(LearningEngineAuth.class) != null;
  }

  protected void validateLearningEngineServiceToken(String learningEngineServiceToken) {
    String jwtLearningEngineServiceSecret = verificationServiceSecretManager.getVerificationServiceSecretKey();
    if (StringUtils.isBlank(jwtLearningEngineServiceSecret)) {
      throw new InvalidRequestException("no secret key for service found for " + ServiceType.LEARNING_ENGINE);
    }
    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtLearningEngineServiceSecret);
      JWTVerifier verifier =
          JWT.require(algorithm).withIssuer("Harness Inc").acceptIssuedAt(TimeUnit.MINUTES.toSeconds(60)).build();
      verifier.verify(learningEngineServiceToken);
      JWT decode = JWT.decode(learningEngineServiceToken);
      if (decode.getExpiresAt().getTime() < System.currentTimeMillis()) {
        throw new WingsException(EXPIRED_TOKEN, USER_ADMIN);
      }
    } catch (JWTVerificationException ex) {
      log.warn("Error in verifying JWT token ", ex);
      throw new WingsException(INVALID_TOKEN);
    } catch (Exception ex) {
      log.warn("Error in verifying JWT token ", ex);
      throw new WingsException(ex);
    }
  }

  public abstract void validateDelegateToken(
      String accountId, String tokenString, String delegateId, String delegateTokenName);

  protected boolean validateHarnessClientApiRequest(ClientType clientType, String apiKey) {
    if (clientType == null || apiKey == null) {
      return false;
    }

    String apiKeyFromDB = get(clientType.name());
    return isNotEmpty(apiKeyFromDB) && apiKeyFromDB.equals(apiKey);
  }

  private String get(String clientType) {
    if (!ClientType.isValid(clientType)) {
      throw new InvalidArgumentsException("Invalid client type : " + clientType);
    }

    byte[] encryptedApiKey = cvNextGenCache.getApiKey(clientType);
    notNullCheck("global api key null for client type " + clientType, encryptedApiKey, USER);
    return getDecryptedKey(encryptedApiKey);
  }

  protected String getDecryptedKey(byte[] encryptedKey) {
    return new String(EncryptionUtils.decrypt(encryptedKey, null), Charset.forName("UTF-8"));
  }

  protected String extractToken(ContainerRequestContext requestContext, String prefix) {
    String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorizationHeader == null || !authorizationHeader.startsWith(prefix)) {
      throw new UnauthorizedException("Invalid token", USER_ADMIN);
    }
    return authorizationHeader.substring(prefix.length()).trim();
  }
}
