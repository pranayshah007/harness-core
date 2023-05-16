/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.TOKEN_ALREADY_REFRESHED_ONCE;
import static io.harness.eraro.ErrorCode.USER_DOES_NOT_EXIST;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.expression.ExpressionEvaluator.matchesVariablePattern;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.app.ManagerCacheRegistrar.AUTH_TOKEN_CACHE;
import static software.wings.app.ManagerCacheRegistrar.PRIMARY_CACHE_PREFIX;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.security.PermissionAttribute.Action.ABORT_WORKFLOW;
import static software.wings.security.PermissionAttribute.Action.CREATE;
import static software.wings.security.PermissionAttribute.Action.DELETE;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_PIPELINE;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_WORKFLOW;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_WORKFLOW_ROLLBACK;
import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.Action.UPDATE;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.cache.HarnessCacheManager;
import io.harness.cvng.core.services.api.VerificationServiceSecretManager;
import io.harness.entity.ServiceSecretKey.ServiceType;
import io.harness.eraro.ErrorCode;
import io.harness.event.handler.impl.segment.SegmentHandler;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AutoLogContext;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.security.dto.UserPrincipal;
import io.harness.version.VersionInfoManager;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.AuthToken;
import software.wings.beans.AuthToken.AuthTokenKeys;
import software.wings.beans.Environment;
import software.wings.beans.Event;
import software.wings.beans.Permission;
import software.wings.beans.Pipeline;
import software.wings.beans.Role;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.core.events.Login2FAEvent;
import software.wings.core.events.LoginEvent;
import software.wings.core.events.UnsuccessfulLoginEvent;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.GenericDbCache;
import software.wings.logcontext.UserLogContext;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.AppFilter;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummary.EnvInfo;
import software.wings.security.AppPermissionSummaryForUI;
import software.wings.security.JWT_CATEGORY;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.SecretManager;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserRequestInfo;
import software.wings.security.UserRestrictionInfo;
import software.wings.security.UserRestrictionInfo.UserRestrictionInfoBuilder;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.InvalidClaimException;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.Key;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@OwnedBy(PL)
public class AuthServiceImpl implements AuthService {
  private GenericDbCache dbCache;
  private HPersistence persistence;
  private UserService userService;
  private UserGroupService userGroupService;
  private HarnessCacheManager harnessCacheManager;
  private UsageRestrictionsService usageRestrictionsService;
  private Cache<String, AuthToken> authTokenCache;
  private MainConfiguration configuration;
  private VerificationServiceSecretManager verificationServiceSecretManager;
  private AuthHandler authHandler;
  private HarnessUserGroupService harnessUserGroupService;
  private SecretManager secretManager;
  private VersionInfoManager versionInfoManager;
  private ConfigurationController configurationController;
  private static final String USER_PERMISSION_CACHE_NAME = "userPermissionCache".concat(":%s");
  private static final String USER_RESTRICTION_CACHE_NAME = "userRestrictionCache".concat(":%s");
  private static final Duration TWO_HOURS = new Duration(TimeUnit.HOURS, 2);
  @Inject private ExecutorService executorService;
  @Inject private ApiKeyService apiKeyService;
  @Inject @Nullable private SegmentHandler segmentHandler;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private DelegateTokenAuthenticator delegateTokenAuthenticator;
  @Inject private OutboxService outboxService;

  @Inject
  public AuthServiceImpl(GenericDbCache dbCache, HPersistence persistence, UserService userService,
      UserGroupService userGroupService, UsageRestrictionsService usageRestrictionsService,
      HarnessCacheManager harnessCacheManager, @Named(AUTH_TOKEN_CACHE) Cache<String, AuthToken> authTokenCache,
      MainConfiguration configuration, VerificationServiceSecretManager verificationServiceSecretManager,
      AuthHandler authHandler, HarnessUserGroupService harnessUserGroupService, SecretManager secretManager,
      VersionInfoManager versionInfoManager, ConfigurationController configurationController) {
    this.dbCache = dbCache;
    this.persistence = persistence;
    this.userService = userService;
    this.userGroupService = userGroupService;
    this.usageRestrictionsService = usageRestrictionsService;
    this.harnessCacheManager = harnessCacheManager;
    this.authTokenCache = authTokenCache;
    this.configuration = configuration;
    this.verificationServiceSecretManager = verificationServiceSecretManager;
    this.authHandler = authHandler;
    this.harnessUserGroupService = harnessUserGroupService;
    this.secretManager = secretManager;
    this.versionInfoManager = versionInfoManager;
    this.configurationController = configurationController;
  }

  @UtilityClass
  public static final class Keys {
    public static final String HARNESS_EMAIL = "@harness.io";
    public static final String LOGIN_EVENT = "User Authenticated";
  }

  @Override
  public AuthToken validateToken(String tokenString) {
    if (tokenString.length() <= 32) {
      AuthToken authToken = getAuthToken(tokenString);
      if (authToken == null) {
        throw new WingsException(INVALID_TOKEN, USER);
      } else if (authToken.getExpireAt() <= System.currentTimeMillis()) {
        throw new WingsException(EXPIRED_TOKEN, USER);
      }
      return getAuthTokenWithUser(authToken);
    } else {
      return getAuthTokenWithUser(verifyToken(tokenString));
    }
  }

  private AuthToken getAuthToken(String authTokenId) {
    AuthToken authToken = null;
    if (authTokenCache != null) {
      authToken = authTokenCache.get(authTokenId);
    }

    if (authToken == null) {
      log.info("Token with prefix {} not found in cache hence fetching it from db", authTokenId.substring(0, 5));
      authToken = getAuthTokenFromDB(authTokenId);
      addAuthTokenToCache(authToken);
    }
    return authToken;
  }

  private void addAuthTokenToCache(AuthToken authToken) {
    if (authToken != null && authTokenCache != null) {
      authTokenCache.put(authToken.getUuid(), authToken);
    }
  }

  private AuthToken getAuthTokenFromDB(String tokenString) {
    return persistence.getDatastore(AuthToken.class).get(AuthToken.class, tokenString);
  }

  private AuthToken verifyToken(String tokenString) {
    AuthToken authToken = verifyJWTToken(tokenString);
    if (authToken == null) {
      throw new UnauthorizedException("Invalid auth token", USER);
    }
    return authToken;
  }

