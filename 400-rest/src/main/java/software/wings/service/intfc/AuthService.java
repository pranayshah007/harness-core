/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;

import software.wings.beans.AuthToken;
import software.wings.beans.Pipeline;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.security.UserGroup;
import software.wings.security.AppPermissionSummary;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestInfo;
import software.wings.security.UserRestrictionInfo;

import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;

/**
 * Created by peeyushaggarwal on 8/18/16.
 */
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public interface AuthService {
  /**
   * Validate token auth token.
   *
   * @param tokenString the token string
   * @return the auth token
   */
  AuthToken validateToken(String tokenString);

  /**
   * Authorize.
   * @param accountId
   * @param appId                the app id
   * @param envId                the env id
   * @param user                 the user
   * @param permissionAttributes the permission attributes
   * @param userRequestInfo
   */
  void authorize(String accountId, String appId, String envId, User user,
      List<PermissionAttribute> permissionAttributes, UserRequestInfo userRequestInfo);

  /**
   * Authorize.
   * @param accountId
   * @param appIds               list of app ids
   * @param envId                the env id
   * @param user                 the user
   * @param permissionAttributes the permission attributes
   * @param userRequestInfo
   */
  void authorize(String accountId, List<String> appIds, String envId, User user,
      List<PermissionAttribute> permissionAttributes, UserRequestInfo userRequestInfo);

  /**
   * Authorize.
   * @param accountId
   * @param appId                the app id
   * @param entityId             the entity id
   * @param user                 the user
   * @param permissionAttributes the permission attributes
   */
  void authorize(
      String accountId, String appId, String entityId, User user, List<PermissionAttribute> permissionAttributes);

  /**
   * Authorize.
   * @param accountId
   * @param appId                the app id
   * @param entityId             the entity id
   * @param user                 the user
   * @param permissionAttributes the permission attributes
   */
  void authorize(String accountId, String appId, String entityId, User user,
      List<PermissionAttribute> permissionAttributes, boolean matchesAny);

  /**
   * Authorize.
   * @param accountId
   * @param appIds               list of app ids
   * @param entityId             the entity id
   * @param user                 the user
   * @param permissionAttributes the permission attributes
   */
  void authorize(String accountId, List<String> appIds, String entityId, User user,
      List<PermissionAttribute> permissionAttributes);

  void validateDelegateToken(
      String accountId, String tokenString, String delegateId, boolean shouldSetTokenNameInGlobalContext);

  void validateDelegateToken(String accountId, String tokenString);

  void invalidateAllTokensForUser(String userId);

  void invalidateToken(String utoken);

  void validateExternalServiceToken(String accountId, String tokenString);

  void validateLearningEngineServiceToken(String learningEngineServiceToken);

  UserPermissionInfo getUserPermissionInfo(String accountId, User user, boolean cacheOnly);

  UserRestrictionInfo getUserRestrictionInfo(
      String accountId, User user, UserPermissionInfo userPermissionInfo, boolean cacheOnly);

  void updateUserPermissionCacheInfo(String accountId, User user, boolean cacheOnly);

  void updateUserRestrictionCacheInfo(
      String accountId, User user, UserPermissionInfo userPermissionInfo, boolean cacheOnly);

  void evictPermissionAndRestrictionCacheForUserGroup(UserGroup userGroup);

  void evictUserPermissionAndRestrictionCacheForAccount(String accountId, List<String> memberIds);

  void evictUserPermissionAndRestrictionCacheForAccount(
      String accountId, boolean rebuildUserPermissionInfo, boolean rebuildUserRestrictionInfo);

  void evictUserPermissionCacheForAccount(String accountId, boolean rebuildUserPermissionInfo);

  void evictUserPermissionAndRestrictionCacheForAccounts(Set<String> accountIds, List<String> memberIds);

  UserRestrictionInfo getUserRestrictionInfoFromDB(
      String accountId, UserPermissionInfo userPermissionInfo, List<UserGroup> userGroupList);

  User refreshToken(String oldToken);

  User generateBearerTokenForUser(@NotNull User user);

  /**
   * @deprecated.
   */
  @Deprecated void checkIfUserAllowedToDeployToEnv(String appId, String envId);

  void checkIfUserAllowedToDeployWorkflowToEnv(String appId, String envId);

  void checkIfUserAllowedToDeployPipelineToEnv(String appId, String envId);

  void checkIfUserAllowedToRollbackWorkflowToEnv(String appId, String envId);

  void checkIfUserCanCreateEnv(String appId, EnvironmentType envType);

  void checkWorkflowPermissionsForEnv(String appId, Workflow workflow, Action action);

  void checkIfUserCanCloneWorkflowToOtherApp(String targetAppId, Workflow workflow);

  void checkPipelinePermissionsForEnv(String appId, Pipeline pipeline, Action action);

  void authorizeAppAccess(String accountId, String appId, User user, Action action);

  void auditLogin(List<String> accountIds, User loggedInUser);

  void auditLogin2FA(List<String> accountIds, User loggedInUser);

  void auditUnsuccessfulLogin(String accountIds, User user);

  void auditLoginToNg(List<String> accountIds, User loggedInUser);

  void auditLogin2FAToNg(List<String> accountIds, User loggedInUser);

  void auditUnsuccessfulLoginToNg(String accountId, User user);

  void authorize(Set<String> envIds, String appId, AppPermissionSummary.ExecutableElementInfo executableElementInfo);
}
