/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security;

import static io.harness.agent.AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.USER_DOES_NOT_EXIST;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.metrics.impl.ExternalApiMetricsServiceImpl.EXTERNAL_API_REQUEST_COUNT;
import static io.harness.security.JWTAuthenticationFilter.setSourcePrincipalInContext;
import static io.harness.security.JWTTokenServiceUtils.extractToken;
import static io.harness.security.JWTTokenServiceUtils.verifyJWTToken;

import static java.util.Collections.emptyMap;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.logging.AccountLogContext;
import io.harness.manage.GlobalContextManager;
import io.harness.metrics.impl.ExternalApiMetricsServiceImpl;
import io.harness.security.JWTAuthenticationFilter;
import io.harness.security.JWTTokenHandler;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.security.annotations.PublicApiWithWhitelist;
import io.harness.security.annotations.ScimAPI;
import io.harness.security.dto.Principal;
import io.harness.service.intfc.DelegateAuthService;

import software.wings.beans.AuthToken;
import software.wings.beans.User;
import software.wings.common.AuditHelper;
import software.wings.security.annotations.AdminPortalAuth;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.ExternalFacingApiAuth;
import software.wings.security.annotations.IdentityServiceAuth;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AuditService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.ExternalApiRateLimitingService;
import software.wings.service.intfc.HarnessApiKeyService;
import software.wings.service.intfc.UserService;

import com.auth0.jwt.interfaces.Claim;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Priority;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Priority(AUTHENTICATION)
@OwnedBy(PL)
@Slf4j
@TargetModule(HarnessModule._360_CG_MANAGER)
public class AuthenticationFilter implements ContainerRequestFilter {
  @VisibleForTesting public static final String API_KEY_HEADER = "X-Api-Key";
  @VisibleForTesting public static final String HARNESS_API_KEY_HEADER = "X-Harness-Api-Key";
  @VisibleForTesting public static final String USER_IDENTITY_HEADER = "X-Identity-User";
  public static final String IDENTITY_SERVICE_PREFIX = "IdentityService ";
  public static final String ADMIN_PORTAL_PREFIX = "AdminPortal ";
  public static final String NEXT_GEN_MANAGER_PREFIX = "NextGenManager ";
  private static final int NUM_MANAGERS = 3;

  @Context private ResourceInfo resourceInfo;
  private AuthService authService;
  private UserService userService;
  private AuditService auditService;
  private ApiKeyService apiKeyService;
  private HarnessApiKeyService harnessApiKeyService;
  private AuditHelper auditHelper;
  private ExternalApiRateLimitingService rateLimitingService;
  private SecretManager secretManager;
  private Map<String, String> serviceToSecretMapping;
  private Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping;

  private DelegateAuthService delegateAuthService;
  private ExternalApiMetricsServiceImpl externalApiMetricsService;

