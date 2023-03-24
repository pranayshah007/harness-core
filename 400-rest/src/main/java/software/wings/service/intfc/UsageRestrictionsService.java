/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import software.wings.beans.Base;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.restrictions.RestrictionsSummary;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UsageRestrictions;
import software.wings.security.UsageRestrictions.AppEnvRestriction;
import software.wings.security.UserPermissionInfo;
import software.wings.settings.RestrictionsAndAppEnvMap;
import software.wings.settings.UsageRestrictionsReferenceSummary;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Usage restrictions related service apis. Account level entities (cloud providers, connectors, secrets) has concept of
 * usage restrictions. The user can restrict entities to certain apps and envs.
 *
 * @author rktummala on 06/05/2018
 */
public interface UsageRestrictionsService {
  enum UsageRestrictionsClient { CONNECTORS, SECRETS_MANAGEMENT, ALL }
  /**
   *
   * @param appEnvRestrictions
   * @param appIdEnvMap
   * @return
   */
  Map<String, Set<String>> getAppEnvMap(Set<AppEnvRestriction> appEnvRestrictions, Map<String, List<Base>> appIdEnvMap);

  /**
   * Derive the user restrictions from user permissions.
   *
   * @param accountId
   * @param action
   * @return Usage restrictions
   */
  UsageRestrictions getUsageRestrictionsFromUserPermissions(
      String accountId, Action action, List<UserGroup> userGroupList);

  /**
   * Check if the user has access to an entity from the given context.
   * Access is determined from Usage restrictions, permissions and context.
   *
   * @param accountId account
   * @param appIdFromRequest current app context
   * @param envIdFromRequest current env context
   * @param forUsageInNewApp
   * @param entityUsageRestrictions
   * @param restrictionsFromUserPermissions
   * @param appEnvMapFromPermissions       @return boolean if the user needs to be provided access or not
   * @param appIdEnvMap
   * */
  boolean hasAccess(String accountId, boolean isAccountAdmin, String appIdFromRequest, String envIdFromRequest,
      boolean forUsageInNewApp, UsageRestrictions entityUsageRestrictions,
      UsageRestrictions restrictionsFromUserPermissions, Map<String, Set<String>> appEnvMapFromPermissions,
      Map<String, List<Base>> appIdEnvMap, boolean scopedToAccount);

  /**
   * Lists all the applications and environments that the user has update permissions on.
   * The update permissions could be on any of the env based permission types. (For example, permission types ENV /
   * WORKFLOW / PIPELINE)
   * @param accountId account id
   * @return Restrictions summary that has app and env info.
   */
  RestrictionsSummary listAppsWithEnvUpdatePermissions(String accountId);

  UsageRestrictions getUsageRestrictionsFromJson(String usageRestrictionsString);

  /**
   * Gets the default usage restrictions based on user permissions and the current app env context.
   * @param accountId account id
   * @param appId app id
   * @param envId env id
   * @return usage restrictions
   */
  UsageRestrictions getDefaultUsageRestrictions(String accountId, String appId, String envId);

  boolean userHasPermissionsToChangeEntity(String accountId, PermissionType permissionType,
      UsageRestrictions entityUsageRestrictions, UsageRestrictions restrictionsFromUserPermissions,
      boolean scopedToEntity);

  /**
   * Checks if the user can update / delete entity based on the user permissions and restrictions set on the entity.
   * @param accountId account id
   * @param entityUsageRestrictions entity usage restrictions
   * @param restrictionsFromUserPermissions restrictions from user permissions
   * @param appIdEnvMap
   * @param scopedToAccount
   * @return boolean
   */
  boolean userHasPermissionsToChangeEntity(String accountId, PermissionType permissionType,
      UsageRestrictions entityUsageRestrictions, UsageRestrictions restrictionsFromUserPermissions,
      Map<String, List<Base>> appIdEnvMap, boolean scopedToAccount);

  /**
   * Checks if the user can update / delete entity based on the user permissions and restrictions set on the entity.
   * @param accountId account id
   * @param entityUsageRestrictions entity usage restrictions
   * @param scopedToAccount
   * @return boolean
   */
  boolean userHasPermissionsToChangeEntity(String accountId, PermissionType permissionType,
      UsageRestrictions entityUsageRestrictions, boolean scopedToAccount);