  private AuthToken getAuthTokenWithUser(AuthToken authToken) {
    User user = userService.getUserFromCacheOrDB(authToken.getUserId());
    if (user == null) {
      throw new UnauthorizedException("User not found", USER);
    }
    authToken.setUser(user);

    return authToken;
  }

  private void authorize(String accountId, String appId, String envId, User user,
      List<PermissionAttribute> permissionAttributes, UserRequestInfo userRequestInfo, boolean accountNullCheck) {
    if (!accountNullCheck) {
      if (accountId == null || dbCache.get(Account.class, accountId) == null) {
        log.error("Auth Failure: non-existing accountId: {}", accountId);
        throw new AccessDeniedException("Not authorized to access the account", USER);
      }
    }

    if (appId != null && dbCache.get(Application.class, appId) == null) {
      log.error("Auth Failure: non-existing appId: {}", appId);
      throw new AccessDeniedException("Not authorized to access the app", USER);
    }

    if (user.isAccountAdmin(accountId)) {
      return;
    }

    EnvironmentType envType = null;
    if (envId != null) {
      Environment env = dbCache.get(Environment.class, envId);
      envType = env.getEnvironmentType();
    }

    for (PermissionAttribute permissionAttribute : permissionAttributes) {
      if (!authorizeAccessType(accountId, appId, envId, envType, permissionAttribute,
              user.getRolesByAccountId(accountId), userRequestInfo)) {
        throw new AccessDeniedException("Not authorized", USER);
      }
    }
  }

  @Override
  public void authorize(String accountId, String appId, String envId, User user,
      List<PermissionAttribute> permissionAttributes, UserRequestInfo userRequestInfo) {
    authorize(accountId, appId, envId, user, permissionAttributes, userRequestInfo, true);
  }

  @Override
  public void authorize(String accountId, List<String> appIds, String envId, User user,
      List<PermissionAttribute> permissionAttributes, UserRequestInfo userRequestInfo) {
    if (accountId == null || dbCache.get(Account.class, accountId) == null) {
      log.error("Auth Failure: non-existing accountId: {}", accountId);
      throw new AccessDeniedException("Not authorized to access the account", USER);
    }

    if (appIds != null) {
      for (String appId : appIds) {
        authorize(accountId, appId, envId, user, permissionAttributes, userRequestInfo, false);
      }
    }
  }

  private void authorize(String accountId, String appId, String entityId, User user,
      List<PermissionAttribute> permissionAttributes, boolean accountNullCheck, boolean matchesAny) {
    UserPermissionInfo userPermissionInfo = authorizeAndGetUserPermissionInfo(accountId, appId, user, accountNullCheck);

    if (matchesAny) {
      boolean authorised = false;
      for (PermissionAttribute permissionAttribute : permissionAttributes) {
        if (authorizeAccessType(appId, entityId, permissionAttribute, userPermissionInfo)) {
          authorised = true;
          break;
        }
      }

      if (!authorised) {
        log.warn("User {} not authorized to access requested resource: {}", user.getName(), entityId);
        throw new AccessDeniedException("Not authorized", USER);
      }

    } else {
      for (PermissionAttribute permissionAttribute : permissionAttributes) {
        if (!authorizeAccessType(appId, entityId, permissionAttribute, userPermissionInfo)) {
          log.warn("User {} not authorized to access requested resource: {}", user.getName(), entityId);
          throw new AccessDeniedException("Not authorized", USER);
        }
      }
    }
  }

  @NotNull
  private UserPermissionInfo authorizeAndGetUserPermissionInfo(
      String accountId, String appId, User user, boolean accountNullCheck) {
    if (!accountNullCheck) {
      if (accountId == null || dbCache.get(Account.class, accountId) == null) {
        log.error("Auth Failure: non-existing accountId: {}", accountId);
        throw new AccessDeniedException("Not authorized to access the account", USER);
      }
    }

    if (appId != null && dbCache.get(Application.class, appId) == null) {
      log.error("Auth Failure: non-existing appId: {}", appId);
      throw new AccessDeniedException("Not authorized to access the app", USER);
    }

    if (user == null) {
      log.error("No user context for authorization request for app: {}", appId);
      throw new AccessDeniedException("Access Denied", USER);
    }

    UserRequestContext userRequestContext = user.getUserRequestContext();
    if (userRequestContext == null) {
      log.error("User Request Context null for User {}", user.getName());
      throw new AccessDeniedException("Access Denied", USER);
    }

    UserPermissionInfo userPermissionInfo = userRequestContext.getUserPermissionInfo();
    if (userPermissionInfo == null) {
      log.error("User permission info null for User {}", user.getName());
      throw new AccessDeniedException("Access Denied", USER);
    }
    return userPermissionInfo;
  }

  @Override
  public void authorize(
      String accountId, String appId, String entityId, User user, List<PermissionAttribute> permissionAttributes) {
    authorize(accountId, appId, entityId, user, permissionAttributes, true, false);
  }

  @Override
  public void authorize(String accountId, String appId, String entityId, User user,
      List<PermissionAttribute> permissionAttributes, boolean matchesAny) {
    authorize(accountId, appId, entityId, user, permissionAttributes, true, matchesAny);
  }

  @Override
  public void authorize(String accountId, List<String> appIds, String entityId, User user,
      List<PermissionAttribute> permissionAttributes) {
    if (accountId == null || dbCache.get(Account.class, accountId) == null) {
      log.error("Auth Failure: non-existing accountId: {}", accountId);
      throw new AccessDeniedException("Not authorized to access the account", USER);
    }

    if (appIds != null) {
      for (String appId : appIds) {
        authorize(accountId, appId, entityId, user, permissionAttributes, false, false);
      }
    }
  }