  @Inject
  public AuthenticationFilter(UserService userService, AuthService authService, AuditService auditService,
      AuditHelper auditHelper, ApiKeyService apiKeyService, HarnessApiKeyService harnessApiKeyService,
      ExternalApiRateLimitingService rateLimitingService, SecretManager secretManager,
      DelegateAuthService delegateAuthService, ExternalApiMetricsServiceImpl externalApiMetricsService) {
    this.userService = userService;
    this.authService = authService;
    this.auditService = auditService;
    this.auditHelper = auditHelper;
    this.apiKeyService = apiKeyService;
    this.harnessApiKeyService = harnessApiKeyService;
    this.rateLimitingService = rateLimitingService;
    this.secretManager = secretManager;
    this.delegateAuthService = delegateAuthService;
    this.externalApiMetricsService = externalApiMetricsService;
    serviceToSecretMapping = getServiceToSecretMapping();
    serviceToJWTTokenHandlerMapping = getServiceToJWTTokenHandlerMapping();
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

    if (learningEngineServiceAPI()) {
      validateLearningEngineRequest(containerRequestContext);
      return; // do nothing
    }

    if (isScimAPI()) {
      return;
    }

    String authorization = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

    boolean isApiKeyAuthorizeRequest = isApiKeyAuthorizationAPI();
    if (isExternalFacingApiRequest(containerRequestContext) || isApiKeyAuthorizeRequest) {
      String apiKey = containerRequestContext.getHeaderString(API_KEY_HEADER);

      if (isNotEmpty(apiKey)) {
        if (!containerRequestContext.getUriInfo().getAbsolutePath().getPath().endsWith("graphql")) {
          ensureValidQPM(containerRequestContext);
        }

        try {
          validateExternalFacingApiRequest(containerRequestContext);
          return;
        } catch (UnauthorizedException | InvalidRequestException exception) {
          if (authorization == null) {
            throw exception;
          }
        }
      }

      if (isApiKeyAuthorizeRequest && isEmpty(apiKey)) {
        if (allowEmptyApiKey()) {
          return;
        }
      }
      if (checkIfBearerTokenAndValidate(authorization, containerRequestContext)) {
        return;
      }
    }

    if (harnessApiKeyService.isHarnessClientApi(resourceInfo)) {
      harnessApiKeyService.validateHarnessClientApiRequest(resourceInfo, containerRequestContext);
      return;
    }

    if (authorization == null) {
      throw new InvalidRequestException(INVALID_TOKEN.name(), INVALID_TOKEN, USER);
    }

    GlobalContextManager.set(new GlobalContext());

    if (isAdminPortalRequest()) {
      String adminPortalToken =
          substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), ADMIN_PORTAL_PREFIX)
              .trim();
      secretManager.verifyJWTToken(adminPortalToken, JWT_CATEGORY.DATA_HANDLER_SECRET);
      return;
    }

    if (isNextGenManagerRequest(resourceInfo)) {
      validateNextGenRequest(containerRequestContext);
      return;
    }

    if (isIdentityServiceOriginatedRequest(containerRequestContext)) {
      String identityServiceToken =
          substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), IDENTITY_SERVICE_PREFIX);
      Map<String, Claim> claimMap =
          secretManager.verifyJWTToken(identityServiceToken, JWT_CATEGORY.IDENTITY_SERVICE_SECRET);
      HarnessUserAccountActions harnessUserAccountActions = secretManager.getHarnessUserAccountActions(claimMap);
      HarnessUserThreadLocal.set(harnessUserAccountActions);
      if (isAuthenticatedByIdentitySvc(containerRequestContext)) {
        SecurityContextBuilder.setContext(claimMap);

        setSourcePrincipalInContext(containerRequestContext, serviceToJWTTokenHandlerMapping, serviceToSecretMapping,
            SecurityContextBuilder.getPrincipal());

        String userId = containerRequestContext.getHeaderString(USER_IDENTITY_HEADER);
        User user = userService.getUserFromCacheOrDB(userId);
        if (user != null) {
          UserThreadLocal.set(user);
          return;
        } else {
          throw new InvalidRequestException(USER_DOES_NOT_EXIST.name(), USER_DOES_NOT_EXIST, USER);
        }
      } else if (identityServiceAPI()) {
        return;
      }
      throw new InvalidRequestException(INVALID_CREDENTIAL.name(), INVALID_CREDENTIAL, USER);
    }

    if (isInternalRequest(resourceInfo)) {
      validateInternalRequest(containerRequestContext);
      return;
    }

    // Bearer token validation is needed for environments without Gateway
    if (checkIfBearerTokenAndValidate(authorization, containerRequestContext)) {
      String accountId = null;
      if (containerRequestContext.getUriInfo() != null) {
        accountId = getRequestParamFromContext("accountId", containerRequestContext.getUriInfo().getPathParameters(),
            containerRequestContext.getUriInfo().getQueryParameters());

        if (isEmpty(accountId)) {
          accountId = getRequestParamFromContext("routingId", containerRequestContext.getUriInfo().getPathParameters(),
              containerRequestContext.getUriInfo().getQueryParameters());
        }
      }
      log.info(
          "AUTH_FILTER: Non gateway or non service-to-service bearer token validation call for account {}", accountId);
      setSourcePrincipalInContext(containerRequestContext, serviceToJWTTokenHandlerMapping, serviceToSecretMapping,
          SecurityContextBuilder.getPrincipal());
      return;
    }

    throw new InvalidRequestException(INVALID_CREDENTIAL.name(), INVALID_CREDENTIAL, USER);
  }

  protected boolean isScimAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(ScimAPI.class) != null || resourceClass.getAnnotation(ScimAPI.class) != null;
  }

  protected boolean isAuthenticatedByIdentitySvc(ContainerRequestContext containerRequestContext) {
    String value = containerRequestContext.getHeaderString(USER_IDENTITY_HEADER);
    return isNotEmpty(value);
  }

  protected boolean isAdminPortalRequest() {
    return resourceInfo.getResourceMethod().getAnnotation(AdminPortalAuth.class) != null
        || resourceInfo.getResourceClass().getAnnotation(AdminPortalAuth.class) != null;
  }

  @VisibleForTesting
  boolean isNextGenManagerRequest(ResourceInfo requestResourceInfo) {
    return requestResourceInfo.getResourceMethod().getAnnotation(NextGenManagerAuth.class) != null
        || requestResourceInfo.getResourceClass().getAnnotation(NextGenManagerAuth.class) != null;
  }

  protected boolean isInternalRequest(ResourceInfo requestResourceInfo) {
    return requestResourceInfo.getResourceMethod().getAnnotation(InternalApi.class) != null
        || requestResourceInfo.getResourceClass().getAnnotation(InternalApi.class) != null;
  }

  private void validateInternalRequest(ContainerRequestContext containerRequestContext) {
    JWTAuthenticationFilter.filter(containerRequestContext, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
  }

  @VisibleForTesting
  boolean isNextGenAuthorizationValid(ContainerRequestContext containerRequestContext) {
    String nextGenManagerToken =
        substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), NEXT_GEN_MANAGER_PREFIX)
            .trim();
    secretManager.verifyJWTToken(nextGenManagerToken, JWT_CATEGORY.NEXT_GEN_MANAGER_SECRET);
    return true;
  }

  private boolean checkIfBearerTokenAndValidate(String authHeader, ContainerRequestContext containerRequestContext) {
    if (authHeader != null && authHeader.startsWith("Bearer")) {
      User user = validateBearerToken(containerRequestContext);
      containerRequestContext.setProperty("USER", user);
      updateUserInAuditRecord(user);
      UserThreadLocal.set(user);
      setPrincipal(extractToken(containerRequestContext, "Bearer"));
      return true;
    }
    return false;
  }

  private boolean allowEmptyApiKey() {
    boolean methodAllowEmptyApiKey = false;
    boolean classAllowEmptyApiKey = false;

    Method resourceMethod = resourceInfo.getResourceMethod();
    ApiKeyAuthorized[] methodAnnotations = resourceMethod.getAnnotationsByType(ApiKeyAuthorized.class);
    if (isNotEmpty(methodAnnotations)) {
      for (ApiKeyAuthorized methodAnnotation : methodAnnotations) {
        methodAllowEmptyApiKey = methodAnnotation.allowEmptyApiKey() || methodAllowEmptyApiKey;
      }
    }

    Class<?> resourceClass = resourceInfo.getResourceClass();
    ApiKeyAuthorized[] classAnnotations = resourceClass.getAnnotationsByType(ApiKeyAuthorized.class);
    if (isNotEmpty(classAnnotations)) {
      for (ApiKeyAuthorized classAnnotation : classAnnotations) {
        classAllowEmptyApiKey = classAnnotation.allowEmptyApiKey() || classAllowEmptyApiKey;
      }
    }

    if (isEmpty(methodAnnotations)) {
      return classAllowEmptyApiKey;
    } else {
      return methodAllowEmptyApiKey;
    }
  }

  private void setPrincipal(String tokenString) {
    if (tokenString.length() > 32) {
      Map<String, Claim> claimMap = verifyJWTToken(tokenString, secretManager.getJWTSecret(JWT_CATEGORY.AUTH_SECRET));
      if (!claimMap.containsKey("exp")) {
        log.warn(this.getClass().getName() + " verifies JWT Token without Expiry Date.");
        Principal principal = SecurityContextBuilder.getPrincipalFromClaims(claimMap);
        if (principal != null) {
          log.info(String.format(
              "Principal type is %s and its name is %s", principal.getType().toString(), principal.getName()));
        }
      }
      SecurityContextBuilder.setContext(claimMap);
    }
  }

  private Map<String, String> getServiceToSecretMapping() {
    Map<String, String> mapping = new HashMap<>();
    mapping.put(DEFAULT.getServiceId(), secretManager.getJWTSecret(JWT_CATEGORY.NEXT_GEN_MANAGER_SECRET));
    return mapping;
  }

  private Map<String, JWTTokenHandler> getServiceToJWTTokenHandlerMapping() {
    return emptyMap();
  }

  private void validateNextGenRequest(ContainerRequestContext containerRequestContext) {
    JWTAuthenticationFilter.filter(containerRequestContext, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
  }

  private void ensureValidQPM(ContainerRequestContext containerRequestContext) {
    String key = containerRequestContext.getHeaderString(API_KEY_HEADER);
    String accountId = apiKeyService.getAccountIdFromApiKey(key);
    String reqPath = containerRequestContext.getUriInfo().getPath();
    externalApiMetricsService.recordApiRequestMetric(accountId, reqPath, EXTERNAL_API_REQUEST_COUNT);
    if (rateLimitingService.rateLimitRequest(key)) {
      String queryParams = containerRequestContext.getUriInfo().getQueryParameters().toString();
      log.warn("Rate Limit exceeded for QPM {}, reqPath {}, queryParams {}",
          rateLimitingService.getMaxQPMPerManager(key), reqPath, queryParams);
      throw new WebApplicationException(Response.status(429)
                                            .entity("Too Many requests. Throttled. Max QPS: "
                                                + rateLimitingService.getMaxQPMPerManager(key) * NUM_MANAGERS / 60)
                                            .build());
    }
  }

  private User validateBearerToken(ContainerRequestContext containerRequestContext) {
    String tokenString = extractToken(containerRequestContext, "Bearer");
    AuthToken authToken = authService.validateToken(tokenString);
    User user = authToken.getUser();
    if (user != null) {
      user.setToken(tokenString);
      return user;
    }
    throw new InvalidRequestException(INVALID_TOKEN.name(), INVALID_TOKEN, USER);
  }

  private void updateUserInAuditRecord(User user) {
    auditService.updateUser(auditHelper.get(), user.getPublicUser(false));
  }

  protected void validateLearningEngineRequest(ContainerRequestContext containerRequestContext) {
    String header = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

    if (isEmpty(header)) {
      throw new IllegalStateException("Invalid verification header");
    }

    authService.validateLearningEngineServiceToken(substringAfter(header, "LearningEngine "));
  }

  protected void validateExternalFacingApiRequest(ContainerRequestContext containerRequestContext) {
    String apiKey = containerRequestContext.getHeaderString(API_KEY_HEADER);
    if (isBlank(apiKey)) {
      throw new InvalidRequestException("Api Key not supplied", USER);
    }
    String accountId = getRequestParamFromContext("accountId", containerRequestContext.getUriInfo().getPathParameters(),
        containerRequestContext.getUriInfo().getQueryParameters());

    if (isEmpty(accountId)) {
      accountId = getRequestParamFromContext("routingId", containerRequestContext.getUriInfo().getPathParameters(),
          containerRequestContext.getUriInfo().getQueryParameters());
    }

    if (isEmpty(accountId)) {
      // In case of graphql, accountId comes as null. For the new version of api keys, we can get the accountId
      accountId = apiKeyService.getAccountIdFromApiKey(apiKey);
    }
    if (!apiKeyService.validate(apiKey, accountId)) {
      throw new UnauthorizedException("Invalid Api Key", USER);
    }
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

  private boolean isIdentityServiceRequest(ContainerRequestContext requestContext) {
    return identityServiceAPI()
        && startsWith(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), IDENTITY_SERVICE_PREFIX);
  }

  protected boolean isIdentityServiceOriginatedRequest(ContainerRequestContext requestContext) {
    return startsWith(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), IDENTITY_SERVICE_PREFIX);
  }

  private boolean isExternalFacingApiRequest(ContainerRequestContext requestContext) {
    return externalFacingAPI();
  }

  boolean identityServiceAPI() {
    return resourceInfo.getResourceMethod().getAnnotation(IdentityServiceAuth.class) != null
        || resourceInfo.getResourceClass().getAnnotation(IdentityServiceAuth.class) != null;
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

  protected boolean learningEngineServiceAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(LearningEngineAuth.class) != null
        || resourceClass.getAnnotation(LearningEngineAuth.class) != null;
  }

  protected boolean externalFacingAPI() {
    return resourceInfo.getResourceMethod().getAnnotation(ExternalFacingApiAuth.class) != null
        || resourceInfo.getResourceClass().getAnnotation(ExternalFacingApiAuth.class) != null;
  }

  protected boolean isApiKeyAuthorizationAPI() {
    return resourceInfo.getResourceMethod().getAnnotation(ApiKeyAuthorized.class) != null
        || resourceInfo.getResourceClass().getAnnotation(ApiKeyAuthorized.class) != null;
  }

  private String getRequestParamFromContext(
      String key, MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
    return queryParameters.getFirst(key) != null ? queryParameters.getFirst(key) : pathParameters.getFirst(key);
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
}