  /**
   * Checks if the user can update / delete entity based on the user permissions and restrictions set on the entity.
   * @param accountId account id
   * @param entityUsageRestrictions entity usage restrictions
   * @param appIdEnvMap
   * @param scopedToAccount boolean to define
   * @return boolean
   */
  boolean userHasPermissionsToChangeEntity(String accountId, PermissionType permissionType,
      UsageRestrictions entityUsageRestrictions, Map<String, List<Base>> appIdEnvMap, boolean scopedToAccount);

  /**
   * Checks if user can update an entity
   * @param oldUsageRestrictions old usage restrictions
   * @param newUsageRestrictions new usage restrictions
   * @param accountId account id
   * @param scopedToAccount boolean to define
   */
  void validateUsageRestrictionsOnEntityUpdate(String accountId, PermissionType permissionType,
      UsageRestrictions oldUsageRestrictions, UsageRestrictions newUsageRestrictions, boolean scopedToAccount);

  /**
   * The update of usage restrictions should not leave setup entities references to be dangling. This method will
   * validate all app/envs referring to the secret is still covered by the new usage restrictions/scopes.
   *
   * @param accountId   account ID.
   * @param setupUsages The app/env map indicating usages by setup entities such as service variable, environment config
   *     override.
   * @param newUsageRestrictions The usage restrictions to be updated to.
   */
  void validateSetupUsagesOnUsageRestrictionsUpdate(
      String accountId, Map<String, Set<String>> setupUsages, UsageRestrictions newUsageRestrictions);

  boolean isUsageRestrictionsSubset(
      String accountId, UsageRestrictions usageRestrictions, UsageRestrictions parentRestrictions);

  /**
   * This method checks whether usages restriction and scoping to account is allowed for current user
   * @param accountId
   * @param newUsageRestrictions
   * @param scopedToAccount
   */
  void validateUsageRestrictionsOnEntitySave(
      String accountId, PermissionType permissionType, UsageRestrictions newUsageRestrictions, boolean scopedToAccount);

  boolean hasNoRestrictions(UsageRestrictions usageRestrictions);

  /**
   * Constructs the app env map from user permissions
   * @param accountId account id
   * @param userPermissionInfo user permission info
   * @param action action
   * @return App env map
   */
  Map<String, Set<String>> getAppEnvMapFromUserPermissions(
      String accountId, UserPermissionInfo userPermissionInfo, Action action);

  /**
   * Gets the usage restrictions and app env map from the user permission cache
   * @param accountId account id
   * @param action action
   * @return
   */
  RestrictionsAndAppEnvMap getRestrictionsAndAppEnvMapFromCache(String accountId, Action action);

  boolean isEditable(String accountId, String entityId, String entityType);

  /**
   * Get the summary of all usage restriction references to the specified application.
   */
  UsageRestrictionsReferenceSummary getReferenceSummaryForApp(String accountId, String appId);

  /**
   * Get the summary of all usage restriction references to the specified environment.
   */
  UsageRestrictionsReferenceSummary getReferenceSummaryForEnv(String accountId, String envId);

  /**
   * Purge all the usage restriction references to application/environments that no longer exists.
   */
  int purgeDanglingAppEnvReferences(String accountId, UsageRestrictionsClient usageRestrictionsClient);

  /**
   * Remove all references to an application or an environment in the usage restrictions. This operation is usually
   * performed before an application is deleted to prevent dangling references to deleted applications.
   */
  int removeAppEnvReferences(String accountId, String appId, String envId);

  UsageRestrictions getMaximumAllowedUsageRestrictionsForUser(String accountId, UsageRestrictions usageRestrictions);

  UsageRestrictions getCommonRestrictions(UsageRestrictions usageRestrictions1, UsageRestrictions usageRestrictions2);

  Map<String, Set<String>> getAppSvcMapFromUserPermissions(
      String accountId, UserPermissionInfo userPermissionInfo, Action action);
}
