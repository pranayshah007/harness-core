/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.ACCOUNT_DOES_NOT_EXIST;
import static io.harness.eraro.ErrorCode.ACCOUNT_MIGRATED;
import static io.harness.eraro.ErrorCode.INACTIVE_ACCOUNT;
import static io.harness.eraro.ErrorCode.NOT_WHITELISTED_IP;
import static io.harness.exception.WingsException.USER;

import static software.wings.security.AuthenticationFilter.API_KEY_HEADER;

import static java.util.Arrays.asList;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.Priorities.AUTHORIZATION;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.annotations.HarnessApiKeyAuth;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.security.annotations.PublicApiWithWhitelist;
import io.harness.security.annotations.ScimAPI;

import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.Event;
import software.wings.beans.HttpMethod;
import software.wings.beans.User;
import software.wings.resources.graphql.GraphQLUtils;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UserRequestContext.UserRequestContextBuilder;
import software.wings.security.annotations.AdminPortalAuth;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.ExternalFacingApiAuth;
import software.wings.security.annotations.IdentityServiceAuth;
import software.wings.security.annotations.ListAPI;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WhitelistService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Priority;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by anubhaw on 3/11/16.
 */
@OwnedBy(PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
@Singleton
@Priority(AUTHORIZATION)
@Slf4j
public class AuthRuleFilter implements ContainerRequestFilter {
  private static final String[] NO_FILTERING_URIS_PREFIXES = new String[] {
      "users/user",
      "users/account",
      "users/sso/zendesk",
      "users/sso/canny",
      "users/two-factor-auth",
      "users/disable-two-factor-auth",
      "users/enable-two-factor-auth",
      "users/refresh-token",
      "harness-api-keys",
      "users/set-default-account",
      "users/update-externally-managed",
      "account/new",
  };
  private static final String[] NO_FILTERING_URIS_SUFFIXES = new String[] {"/logout"};
  private static final String[] EXEMPTED_URI_PREFIXES = new String[] {"limits/configure", "account/license",
      "account/export", "account/import", "account/delete/", "account/disable", "account/enable", "users/reset-cache",
      "executions/workflow-variables", "executions/nodeSubGraphs", "executions/deployment-metadata",
      "setup-as-code/yaml/internal/template-yaml-sync", "infrastructure-definitions/list",
      "usageRestrictions/references", "setup/delegates/ng/v2", "custom-dashboard/deployment-recon-per-account",
      "custom-dashboard/deployment-recon-all-accounts", "custom-dashboard/instance-recon-per-account",
      "custom-dashboard/looker-entity-recon-per-account"};
  private static final String[] EXEMPTED_URI_SUFFIXES = new String[] {"sales-contacts", "addSubdomainUrl"};
  private static final String USER_NOT_AUTHORIZED = "User not authorized";
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String DELIMITER = "::";

  @Context private ResourceInfo resourceInfo;
  @Context private HttpServletRequest servletRequest;
  @Inject AuditServiceHelper auditServiceHelper;
  @Inject FeatureFlagService featureFlagService;

  private AuthService authService;
  private AuthHandler authHandler;
  private AccountService accountService;
  private UserService userService;
  private AppService appService;
  private WhitelistService whitelistService;
  private HarnessUserGroupService harnessUserGroupService;
  private GraphQLUtils graphQLUtils;
  private ApiKeyService apiKeyService;

  /**
   * Instantiates a new Auth rule filter.
   *
   * @param authService      the auth service
   * @param appService       the appService
   * @param userService      the userService
   * @param whitelistService
   */
  @Inject
  public AuthRuleFilter(AuthService authService, AuthHandler authHandler, AppService appService,
      UserService userService, AccountService accountService, WhitelistService whitelistService,
      HarnessUserGroupService harnessUserGroupService, GraphQLUtils graphQLUtils, ApiKeyService apiKeyService,
      FeatureFlagService featureFlagService) {
    this.authService = authService;
    this.authHandler = authHandler;
    this.appService = appService;
    this.userService = userService;
    this.accountService = accountService;
    this.whitelistService = whitelistService;
    this.harnessUserGroupService = harnessUserGroupService;
    this.graphQLUtils = graphQLUtils;
    this.apiKeyService = apiKeyService;
    this.featureFlagService = featureFlagService;
  }

  private boolean isAuthFilteringExempted(String uri) {
    for (String noFilteringUri : NO_FILTERING_URIS_PREFIXES) {
      if (uri.startsWith(noFilteringUri)) {
        return true;
      }
    }
    for (String noFilteringUri : NO_FILTERING_URIS_SUFFIXES) {
      if (uri.endsWith(noFilteringUri)) {
        return true;
      }
    }
    return false;
  }

  private boolean isGraphQLRequest(String uri) {
    // GraphQL API calls
    return uri.equals("graphql") || uri.equals("graphql/int");
  }

  private boolean isExternalGraphQLRequest(String uri) {
    // External GraphQL API calls
    return uri.endsWith("graphql");
  }

  private boolean isInternalGraphQLRequest(String uri) {
    // Internal GraphQL calls used by custom dashboards.
    return uri.startsWith("graphql/int");
  }

  private boolean isHarnessUserExemptedRequest(String uri) {
    for (String exemptedUriPrefix : EXEMPTED_URI_PREFIXES) {
      if (uri.startsWith(exemptedUriPrefix)) {
        return true;
      }
    }
    for (String exemptedUriSuffix : EXEMPTED_URI_SUFFIXES) {
      if (uri.endsWith(exemptedUriSuffix)) {
        return true;
      }
    }
    return false;
  }

  /* (non-Javadoc)
   * @see javax.ws.rs.container.ContainerRequestFilter#filter(javax.ws.rs.container.ContainerRequestContext)
   */
  @Override
  public void filter(ContainerRequestContext requestContext) {
    MultivaluedMap<String, String> pathParameters = requestContext.getUriInfo().getPathParameters();
    MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();

    String accountId = getRequestParamFromContext("accountId", pathParameters, queryParameters);
    if (isNotEmpty(requestContext.getHeaderString("X-Api-Key"))) {
      if (isEmpty(accountId)) {
        accountId = apiKeyService.getAccountIdFromApiKey(requestContext.getHeaderString("X-Api-Key"));
      }
      if (isNotEmpty(accountId)) {
        ApiKeyEntry apiKeyEntry = apiKeyService.getByKey(requestContext.getHeaderString("X-Api-Key"), accountId);
        auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, apiKeyEntry, Event.Type.INVOKED);
      }
    }
    if (authorizationExemptedRequest(requestContext)) {
      return; // do nothing
    }

    if (isScimAPI()) {
      authHandler.authorizeScimApi(requestContext);
      return;
    }

    if (isDelegateRequest(requestContext) || isLearningEngineServiceRequest(requestContext)
        || isIdentityServiceRequest(requestContext) || isAdminPortalRequest(requestContext) || isNextGenManagerRequest()
        || isInternalAPI()) {
      return;
    }

    boolean isExternalApi = externalAPI();

    List<String> appIdsFromRequest = getRequestParamsFromContext("appId", pathParameters, queryParameters);
    boolean emptyAppIdsInReq = isEmpty(appIdsFromRequest);

    if (!emptyAppIdsInReq && accountId == null) {
      // Ideally, we should force all the apis to have accountId as a mandatory field and not have this code.
      // But, since there might be some paths hitting this condition already, pulling up account from the first appId.
      accountId = appService.get(appIdsFromRequest.get(0)).getAccountId();
    }

    User user = UserThreadLocal.get();

    if (isPublicApiWithWhitelist()) {
      checkForWhitelisting(accountId, FeatureName.WHITELIST_PUBLIC_API, requestContext, user);
      return;
    }

    boolean isApiKeyAuthorized = apiKeyAuthorizationAPI();
    boolean isApiKeyAuthorizedWithBearerToken = false;
    if (isApiKeyAuthorized) {
      checkForWhitelisting(accountId, FeatureName.WHITELIST_PUBLIC_API, requestContext, user);
      if (user != null) {
        isApiKeyAuthorizedWithBearerToken = true;
      } else {
        String apiKey = requestContext.getHeaderString(API_KEY_HEADER);
        // This is only possible when allowEmptyApiKey is true.
        if (isEmpty(apiKey)) {
          return;
        }

        List<PermissionAttribute> requiredPermissionAttributes =
            getApiKeyAuthorizedPermissionAttributes(requestContext);
        boolean skipAuth = skipAuth(requiredPermissionAttributes);
        user = setUserAndUserRequestContextUsingApiKey(
            accountId, requestContext, emptyAppIdsInReq, appIdsFromRequest, requiredPermissionAttributes, skipAuth);

        if (isEmpty(requiredPermissionAttributes) || allLoggedInScope(requiredPermissionAttributes)) {
          return;
        }

        boolean accountLevelPermissions = isAccountLevelPermissions(requiredPermissionAttributes);
        authorizeUser(user, requestContext, accountId, appIdsFromRequest, requiredPermissionAttributes, skipAuth,
            accountLevelPermissions);
        return;
      }
    }

    String uriPath = requestContext.getUriInfo().getPath();

    boolean graphQLRequest = isGraphQLRequest(uriPath);
    if (graphQLRequest) {
      checkForWhitelisting(accountId, FeatureName.WHITELIST_GRAPHQL, requestContext, user);
      if (isInternalGraphQLRequest(uriPath)) {
        graphQLUtils.validateGraphQLCall(accountId, true);
      } else if (isExternalGraphQLRequest(uriPath)) {
        graphQLUtils.validateGraphQLCall(accountId, false);
      }
    }

    boolean isHarnessUserExemptedRequest = isHarnessUserExemptedRequest(uriPath);
    if (isNotEmpty(accountId)) {
      validateAccountStatus(accountId, isHarnessUserExemptedRequest);
    } else if (accountId == null && isAuthFilteringExempted(uriPath)) {
      return;
    }

    if (user == null) {
      if (isExternalApi) {
        return;
      } else {
        log.warn("No user context in operation: {}", uriPath);
        throw new AccessDeniedException("No user context set", USER);
      }
    }

    String httpMethod = requestContext.getMethod();
    List<PermissionAttribute> requiredPermissionAttributes;
    boolean harnessSupportUser = false;
    if (!userService.isUserAssignedToAccount(user, accountId)) {
      if (!isHarnessUserExemptedRequest && !httpMethod.equals(HttpMethod.GET.name())) {
        if (httpMethod.equals(HttpMethod.POST.name())) {
          if (!isGraphQLRequest(uriPath)) {
            throw new AccessDeniedException(USER_NOT_AUTHORIZED, USER);
          }
        } else {
          throw new AccessDeniedException(USER_NOT_AUTHORIZED, USER);
        }
      }
      if (!harnessUserGroupService.isHarnessSupportUser(user.getUuid())
          || !harnessUserGroupService.isHarnessSupportEnabled(accountId, user.getUuid())) {
        throw new AccessDeniedException(USER_NOT_AUTHORIZED, USER);
      }
      harnessSupportUser = true;
    }

    // For graphql requests, whitelisting check is already done earlier
    if (servletRequest != null && !graphQLRequest) {
      checkForWhitelisting(accountId, null, requestContext, user);
    }
    if (isApiKeyAuthorizedWithBearerToken) {
      requiredPermissionAttributes = getApiKeyAuthorizedPermissionAttributes(requestContext);
    } else {
      requiredPermissionAttributes = getAllRequiredPermissionAttributes(requestContext);
    }
    if (isEmpty(requiredPermissionAttributes) || allLoggedInScope(requiredPermissionAttributes)) {
      UserRequestContext userRequestContext =
          buildUserRequestContext(accountId, user, emptyAppIdsInReq, harnessSupportUser);
      user.setUserRequestContext(userRequestContext);
      return;
    }

    if (accountId == null) {
      if (emptyAppIdsInReq) {
        throw new InvalidRequestException("appId not specified", USER);
      }
      throw new InvalidRequestException("accountId not specified", USER);
    }

    boolean skipAuth = skipAuth(requiredPermissionAttributes);
    boolean accountLevelPermissions = isAccountLevelPermissions(requiredPermissionAttributes);

    UserRequestContext userRequestContext = buildUserRequestContext(requiredPermissionAttributes, user, accountId,
        emptyAppIdsInReq, httpMethod, appIdsFromRequest, skipAuth, accountLevelPermissions, harnessSupportUser);
    user.setUserRequestContext(userRequestContext);

    authorizeUser(user, requestContext, accountId, appIdsFromRequest, requiredPermissionAttributes, skipAuth,
        accountLevelPermissions);
  }

  private void authorizeUser(User user, ContainerRequestContext requestContext, String accountId,
      List<String> appIdsFromRequest, List<PermissionAttribute> requiredPermissionAttributes, boolean skipAuth,
      boolean accountLevelPermissions) {
    MultivaluedMap<String, String> pathParameters = requestContext.getUriInfo().getPathParameters();
    MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();
    String httpMethod = requestContext.getMethod();

    UserRequestContext userRequestContext = user.getUserRequestContext();

    if (!skipAuth) {
      if (accountLevelPermissions) {
        authHandler.authorizeAccountPermission(userRequestContext, requiredPermissionAttributes);
      } else {
        // Handle delete and update methods
        String entityId = getEntityIdFromRequest(requiredPermissionAttributes, pathParameters, queryParameters);
        if (requestContext.getRequest().getMethod().equals(HttpMethod.PUT.name())
            || httpMethod.equals(HttpMethod.DELETE.name()) || httpMethod.equals(HttpMethod.POST.name())) {
          authService.authorize(accountId, appIdsFromRequest, entityId, user, requiredPermissionAttributes);
        } else if (httpMethod.equals(HttpMethod.GET.name())) {
          // In case of list api, the entityId would be null, we enforce restrictions in WingsMongoPersistence
          if (entityId != null) {
            // get api
            authService.authorize(accountId, appIdsFromRequest, entityId, user, requiredPermissionAttributes);
          }
        }
      }
    }
  }

  private boolean isPublicApiWithWhitelist() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(PublicApiWithWhitelist.class) != null
        || resourceClass.getAnnotation(PublicApiWithWhitelist.class) != null;
  }

  private void checkForWhitelisting(
      String accountId, FeatureName featureName, ContainerRequestContext requestContext, User user) {
    String forwardedFor = servletRequest.getHeader(X_FORWARDED_FOR);
    String remoteHost = isNotBlank(forwardedFor) ? forwardedFor : servletRequest.getRemoteHost();
    boolean isWhitelisted;
    if (featureName == null) {
      isWhitelisted = whitelistService.isValidIPAddress(accountId, remoteHost);
    } else {
      isWhitelisted = whitelistService.checkIfFeatureIsEnabledAndWhitelisting(accountId, remoteHost, featureName);
    }

    if (!isWhitelisted) {
      String msg = "Current IP Address (" + remoteHost + ") is not whitelisted.";
      log.warn(msg);
      if (requestContext.getUriInfo().getPath().contains("whitelist/isEnabled") && user != null) {
        auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, user, Event.Type.NON_WHITELISTED);
      }

      throw new WingsException(NOT_WHITELISTED_IP, USER).addParam("args", msg);
    }
  }

  private void validateAccountStatus(String accountId, boolean isHarnessUserExemptedRequest) {
    String accountStatus = accountService.getAccountStatus(accountId);
    log.debug("Testing: accountstatus for accountId {} is {}", accountId, accountStatus);
    if (AccountStatus.DELETED.equals(accountStatus)) {
      log.error("Testing: account {} does not exist with status {}", accountId, accountStatus);
      throw new WingsException(ACCOUNT_DOES_NOT_EXIST, USER);
    } else if (AccountStatus.INACTIVE.equals(accountStatus) && !isHarnessUserExemptedRequest) {
      Account account = accountService.getFromCache(accountId);
      String migratedToClusterUrl = account == null ? null : account.getMigratedToClusterUrl();
      if (migratedToClusterUrl == null) {
        throw new WingsException(INACTIVE_ACCOUNT, USER);
      } else {
        throw new WingsException(ACCOUNT_MIGRATED, USER);
      }
    }
  }

  public boolean isAccountLevelPermissions(List<PermissionAttribute> permissionAttributes) {
    return permissionAttributes.stream().anyMatch(
        permissionAttribute -> isAccountLevelPermissions(permissionAttribute.getPermissionType()));
  }

  private boolean isAccountLevelPermissions(PermissionType permissionType) {
    Set<PermissionType> accountPermissions = authHandler.getAllAccountPermissions();
    if (isNotEmpty(accountPermissions)) {
      return accountPermissions.contains(permissionType);
    }
    return false;
  }

  private String getEntityIdFromRequest(List<PermissionAttribute> permissionAttributes,
      MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
    String parameterName = getParameterName(permissionAttributes);
    if (parameterName != null) {
      return getEntityId(pathParameters, queryParameters, parameterName);
    }

    return null;
  }

  private String getParameterName(List<PermissionAttribute> permissionAttributes) {
    Optional<String> entityFieldNameOptional =
        permissionAttributes.stream()
            .map(permissionAttribute -> {
              if (StringUtils.isNotBlank(permissionAttribute.getParameterName())) {
                return permissionAttribute.getParameterName();
              }

              PermissionType permissionType = permissionAttribute.getPermissionType();

              String fieldName = null;
              if (permissionType == PermissionType.SERVICE) {
                fieldName = "serviceId";
              } else if (permissionType == PermissionType.PROVISIONER) {
                fieldName = "infraProvisionerId";
              } else if (permissionType == PermissionType.ENV) {
                fieldName = "envId";
              } else if (permissionType == PermissionType.WORKFLOW) {
                fieldName = "workflowId";
              } else if (permissionType == PermissionType.PIPELINE) {
                fieldName = "pipelineId";
              } else if (permissionType == PermissionType.DEPLOYMENT) {
                fieldName = "workflowId";
              } else if (permissionType == PermissionType.APP_TEMPLATE) {
                fieldName = "templateId";
              }

              return fieldName;
            })
            .findFirst();

    if (entityFieldNameOptional.isPresent()) {
      return entityFieldNameOptional.get();
    }

    return null;
  }

  private String getEntityId(MultivaluedMap<String, String> pathParameters,
      MultivaluedMap<String, String> queryParameters, String parameterName) {
    String entityId = queryParameters.getFirst(parameterName);
    if (entityId == null) {
      return pathParameters.getFirst(parameterName);
    }
    return entityId;
  }

  public static Set<String> getAllowedAppIds(UserPermissionInfo userPermissionInfo) {
    Set<String> allowedAppIds;

    Map<String, AppPermissionSummaryForUI> appPermissionMap = userPermissionInfo.getAppPermissionMap();

    if (MapUtils.isNotEmpty(appPermissionMap)) {
      allowedAppIds = appPermissionMap.keySet();
    } else {
      allowedAppIds = new HashSet<>();
    }
    return allowedAppIds;
  }

  private UserRequestContext buildUserRequestContext(List<PermissionAttribute> requiredPermissionAttributes, User user,
      String accountId, boolean emptyAppIdsInReq, String httpMethod, List<String> appIdsFromRequest, boolean skipAuth,
      boolean accountLevelPermissions, boolean harnessSupportUser) {
    UserRequestContext userRequestContext =
        buildUserRequestContext(accountId, user, emptyAppIdsInReq, harnessSupportUser);

    if (!accountLevelPermissions) {
      authHandler.setEntityIdFilterIfGet(httpMethod, skipAuth, requiredPermissionAttributes, userRequestContext,
          userRequestContext.isAppIdFilterRequired(), userRequestContext.getAppIds(), appIdsFromRequest);
    }
    return userRequestContext;
  }

  private UserRequestContext buildUserRequestContext(
      String accountId, User user, boolean emptyAppIdsInReq, boolean harnessSupportUser) {
    UserRequestContextBuilder userRequestContextBuilder =
        UserRequestContext.builder().accountId(accountId).entityInfoMap(new HashMap<>());

    UserPermissionInfo userPermissionInfo = authService.getUserPermissionInfo(accountId, user, false);
    userRequestContextBuilder.userPermissionInfo(userPermissionInfo);

    UserRestrictionInfo userRestrictionInfo =
        authService.getUserRestrictionInfo(accountId, user, userPermissionInfo, false);
    userRequestContextBuilder.userRestrictionInfo(userRestrictionInfo);

    Set<String> allowedAppIds = getAllowedAppIds(userPermissionInfo);
    setAppIdFilterInUserRequestContext(userRequestContextBuilder, emptyAppIdsInReq, allowedAppIds);

    userRequestContextBuilder.harnessSupportUser(harnessSupportUser);
    return userRequestContextBuilder.build();
  }

  private boolean skipAuth(List<PermissionAttribute> requiredPermissionAttributes) {
    if (CollectionUtils.isEmpty(requiredPermissionAttributes)) {
      return false;
    }

    return requiredPermissionAttributes.stream().anyMatch(PermissionAttribute::isSkipAuth);
  }

  private boolean setAppIdFilterInUserRequestContext(
      UserRequestContextBuilder userRequestContextBuilder, boolean emptyAppIdsInReq, Set<String> allowedAppIds) {
    if (!emptyAppIdsInReq) {
      return false;
    }

    List<ResourceType> requiredResourceTypes = getAllResourceTypes();
    if (isPresent(requiredResourceTypes, ResourceType.APPLICATION)) {
      userRequestContextBuilder.appIdFilterRequired(true);
      userRequestContextBuilder.appIds(allowedAppIds);
      return true;
    }

    return false;
  }

  private boolean isPresent(List<ResourceType> requiredResourceTypes, ResourceType resourceType) {
    return requiredResourceTypes.stream().anyMatch(requiredResourceType -> requiredResourceType == resourceType);
  }

  private boolean allLoggedInScope(List<PermissionAttribute> requiredPermissionAttributes) {
    return !requiredPermissionAttributes.parallelStream().anyMatch(
        permissionAttribute -> permissionAttribute.getPermissionType() != PermissionType.LOGGED_IN);
  }

  private boolean isDelegateRequest(ContainerRequestContext requestContext) {
    return delegateAPI() && startsWith(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "Delegate ");
  }

  private boolean isLearningEngineServiceRequest(ContainerRequestContext requestContext) {
    return learningEngineServiceAPI()
        && startsWith(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "LearningEngine ");
  }

  private boolean isIdentityServiceRequest(ContainerRequestContext requestContext) {
    return identityServiceAPI()
        && startsWith(
            requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), AuthenticationFilter.IDENTITY_SERVICE_PREFIX);
  }

  private boolean isAdminPortalRequest(ContainerRequestContext requestContext) {
    return adminPortalAPI()
        && startsWith(
            requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), AuthenticationFilter.ADMIN_PORTAL_PREFIX);
  }

  @VisibleForTesting
  boolean isNextGenManagerRequest() {
    return isNextGenManagerAPI();
  }

  private boolean authorizationExemptedRequest(ContainerRequestContext requestContext) {
    // externalAPI() doesn't need any authorization
    return publicAPI() || requestContext.getMethod().equals(OPTIONS) || identityServiceAPI() || harnessClientApi()
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("api/version")
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("api/swagger")
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("api/swagger.json");
  }

  private String getRequestParamFromContext(
      String key, MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
    return queryParameters.getFirst(key) != null ? queryParameters.getFirst(key) : pathParameters.getFirst(key);
  }

  private List<String> getRequestParamsFromContext(
      String key, MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
    return queryParameters.get(key) != null ? queryParameters.get(key) : pathParameters.get(key);
  }

  private boolean identityServiceAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(IdentityServiceAuth.class) != null
        || resourceClass.getAnnotation(IdentityServiceAuth.class) != null;
  }

  private boolean adminPortalAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(AdminPortalAuth.class) != null
        || resourceClass.getAnnotation(AdminPortalAuth.class) != null;
  }

  private boolean isNextGenManagerAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(NextGenManagerAuth.class) != null
        || resourceClass.getAnnotation(NextGenManagerAuth.class) != null;
  }

  private boolean publicAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(PublicApi.class) != null
        || resourceClass.getAnnotation(PublicApi.class) != null;
  }

  private boolean harnessClientApi() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(HarnessApiKeyAuth.class) != null
        || resourceClass.getAnnotation(HarnessApiKeyAuth.class) != null;
  }

  private boolean externalAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(ExternalFacingApiAuth.class) != null
        || resourceClass.getAnnotation(ExternalFacingApiAuth.class) != null;
  }

  protected boolean apiKeyAuthorizationAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(ApiKeyAuthorized.class) != null
        || resourceClass.getAnnotation(ApiKeyAuthorized.class) != null;
  }

  private boolean delegateAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(DelegateAuth.class) != null
        || resourceClass.getAnnotation(DelegateAuth.class) != null
        || resourceMethod.getAnnotation(io.harness.security.annotations.DelegateAuth2.class) != null
        || resourceClass.getAnnotation(io.harness.security.annotations.DelegateAuth2.class) != null;
  }

  private boolean learningEngineServiceAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(LearningEngineAuth.class) != null
        || resourceClass.getAnnotation(LearningEngineAuth.class) != null;
  }

  private boolean listAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(ListAPI.class) != null || resourceClass.getAnnotation(ListAPI.class) != null;
  }

  private boolean isInternalAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(InternalApi.class) != null
        || resourceClass.getAnnotation(InternalApi.class) != null;
  }

  public PermissionAttribute buildPermissionAttribute(AuthRule authRule, String httpMethod, ResourceType resourceType) {
    return new PermissionAttribute(resourceType, authRule.permissionType(), getAction(authRule, httpMethod), httpMethod,
        authRule.parameterName(), authRule.dbFieldName(), authRule.dbCollectionName(), authRule.skipAuth());
  }

  private List<PermissionAttribute> getApiKeyAuthorizedPermissionAttributes(ContainerRequestContext requestContext) {
    List<PermissionAttribute> methodPermissionAttributes = new ArrayList<>();
    List<PermissionAttribute> classPermissionAttributes = new ArrayList<>();
    String httpMethod = requestContext.getMethod();

    Method resourceMethod = resourceInfo.getResourceMethod();
    ApiKeyAuthorized[] methodAnnotations = resourceMethod.getAnnotationsByType(ApiKeyAuthorized.class);
    if (isNotEmpty(methodAnnotations)) {
      for (ApiKeyAuthorized methodAnnotation : methodAnnotations) {
        methodPermissionAttributes.add(new PermissionAttribute(null, methodAnnotation.permissionType(),
            getAction(methodAnnotation, requestContext.getMethod()), httpMethod, null, null, null,
            methodAnnotation.skipAuth()));
      }
    }

    Class<?> resourceClass = resourceInfo.getResourceClass();
    ApiKeyAuthorized[] classAnnotations = resourceClass.getAnnotationsByType(ApiKeyAuthorized.class);
    if (isNotEmpty(classAnnotations)) {
      for (ApiKeyAuthorized classAnnotation : classAnnotations) {
        classPermissionAttributes.add(new PermissionAttribute(null, classAnnotation.permissionType(),
            getAction(classAnnotation, requestContext.getMethod()), httpMethod, null, null, null,
            classAnnotation.skipAuth()));
      }
    }

    if (methodPermissionAttributes.isEmpty()) {
      return classPermissionAttributes;
    } else {
      return methodPermissionAttributes;
    }
  }

  private List<PermissionAttribute> getAllRequiredPermissionAttributes(ContainerRequestContext requestContext) {
    List<PermissionAttribute> methodPermissionAttributes = new ArrayList<>();
    List<PermissionAttribute> classPermissionAttributes = new ArrayList<>();

    Method resourceMethod = resourceInfo.getResourceMethod();
    AuthRule[] methodAnnotations = resourceMethod.getAnnotationsByType(AuthRule.class);
    if (isNotEmpty(methodAnnotations)) {
      for (AuthRule methodAnnotation : methodAnnotations) {
        methodPermissionAttributes.add(buildPermissionAttribute(methodAnnotation, requestContext.getMethod(), null));
      }
    }

    Class<?> resourceClass = resourceInfo.getResourceClass();
    AuthRule[] classAnnotations = resourceClass.getAnnotationsByType(AuthRule.class);
    if (isNotEmpty(classAnnotations)) {
      for (AuthRule classAnnotation : classAnnotations) {
        classPermissionAttributes.add(new PermissionAttribute(null, classAnnotation.permissionType(),
            getAction(classAnnotation, requestContext.getMethod()), requestContext.getMethod(),
            classAnnotation.parameterName(), classAnnotation.dbFieldName(), classAnnotation.dbCollectionName(),
            classAnnotation.skipAuth()));
      }
    }

    if (methodPermissionAttributes.isEmpty()) {
      return classPermissionAttributes;
    } else {
      return methodPermissionAttributes;
    }
  }

  private Action getAction(AuthRule authRule, String method) {
    Action action;
    if (authRule.action() == Action.DEFAULT) {
      action = getDefaultAction(method);
    } else {
      action = authRule.action();
    }
    return action;
  }

  private Action getAction(ApiKeyAuthorized apiKeyAuthorized, String method) {
    Action action;
    if (apiKeyAuthorized.action() == Action.DEFAULT) {
      action = getDefaultAction(method);
    } else {
      action = apiKeyAuthorized.action();
    }
    return action;
  }

  private Action getDefaultAction(String method) {
    if (HttpMethod.GET.name().equals(method)) {
      return Action.READ;
    } else if (HttpMethod.PUT.name().equals(method)) {
      return Action.UPDATE;
    } else if (HttpMethod.POST.name().equals(method)) {
      return Action.CREATE;
    } else if (HttpMethod.DELETE.name().equals(method)) {
      return Action.DELETE;
    }

    return Action.READ;
  }

  private List<ResourceType> getAllResourceTypes() {
    List<ResourceType> methodResourceTypes = new ArrayList<>();
    List<ResourceType> classResourceTypes = new ArrayList<>();

    Method resourceMethod = resourceInfo.getResourceMethod();
    Scope methodAnnotations = resourceMethod.getAnnotation(Scope.class);
    if (null != methodAnnotations) {
      methodResourceTypes = asList(methodAnnotations.value());
    }

    Class<?> resourceClass = resourceInfo.getResourceClass();
    Scope classAnnotations = resourceClass.getAnnotation(Scope.class);
    if (null != classAnnotations) {
      classResourceTypes = asList(classAnnotations.value());
    }

    if (methodResourceTypes.isEmpty()) {
      return classResourceTypes;
    } else {
      return methodResourceTypes;
    }
  }

  private boolean isScimAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(ScimAPI.class) != null || resourceClass.getAnnotation(ScimAPI.class) != null;
  }

  private User setUserAndUserRequestContextUsingApiKey(String accountId, ContainerRequestContext requestContext,
      boolean emptyAppIdsInReq, List<String> appIdsFromRequest, List<PermissionAttribute> requiredPermissionAttributes,
      boolean skipAuth) {
    if (accountId == null) {
      throw new InvalidRequestException("accountId not specified", USER);
    }

    String apiKey = requestContext.getHeaderString(API_KEY_HEADER);
    User user = new User();
    ApiKeyEntry apiKeyEntry = apiKeyService.getByKey(apiKey, accountId);
    if (apiKeyEntry == null) {
      throw new InvalidRequestException("Invalid Api Key provided", USER);
    }

    UserPermissionInfo apiKeyPermissions = apiKeyService.getApiKeyPermissions(apiKeyEntry, accountId);
    UserRestrictionInfo apiKeyRestrictions =
        apiKeyService.getApiKeyRestrictions(apiKeyEntry, apiKeyPermissions, accountId);
    String httpMethod = requestContext.getMethod();
    List<ResourceType> requiredResourceTypes = getAllResourceTypes();
    boolean isScopedToApp = isPresent(requiredResourceTypes, ResourceType.APPLICATION);

    if (isEmpty(requiredPermissionAttributes) || allLoggedInScope(requiredPermissionAttributes)) {
      UserRequestContext userRequestContext = buildUserRequestContext(
          apiKeyPermissions, apiKeyRestrictions, accountId, emptyAppIdsInReq, isScopedToApp, appIdsFromRequest);
      user.setUserRequestContext(userRequestContext);
      UserThreadLocal.set(user);
      return user;
    }

    boolean isAccountLevelPermissions = isAccountLevelPermissions(requiredPermissionAttributes);
    UserRequestContext userRequestContext =
        buildUserRequestContext(apiKeyPermissions, apiKeyRestrictions, requiredPermissionAttributes, accountId,
            emptyAppIdsInReq, httpMethod, appIdsFromRequest, skipAuth, isAccountLevelPermissions, isScopedToApp);
    user.setUserRequestContext(userRequestContext);
    UserThreadLocal.set(user);
    return user;
  }

  public UserRequestContext buildUserRequestContext(UserPermissionInfo userPermissionInfo,
      UserRestrictionInfo userRestrictionInfo, List<PermissionAttribute> requiredPermissionAttributes, String accountId,
      boolean emptyAppIdsInReq, String httpMethod, List<String> appIdsFromRequest, boolean skipAuth,
      boolean accountLevelPermissions, boolean isScopeToApp) {
    UserRequestContext userRequestContext = buildUserRequestContext(
        userPermissionInfo, userRestrictionInfo, accountId, emptyAppIdsInReq, isScopeToApp, appIdsFromRequest);

    if (!accountLevelPermissions) {
      authHandler.setEntityIdFilterIfGet(httpMethod, skipAuth, requiredPermissionAttributes, userRequestContext,
          userRequestContext.isAppIdFilterRequired(), userRequestContext.getAppIds(), appIdsFromRequest);
    }
    return userRequestContext;
  }

  public UserRequestContext buildUserRequestContext(UserPermissionInfo userPermissionInfo,
      UserRestrictionInfo userRestrictionInfo, String accountId, boolean emptyAppIdsInReq, boolean isScopedToApp,
      List<String> appIdsFromRequest) {
    UserRequestContextBuilder userRequestContextBuilder =
        UserRequestContext.builder().accountId(accountId).entityInfoMap(new HashMap<>());

    userRequestContextBuilder.userPermissionInfo(userPermissionInfo);
    userRequestContextBuilder.userRestrictionInfo(userRestrictionInfo);

    if (isScopedToApp) {
      Set<String> allowedAppIds = getAllowedAppIds(userPermissionInfo);
      if (emptyAppIdsInReq) {
        userRequestContextBuilder.appIdFilterRequired(true);
        userRequestContextBuilder.appIds(allowedAppIds);
      } else {
        if (isEmpty(allowedAppIds) || !allowedAppIds.containsAll(appIdsFromRequest)) {
          throw new WingsException(ErrorCode.ACCESS_DENIED);
        }
      }
    }

    return userRequestContextBuilder.build();
  }
}