  @Override
  public void validateExternalServiceToken(String accountId, String externalServiceToken) {
    String jwtExternalServiceSecret = configuration.getPortal().getJwtExternalServiceSecret();
    if (isBlank(jwtExternalServiceSecret)) {
      throw new InvalidRequestException("incorrect portal setup");
    }
    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtExternalServiceSecret);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer("Harness Inc").build();
      verifier.verify(externalServiceToken);
      JWT decode = JWT.decode(externalServiceToken);
      if (decode.getExpiresAt().getTime() < System.currentTimeMillis()) {
        throw new WingsException(EXPIRED_TOKEN, USER_ADMIN);
      }
    } catch (Exception ex) {
      log.warn("Error in verifying JWT token ", ex);
      throw ex instanceof JWTVerificationException ? new WingsException(INVALID_TOKEN) : new WingsException(ex);
    }
  }

  @Override
  public void validateLearningEngineServiceToken(String learningEngineServiceToken) {
    String jwtLearningEngineServiceSecret = verificationServiceSecretManager.getVerificationServiceSecretKey();
    if (StringUtils.isBlank(jwtLearningEngineServiceSecret)) {
      throw new InvalidRequestException("no secret key for service found for " + ServiceType.LEARNING_ENGINE);
    }
    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtLearningEngineServiceSecret);
      JWTVerifier verifier =
          JWT.require(algorithm).withIssuer("Harness Inc").acceptIssuedAt(TimeUnit.MINUTES.toSeconds(10)).build();
      verifier.verify(learningEngineServiceToken);
      JWT decode = JWT.decode(learningEngineServiceToken);
      if (decode.getExpiresAt().getTime() < System.currentTimeMillis()) {
        throw new WingsException(EXPIRED_TOKEN, USER_ADMIN);
      }
    } catch (Exception ex) {
      log.warn("Error in verifying JWT token ", ex);
      throw ex instanceof JWTVerificationException ? new WingsException(INVALID_TOKEN) : new WingsException(ex);
    }
  }

  @Override
  public void invalidateAllTokensForUser(String userId) {
    List<Key<AuthToken>> keyList =
        persistence.createQuery(AuthToken.class, excludeAuthority).filter(AuthTokenKeys.userId, userId).asKeyList();
    keyList.forEach(authToken -> invalidateToken(authToken.getId().toString()));
  }

  @Override
  public void invalidateToken(String utoken) {
    AuthToken authToken = validateToken(utoken);
    if (authToken != null) {
      persistence.delete(AuthToken.class, authToken.getUuid());
      if (authTokenCache != null) {
        authTokenCache.remove(authToken.getUuid());
      }
    }
  }

  private boolean authorizeAccessType(String accountId, String appId, String envId, EnvironmentType envType,
      PermissionAttribute permissionAttribute, List<Role> roles, UserRequestInfo userRequestInfo) {
    if (isEmpty(roles)) {
      return false;
    }
    return roles.stream()
        .filter(role
            -> roleAuthorizedWithAccessType(
                role, permissionAttribute, accountId, appId, envId, envType, userRequestInfo))
        .findFirst()
        .isPresent();
  }

  private boolean roleAuthorizedWithAccessType(Role role, PermissionAttribute permissionAttribute, String accountId,
      String appId, String envId, EnvironmentType envType, UserRequestInfo userRequestInfo) {
    if (role.getPermissions() == null) {
      return false;
    }

    Action reqAction = permissionAttribute.getAction();
    PermissionType permissionType = permissionAttribute.getPermissionType();

    for (Permission permission : role.getPermissions()) {
      if (permission.getPermissionScope() != permissionType
          || (permission.getAction() != Action.ALL && reqAction != permission.getAction())) {
        continue;
      }
      if (permissionType == PermissionType.APP) {
        if (userRequestInfo != null
            && (userRequestInfo.isAllAppsAllowed() || userRequestInfo.getAllowedAppIds().contains(appId))) {
          return true;
        }
        if (permission.getAppId() != null
            && (permission.getAppId().equals(GLOBAL_APP_ID) || permission.getAppId().equals(appId))) {
          return true;
        }
      } else if (permissionType == PermissionType.ENV) {
        if (userRequestInfo != null
            && (userRequestInfo.isAllEnvironmentsAllowed() || userRequestInfo.getAllowedEnvIds().contains(envId))) {
          return true;
        }

        if (permission.getEnvironmentType() != null && permission.getEnvironmentType() == envType) {
          return true;
        }

        if (permission.getEnvId() != null
            && (permission.getEnvId().equals(GLOBAL_ENV_ID) || permission.getEnvId().equals(envId))) {
          return true;
        }
      }
    }

    return false;
  }

  private Cache<String, UserPermissionInfo> getUserPermissionCache(String accountId) {
    if (configurationController.isPrimary()) {
      return harnessCacheManager.getCache(String.format(PRIMARY_CACHE_PREFIX + USER_PERMISSION_CACHE_NAME, accountId),
          String.class, UserPermissionInfo.class, CreatedExpiryPolicy.factoryOf(TWO_HOURS));
    }
    return harnessCacheManager.getCache(String.format(USER_PERMISSION_CACHE_NAME, accountId), String.class,
        UserPermissionInfo.class, CreatedExpiryPolicy.factoryOf(TWO_HOURS),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  private Cache<String, UserRestrictionInfo> getUserRestrictionCache(String accountId) {
    if (configurationController.isPrimary()) {
      return harnessCacheManager.getCache(String.format(PRIMARY_CACHE_PREFIX + USER_RESTRICTION_CACHE_NAME, accountId),
          String.class, UserRestrictionInfo.class, AccessedExpiryPolicy.factoryOf(Duration.ONE_HOUR));
    }
    return harnessCacheManager.getCache(String.format(USER_RESTRICTION_CACHE_NAME, accountId), String.class,
        UserRestrictionInfo.class, AccessedExpiryPolicy.factoryOf(Duration.ONE_HOUR),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Override
  public UserPermissionInfo getUserPermissionInfo(String accountId, User user, boolean cacheOnly) {
    Cache<String, UserPermissionInfo> userPermissionInfoCache = getUserPermissionCache(accountId);
    if (userPermissionInfoCache == null) {
      if (cacheOnly) {
        return null;
      }
      log.error("UserInfoCache is null. This should not happen. Fall back to DB");
      return getUserPermissionInfoFromDB(accountId, user);
    }
    String key = user.getUuid();
    UserPermissionInfo value;
    try {
      value = userPermissionInfoCache.get(key);
      if (value == null) {
        if (cacheOnly) {
          return null;
        }

        // if value is empty, then we should have a fallback!
        value = getUserPermissionInfoFromDB(accountId, user);
        // Add a log message when UserPermissionInfo is empty. Context: PL-32390
        if (isUserPermissionInfoEmpty(value)) {
          log.warn("Adding empty user permission for accountId: {} and user: {}", accountId, user.getEmail());
        }
        userPermissionInfoCache.put(key, value);
      }
      return value;
    } catch (Exception e) {
      log.warn("Error in fetching user UserPermissionInfo from Cache for key:" + key, e);
    }

    // not found in cache. cache write through failed as well. rebuild anyway
    return getUserPermissionInfoFromDB(accountId, user);
  }

  @Override
  public UserRestrictionInfo getUserRestrictionInfo(
      String accountId, User user, UserPermissionInfo userPermissionInfo, boolean cacheOnly) {
    Cache<String, UserRestrictionInfo> userRestrictionInfoCache = getUserRestrictionCache(accountId);
    if (userRestrictionInfoCache == null) {
      if (cacheOnly) {
        return null;
      }
      log.error("UserInfoCache is null. This should not happen. Fall back to DB");
      return getUserRestrictionInfoFromDB(accountId, user, userPermissionInfo);
    }

    String key = user.getUuid();
    UserRestrictionInfo value;
    try {
      value = userRestrictionInfoCache.get(key);
      if (value == null) {
        if (cacheOnly) {
          return null;
        }
        value = getUserRestrictionInfoFromDB(accountId, user, userPermissionInfo);
        userRestrictionInfoCache.put(key, value);
      }
      return value;
    } catch (Exception e) {
      log.warn("Error in fetching user UserPermissionInfo from Cache for key:" + key, e);
    }

    // not found in cache. cache write through failed as well. rebuild anyway
    return getUserRestrictionInfoFromDB(accountId, user, userPermissionInfo);
  }

  public void updateUserPermissionCacheInfo(String accountId, User user, boolean cacheOnly) {
    Cache<String, UserPermissionInfo> userPermissionInfoCache = getUserPermissionCache(accountId);
    if (userPermissionInfoCache == null && cacheOnly) {
      return;
    }
    String key = user.getUuid();
    UserPermissionInfo value;
    try {
      value = userPermissionInfoCache.get(key);
      if (value == null && cacheOnly) {
        return;
      }

      value = getUserPermissionInfoFromDB(accountId, user);
      // Add a log message when UserPermissionInfo is empty. Context: PL-32390
      if (isUserPermissionInfoEmpty(value)) {
        log.warn("Updating empty user permission for accountId: {} and user: {}", accountId, user.getEmail());
      }
      userPermissionInfoCache.put(key, value);

    } catch (Exception e) {
      log.warn("Error in fetching user while updating UserPermissionInfo from Cache of accountId: " + accountId
              + " userId: " + key,
          e);
    }
  }

  public void updateUserRestrictionCacheInfo(
      String accountId, User user, UserPermissionInfo userPermissionInfo, boolean cacheOnly) {
    Cache<String, UserRestrictionInfo> userRestrictionInfoCache = getUserRestrictionCache(accountId);
    if (userRestrictionInfoCache == null) {
      if (cacheOnly) {
        return;
      }
      log.error("UserInfoCache is null. This should not happen. Fall back to DB");
    }
    String key = user.getUuid();
    UserRestrictionInfo value;
    try {
      value = userRestrictionInfoCache.get(key);
      if (value == null) {
        if (cacheOnly) {
          return;
        }
      }
      value = getUserRestrictionInfoFromDB(accountId, user, userPermissionInfo);
      userRestrictionInfoCache.put(key, value);
    } catch (Exception e) {
      log.warn("Error in fetching user while updating UserRestrictionInfo from Cache of accountId: " + accountId
              + " userId: " + key,
          e);
    }
  }

  @Override
  public void evictUserPermissionAndRestrictionCacheForAccount(
      String accountId, boolean rebuildUserPermissionInfo, boolean rebuildUserRestrictionInfo) {
    getUserPermissionCache(accountId).clear();
    getUserRestrictionCache(accountId).clear();
    apiKeyService.evictAndRebuildPermissionsAndRestrictions(
        accountId, rebuildUserPermissionInfo || rebuildUserRestrictionInfo);
  }

  @Override
  public void evictUserPermissionCacheForAccount(String accountId, boolean rebuildUserPermissionInfo) {
    getUserPermissionCache(accountId).clear();
    apiKeyService.evictAndRebuildPermissions(accountId, rebuildUserPermissionInfo);
  }

  private <T> void removeFromCache(Cache<String, T> cache, List<String> memberIds) {
    if (cache != null && isNotEmpty(memberIds)) {
      Set<String> keys = new HashSet<>(memberIds);
      cache.removeAll(keys);
    }
  }

  @Override
  public void evictUserPermissionAndRestrictionCacheForAccounts(Set<String> accountIds, List<String> memberIds) {
    if (isEmpty(accountIds)) {
      return;
    }
    accountIds.forEach(accountId -> evictUserPermissionAndRestrictionCacheForAccount(accountId, memberIds));
  }

  @Override
  public void evictPermissionAndRestrictionCacheForUserGroup(UserGroup userGroup) {
    evictUserPermissionAndRestrictionCacheForAccount(userGroup.getAccountId(), userGroup.getMemberIds());
    apiKeyService.evictPermissionsAndRestrictionsForUserGroup(userGroup);
  }

  @Override
  public void evictUserPermissionAndRestrictionCacheForAccount(String accountId, List<String> memberIds) {
    removeFromCache(getUserPermissionCache(accountId), memberIds);
    removeFromCache(getUserRestrictionCache(accountId), memberIds);
  }

  private UserPermissionInfo getUserPermissionInfoFromDB(String accountId, User user) {
    List<UserGroup> userGroups = getUserGroups(accountId, user);
    if (userGroups.isEmpty()) {
      log.info("Attempting to evaluate user permission info with empty user-groups list. AccountId: {} User: {}",
          accountId, user.getEmail());
    }
    return authHandler.evaluateUserPermissionInfo(accountId, userGroups, user);
  }

  public List<UserGroup> getUserGroups(String accountId, User user) {
    List<UserGroup> userGroups = userGroupService.listByAccountId(accountId, user, false);

    if (isEmpty(userGroups) && !userService.isUserAssignedToAccount(user, accountId)) {
      // Check if its a harness user
      Optional<UserGroup> harnessUserGroup = getHarnessUserGroupsByAccountId(accountId, user);
      if (harnessUserGroup.isPresent()) {
        userGroups = Lists.newArrayList(harnessUserGroup.get());
      }
    }
    return userGroups;
  }

  private UserRestrictionInfo getUserRestrictionInfoFromDB(
      String accountId, User user, UserPermissionInfo userPermissionInfo) {
    List<UserGroup> userGroups = getUserGroups(accountId, user);
    return getUserRestrictionInfoFromDB(accountId, userPermissionInfo, userGroups);
  }

  @Override
  public UserRestrictionInfo getUserRestrictionInfoFromDB(
      String accountId, UserPermissionInfo userPermissionInfo, List<UserGroup> userGroupList) {
    UserRestrictionInfoBuilder userRestrictionInfoBuilder = UserRestrictionInfo.builder();

    // Restrictions for update permissions
    userRestrictionInfoBuilder.appEnvMapForUpdateAction(
        usageRestrictionsService.getAppEnvMapFromUserPermissions(accountId, userPermissionInfo, UPDATE));
    userRestrictionInfoBuilder.usageRestrictionsForUpdateAction(
        usageRestrictionsService.getUsageRestrictionsFromUserPermissions(accountId, UPDATE, userGroupList));

    // Restrictions for read permissions
    userRestrictionInfoBuilder.appEnvMapForReadAction(
        usageRestrictionsService.getAppEnvMapFromUserPermissions(accountId, userPermissionInfo, READ));
    userRestrictionInfoBuilder.usageRestrictionsForReadAction(
        usageRestrictionsService.getUsageRestrictionsFromUserPermissions(accountId, READ, userGroupList));

    return userRestrictionInfoBuilder.build();
  }

  private Optional<UserGroup> getHarnessUserGroupsByAccountId(String accountId, User user) {
    if (featureFlagService.isEnabled(FeatureName.LIMITED_ACCESS_FOR_HARNESS_USER_GROUP, accountId)) {
      if (!harnessUserGroupService.isHarnessSupportEnabled(accountId, user.getUuid())) {
        return Optional.empty();
      }
    } else {
      if (!harnessUserGroupService.isHarnessSupportUser(user.getUuid())
          || !harnessUserGroupService.isHarnessSupportEnabled(accountId, user.getUuid())) {
        return Optional.empty();
      }
    }

    AppPermission appPermission = AppPermission.builder()
                                      .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
                                      .permissionType(PermissionType.ALL_APP_ENTITIES)
                                      .actions(Sets.newHashSet(READ, UPDATE, DELETE, CREATE, EXECUTE_PIPELINE,
                                          EXECUTE_WORKFLOW, EXECUTE_WORKFLOW_ROLLBACK, ABORT_WORKFLOW))
                                      .build();

    AccountPermissions accountPermissions =
        AccountPermissions.builder().permissions(authHandler.getDefaultEnabledAccountPermissions()).build();
    UserGroup userGroup = UserGroup.builder()
                              .accountId(accountId)
                              .accountPermissions(accountPermissions)
                              .appPermissions(Sets.newHashSet(appPermission))
                              .build();
    return Optional.of(userGroup);
  }

  private boolean authorizeAccessType(String appId, String entityId, PermissionAttribute requiredPermissionAttribute,
      UserPermissionInfo userPermissionInfo) {
    if (requiredPermissionAttribute.isSkipAuth()) {
      return true;
    }
    Action requiredAction = requiredPermissionAttribute.getAction();
    PermissionType requiredPermissionType = requiredPermissionAttribute.getPermissionType();

    Map<String, AppPermissionSummary> appPermissionMap = userPermissionInfo.getAppPermissionMapInternal();
    AppPermissionSummary appPermissionSummary = appPermissionMap.get(appId);

    if (appPermissionSummary == null) {
      return false;
    }

    if (CREATE == requiredAction) {
      if (requiredPermissionType == PermissionType.SERVICE) {
        return appPermissionSummary.isCanCreateService();
      } else if (requiredPermissionType == PermissionType.PROVISIONER) {
        return appPermissionSummary.isCanCreateProvisioner();
      } else if (requiredPermissionType == PermissionType.ENV) {
        return appPermissionSummary.isCanCreateEnvironment();
      } else if (requiredPermissionType == PermissionType.WORKFLOW) {
        return appPermissionSummary.isCanCreateWorkflow();
      } else if (requiredPermissionType == PermissionType.PIPELINE) {
        return appPermissionSummary.isCanCreatePipeline();
      } else if (requiredPermissionType == PermissionType.APP_TEMPLATE) {
        return appPermissionSummary.isCanCreateTemplate();
      } else {
        String msg = "Unsupported app permission entity type: " + requiredPermissionType;
        log.error(msg);
        throw new WingsException(msg);
      }
    }

    Map<Action, Set<String>> actionEntityIdMap;

    if (requiredPermissionType == PermissionType.SERVICE) {
      actionEntityIdMap = appPermissionSummary.getServicePermissions();
    } else if (requiredPermissionType == PermissionType.PROVISIONER) {
      actionEntityIdMap = appPermissionSummary.getProvisionerPermissions();
    } else if (requiredPermissionType == PermissionType.ENV) {
      Map<Action, Set<EnvInfo>> actionEnvPermissionsMap = appPermissionSummary.getEnvPermissions();
      if (isEmpty(actionEnvPermissionsMap)) {
        return false;
      }

      Set<EnvInfo> envInfoSet = actionEnvPermissionsMap.get(requiredAction);
      if (isEmpty(envInfoSet)) {
        return false;
      }

      Set<String> envIdSet = envInfoSet.stream().map(EnvInfo::getEnvId).collect(toSet());
      return envIdSet.contains(entityId);

    } else if (requiredPermissionType == PermissionType.WORKFLOW) {
      actionEntityIdMap = appPermissionSummary.getWorkflowPermissions();
    } else if (requiredPermissionType == PermissionType.PIPELINE) {
      actionEntityIdMap = appPermissionSummary.getPipelinePermissions();
    } else if (requiredPermissionType == PermissionType.DEPLOYMENT) {
      actionEntityIdMap = appPermissionSummary.getDeploymentPermissions();
    } else if (requiredPermissionType == PermissionType.APP_TEMPLATE) {
      actionEntityIdMap = appPermissionSummary.getTemplatePermissions();
    } else {
      String msg = "Unsupported app permission entity type: " + requiredPermissionType;
      log.error(msg);
      throw new WingsException(msg);
    }

    if (isEmpty(actionEntityIdMap)) {
      return false;
    }

    Collection<String> entityIds = actionEntityIdMap.get(requiredAction);
    if (isEmpty(entityIds)) {
      return false;
    }

    return entityIds.contains(entityId);
  }

  private AuthToken verifyJWTToken(String token) {
    String jwtPasswordSecret = secretManager.getJWTSecret(JWT_CATEGORY.AUTH_SECRET);
    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer("Harness Inc").build();
      verifier.verify(token);
      String authToken = JWT.decode(token).getClaim("authToken").asString();
      return getAuthToken(authToken);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new GeneralException("JWTToken validation failed", exception);
    } catch (JWTDecodeException | SignatureVerificationException | InvalidClaimException e) {
      throw new WingsException(INVALID_CREDENTIAL, USER, e)
          .addParam("message", "Invalid JWTToken received, failed to decode the token");
    }
  }

  @Override
  public User refreshToken(String oldToken) {
    if (oldToken.length() <= 32) {
      AuthToken authToken = getAuthToken(oldToken);
      if (authToken == null) {
        throw new WingsException(EXPIRED_TOKEN, USER);
      }

      User user = getUserFromAuthToken(authToken);
      user.setToken(authToken.getUuid());
      return user;
    }

    AuthToken authToken = verifyToken(oldToken);
    if (authToken.isRefreshed()) {
      throw new WingsException(TOKEN_ALREADY_REFRESHED_ONCE, USER);
    }
    User user = getUserFromAuthToken(authToken);
    authToken.setRefreshed(true);
    saveAuthToken(authToken);
    addAuthTokenToCache(authToken);
    return generateBearerTokenForUser(user);
  }

  private String saveAuthToken(AuthToken authToken) {
    return persistence.save(authToken);
  }

  private User getUserFromAuthToken(AuthToken authToken) {
    User user = userService.getUserFromCacheOrDB(authToken.getUserId());
    if (user == null) {
      log.warn("No user found for userId:" + authToken.getUserId());
      throw new WingsException(USER_DOES_NOT_EXIST, USER);
    }
    return user;
  }

  @Override
  public User generateBearerTokenForUser(User user) {
    String acctId = user == null ? null : user.getDefaultAccountId();
    String uuid = user == null ? null : user.getUuid();
    try (AutoLogContext ignore = new UserLogContext(acctId, uuid, OVERRIDE_ERROR)) {
      log.info("Generating bearer token");
      AuthToken authToken = new AuthToken(
          user.getLastAccountId(), user.getUuid(), configuration.getPortal().getAuthTokenExpiryInMillis());
      authToken.setJwtToken(UUID.randomUUID().toString());
      saveAuthToken(authToken);
      boolean isFirstLogin = user.getLastLogin() == 0L;
      user.setLastLogin(System.currentTimeMillis());
      userService.update(user);

      userService.evictUserFromCache(user.getUuid());
      user.setToken(
          generateJWTSecret(authToken, user.getEmail(), user.getName())); // this is used by UI to get resource.token

      user.setFirstLogin(isFirstLogin);
      if (!user.getEmail().endsWith(Keys.HARNESS_EMAIL)) {
        executorService.submit(() -> {
          String accountId = user.getLastAccountId();
          if (isEmpty(accountId)) {
            log.warn("last accountId is null for User {}", user.getUuid());
            return;
          }

          Account account = dbCache.get(Account.class, accountId);
          if (account == null) {
            log.warn("last account is null for User {}", user.getUuid());
            return;
          }
          try {
            if (segmentHandler != null) {
              Map<String, String> properties = new HashMap<>();
              properties.put(SegmentHandler.Keys.GROUP_ID, accountId);

              Map<String, Boolean> integrations = new HashMap<>();
              integrations.put(SegmentHandler.Keys.NATERO, true);
              integrations.put(SegmentHandler.Keys.SALESFORCE, false);

              segmentHandler.reportTrackEvent(account, Keys.LOGIN_EVENT, user, properties, integrations);
            }
          } catch (Exception e) {
            log.error("Exception while reporting track event for User {} login", user.getUuid(), e);
          }
        });
      }

      return user;
    }
  }

  private String generateJWTSecret(AuthToken authToken, String email, String username) {
    String jwtAuthSecret = secretManager.getJWTSecret(JWT_CATEGORY.AUTH_SECRET);
    int duration = JWT_CATEGORY.AUTH_SECRET.getValidityDuration();
    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtAuthSecret);
      JWTCreator.Builder jwtBuilder = JWT.create()
                                          .withIssuer("Harness Inc")
                                          .withIssuedAt(new Date())
                                          .withExpiresAt(new Date(System.currentTimeMillis() + duration))
                                          .withClaim("authToken", authToken.getUuid())
                                          .withClaim("usrId", authToken.getUserId())
                                          .withClaim("env", configuration.getEnvPath());
      // User Principal needed in token for environments without gateway as this token will be sent back to different
      // microservices
      addUserPrincipal(authToken.getUserId(), email, username, jwtBuilder, authToken.getAccountId());
      return jwtBuilder.sign(algorithm);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new GeneralException("JWTToken could not be generated", exception);
    }
  }

  private void addUserPrincipal(
      String userId, String email, String username, JWTCreator.Builder jwtBuilder, String accountId) {
    UserPrincipal userPrincipal = new UserPrincipal(userId, email, username, accountId);
    Map<String, String> userClaims = userPrincipal.getJWTClaims();
    userClaims.forEach(jwtBuilder::withClaim);
  }

  @Override
  public void checkIfUserAllowedToDeployToEnv(String appId, String envId) {
    if (isEmpty(envId)) {
      return;
    }

    User user = UserThreadLocal.get();
    if (user == null) {
      return;
    }

    Set<String> deploymentEnvExecutePermissions = user.getUserRequestContext()
                                                      .getUserPermissionInfo()
                                                      .getAppPermissionMapInternal()
                                                      .get(appId)
                                                      .getDeploymentExecutePermissionsForEnvs();

    if (isEmpty(deploymentEnvExecutePermissions)) {
      throw new AccessDeniedException("Not authorized", USER);
    }

    if (!deploymentEnvExecutePermissions.contains(envId)) {
      throw new AccessDeniedException("Not authorized", USER);
    }
  }

  @Override
  public void checkIfUserAllowedToDeployWorkflowToEnv(String appId, String envId) {
    if (isEmpty(envId)) {
      return;
    }

    User user = UserThreadLocal.get();
    if (user == null) {
      return;
    }

    Set<String> workflowExecutePermissionsForEnvs = user.getUserRequestContext()
                                                        .getUserPermissionInfo()
                                                        .getAppPermissionMapInternal()
                                                        .get(appId)
                                                        .getWorkflowExecutePermissionsForEnvs();

    if (isEmpty(workflowExecutePermissionsForEnvs) || !workflowExecutePermissionsForEnvs.contains(envId)) {
      throw new InvalidRequestException(
          "User doesn't have rights to execute Workflow in this Environment", ErrorCode.ACCESS_DENIED, USER);
    }
  }

  @Override
  public void checkIfUserAllowedToDeployPipelineToEnv(String appId, String envId) {
    if (isEmpty(envId)) {
      return;
    }

    User user = UserThreadLocal.get();
    if (user == null) {
      return;
    }

    Set<String> pipelineExecutePermissionsForEnvs = user.getUserRequestContext()
                                                        .getUserPermissionInfo()
                                                        .getAppPermissionMapInternal()
                                                        .get(appId)
                                                        .getPipelineExecutePermissionsForEnvs();

    if (isEmpty(pipelineExecutePermissionsForEnvs) || !pipelineExecutePermissionsForEnvs.contains(envId)) {
      throw new InvalidRequestException(
          "User doesn't have rights to execute Pipeline in this Environment", ErrorCode.ACCESS_DENIED, USER);
    }
  }

  @Override
  public void checkIfUserAllowedToRollbackWorkflowToEnv(String appId, String envId) {
    if (isEmpty(envId)) {
      return;
    }

    User user = UserThreadLocal.get();
    if (user == null) {
      throw new InvalidRequestException("User not found", USER);
    }

    Set<String> rollbackWorkflowExecutePermissionsForEnvs = user.getUserRequestContext()
                                                                .getUserPermissionInfo()
                                                                .getAppPermissionMapInternal()
                                                                .get(appId)
                                                                .getRollbackWorkflowExecutePermissionsForEnvs();

    if (isEmpty(rollbackWorkflowExecutePermissionsForEnvs)
        || !rollbackWorkflowExecutePermissionsForEnvs.contains(envId)) {
      throw new InvalidRequestException(
          "User doesn't have rights to rollback Workflow in this Environment", ErrorCode.ACCESS_DENIED, USER);
    }
  }

  @Override
  public void checkIfUserAllowedToAbortWorkflowToEnv(String appId, String envId) {
    if (isEmpty(envId)) {
      return;
    }
    User user = UserThreadLocal.get();
    if (user == null) {
      throw new InvalidRequestException("User not found", USER);
    }
    Set<String> abortWorkflowExecutePermissionsForEnvs = user.getUserRequestContext()
                                                             .getUserPermissionInfo()
                                                             .getAppPermissionMapInternal()
                                                             .get(appId)
                                                             .getAbortWorkflowExecutePermissionsForEnvs();
    if (isEmpty(abortWorkflowExecutePermissionsForEnvs) || !abortWorkflowExecutePermissionsForEnvs.contains(envId)) {
      throw new InvalidRequestException(
          "User doesn't have rights to abort Workflow in this Environment", ErrorCode.ACCESS_DENIED, USER);
    }
  }

  @Override
  public void checkIfUserCanCreateEnv(String appId, EnvironmentType envType) {
    User user = UserThreadLocal.get();
    if (user == null) {
      return;
    }

    if (envType == null) {
      throw new WingsException("No environment type specified", USER);
    }

    Set<EnvironmentType> envCreatePermissions = user.getUserRequestContext()
                                                    .getUserPermissionInfo()
                                                    .getAppPermissionMapInternal()
                                                    .get(appId)
                                                    .getEnvCreatePermissionsForEnvTypes();

    if (isEmpty(envCreatePermissions)) {
      throw new AccessDeniedException("Access Denied", USER);
    }

    if (envCreatePermissions.contains(EnvironmentType.ALL)) {
      return;
    }

    if (!envCreatePermissions.contains(envType)) {
      throw new AccessDeniedException("Access Denied", USER);
    }
  }

  @Override
  public void checkWorkflowPermissionsForEnv(String appId, Workflow workflow, Action action) {
    if (workflow == null) {
      return;
    }

    boolean envTemplatized = authHandler.isEnvTemplatized(workflow);
    String envId = workflow.getEnvId();

    if (!envTemplatized && isEmpty(envId)) {
      return;
    }

    User user = UserThreadLocal.get();
    if (user == null) {
      return;
    }

    AppPermissionSummary appPermissionSummary =
        user.getUserRequestContext().getUserPermissionInfo().getAppPermissionMapInternal().get(appId);

    // Do not check environment if update access is defined by workflow entity itself
    if (UPDATE.equals(action) && isNotEmpty(appPermissionSummary.getWorkflowUpdatePermissionsByEntity())
        && appPermissionSummary.getWorkflowUpdatePermissionsByEntity().contains(workflow.getUuid())) {
      return;
    }

    if (envTemplatized) {
      if (appPermissionSummary.isCanCreateTemplatizedWorkflow()) {
        return;
      } else {
        throw new AccessDeniedException("Access Denied", USER);
      }
    }

    Set<String> allowedEnvIds;
    switch (action) {
      case CREATE:
        allowedEnvIds = appPermissionSummary.getWorkflowCreatePermissionsForEnvs();
        break;
      case UPDATE:
        allowedEnvIds = appPermissionSummary.getWorkflowUpdatePermissionsForEnvs();
        break;
      default:
        return;
    }

    if (isEmpty(allowedEnvIds) || !allowedEnvIds.contains(envId)) {
      throw new AccessDeniedException("Access Denied", USER);
    }
  }

  @Override
  public void checkIfUserCanCloneWorkflowToOtherApp(String targetAppId, Workflow workflow) {
    if (workflow == null) {
      return;
    }
    boolean envTemplatized = authHandler.isEnvTemplatized(workflow);

    if (!envTemplatized) {
      return;
    }

    User user = UserThreadLocal.get();
    if (user == null) {
      return;
    }

    AppPermissionSummary appPermissionSummary =
        user.getUserRequestContext().getUserPermissionInfo().getAppPermissionMapInternal().get(targetAppId);

    if (!appPermissionSummary.isCanCreateTemplatizedWorkflow()) {
      throw new AccessDeniedException("Access Denied", USER);
    }
  }

  @Override
  public void checkPipelinePermissionsForEnv(String appId, Pipeline pipeline, Action action) {
    User user = UserThreadLocal.get();

    if (user == null) {
      return;
    }

    AppPermissionSummary appPermissionSummary =
        user.getUserRequestContext().getUserPermissionInfo().getAppPermissionMapInternal().get(appId);

    // Do not check environment if RBAC is defined by pipeline entity itself
    if (UPDATE.equals(action) && isNotEmpty(appPermissionSummary.getPipelineUpdatePermissionsByEntity())
        && appPermissionSummary.getPipelineUpdatePermissionsByEntity().contains(pipeline.getUuid())) {
      return;
    }

    Set<String> allowedEnvIds;

    switch (action) {
      case CREATE:
        allowedEnvIds = appPermissionSummary.getPipelineCreatePermissionsForEnvs();
        break;
      case UPDATE:
        allowedEnvIds = appPermissionSummary.getPipelineUpdatePermissionsForEnvs();
        break;
      default:
        return;
    }

    if (!authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline, allowedEnvIds)) {
      throw new AccessDeniedException("Access Denied", USER);
    }
  }

  @Override
  public void auditLogin(List<String> accountIds, User loggedInUser) {
    if (Objects.nonNull(loggedInUser) && Objects.nonNull(accountIds)) {
      accountIds.forEach(accountId
          -> auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, loggedInUser, Event.Type.LOGIN));
    }
  }

  @Override
  public void auditLogin2FA(List<String> accountIds, User loggedInUser) {
    if (Objects.nonNull(loggedInUser) && Objects.nonNull(accountIds)) {
      accountIds.forEach(accountId
          -> auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, loggedInUser, Event.Type.LOGIN_2FA));
    }
  }

  @Override
  public void auditUnsuccessfulLogin(String accountId, User user) {
    if (Objects.nonNull(user) && Objects.nonNull(accountId)) {
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, user, Event.Type.UNSUCCESSFUL_LOGIN);
    }
  }

  @Override
  public void auditLoginToNg(List<String> accountIds, User loggedInUser) {
    if (Objects.nonNull(loggedInUser) && Objects.nonNull(accountIds)) {
      for (String accountIdentifier : accountIds) {
        try {
          OutboxEvent outboxEvent = outboxService.save(new LoginEvent(
              accountIdentifier, loggedInUser.getUuid(), loggedInUser.getEmail(), loggedInUser.getName()));
          log.info(
              "NG Login Audits: for account {} and outboxEventId {} successfully saved the audit for LoginEvent to outbox",
              accountIdentifier, outboxEvent.getId());
        } catch (Exception ex) {
          log.error("NG Login Audits: for account {} saving the LoginEvent to outbox failed with exception: ",
              accountIdentifier, ex);
        }
      }
    }
  }

  @Override
  public void auditLogin2FAToNg(List<String> accountIds, User loggedInUser) {
    if (Objects.nonNull(loggedInUser) && Objects.nonNull(accountIds)) {
      accountIds.forEach(accountId
          -> outboxService.save(
              new Login2FAEvent(accountId, loggedInUser.getUuid(), loggedInUser.getEmail(), loggedInUser.getName())));
    }
  }

  @Override
  public void auditUnsuccessfulLoginToNg(String accountId, User user) {
    if (Objects.nonNull(user) && Objects.nonNull(accountId)) {
      outboxService.save(new UnsuccessfulLoginEvent(accountId, user.getUuid(), user.getEmail(), user.getName()));
    }
  }

  @Override
  public void authorizeAppAccess(String accountId, String appId, User user, Action action) {
    UserPermissionInfo userPermissionInfo = authorizeAndGetUserPermissionInfo(accountId, appId, user, false);

    Map<String, AppPermissionSummaryForUI> appPermissionMap = userPermissionInfo.getAppPermissionMap();
    if (appPermissionMap == null || !appPermissionMap.containsKey(appId)) {
      log.error("Auth Failure: User does not have access to app {}", appId);
      throw new AccessDeniedException("Not authorized to access the app", USER);
    }

    if (Action.UPDATE == action) {
      AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();

      if (accountPermissionSummary == null || isEmpty(accountPermissionSummary.getPermissions())
          || !accountPermissionSummary.getPermissions().contains(MANAGE_APPLICATIONS)) {
        log.error("Auth Failure: User does not have access to update {}", appId);
        throw new AccessDeniedException("Not authorized to update the app", USER);
      }
    }
  }

  @Override
  public void authorize(
      Set<String> envIds, String appId, AppPermissionSummary.ExecutableElementInfo executableElementInfo) {
    // not authorizing action since it will be authorized by other filter before this.
    final User user = UserThreadLocal.get();
    if (user == null) {
      return;
    }
    final UserRequestContext userRequestContext = user.getUserRequestContext();
    if (userRequestContext == null) {
      return;
    }
    final Optional<Map<String, AppPermissionSummary>> appPermissionSummaryMap =
        Optional.ofNullable(userRequestContext.getUserPermissionInfo())
            .map(UserPermissionInfo::getAppPermissionMapInternal);
    if (!appPermissionSummaryMap.isPresent()) {
      return;
    }
    final AppPermissionSummary appPermissionSummary = appPermissionSummaryMap.get().get(appId);
    if (appPermissionSummary == null) {
      return;
    }
    final Map<AppPermissionSummary.ExecutableElementInfo, Set<String>> envPipelineDeployPermissions =
        appPermissionSummary.getEnvExecutableElementDeployPermissions();
    if (envPipelineDeployPermissions == null) {
      return;
    }
    if (!envPipelineDeployPermissions.containsKey(executableElementInfo)) {
      log.error("User not authorized for executable element {}", executableElementInfo);
      throw new AccessDeniedException(String.format("User not authorized to deploy %s : %s",
                                          executableElementInfo.getEntityType(), executableElementInfo.getEntityId()),
          USER);
    }
    final Set<String> envDeployPerms = envPipelineDeployPermissions.get(executableElementInfo);

    envIds.forEach(envId -> {
      if (matchesVariablePattern(envId)) {
        return;
      }
      if (!envDeployPerms.contains(envId)) {
        log.error("User not authorized for envId {}", envId);
        throw new InvalidRequestException(String.format("User not authorized to deploy %s to given environment",
                                              executableElementInfo.getEntityType()),
            USER);
      }
    });
  }

  private boolean isUserPermissionInfoEmpty(UserPermissionInfo value) {
    return (value.getAppPermissionMap() == null || value.getAppPermissionMap().isEmpty())
        && (value.getAppPermissionMapInternal() == null || value.getAppPermissionMapInternal().isEmpty())
        && (value.getDashboardPermissions() == null || value.getDashboardPermissions().isEmpty())
        && (value.getAccountPermissionSummary() == null
            || value.getAccountPermissionSummary().getPermissions().isEmpty());
  }
}
