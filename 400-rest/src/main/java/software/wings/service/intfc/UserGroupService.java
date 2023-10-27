/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;
import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroupSearchTermType;
import software.wings.beans.sso.SSOType;
import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.service.intfc.ownership.OwnedByApplication;
import software.wings.service.intfc.ownership.OwnedByEnvironment;
import software.wings.service.intfc.ownership.OwnedByInfrastructureProvisioner;
import software.wings.service.intfc.ownership.OwnedByPipeline;
import software.wings.service.intfc.ownership.OwnedByService;
import software.wings.service.intfc.ownership.OwnedByTemplate;
import software.wings.service.intfc.ownership.OwnedByWorkflow;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by rishi
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public interface UserGroupService
    extends OwnedByAccount, OwnedByApplication, OwnedByService, OwnedByEnvironment, OwnedByPipeline, OwnedByWorkflow,
            OwnedByInfrastructureProvisioner, OwnedByTemplate {
  /**
   * Save.
   *
   * @param userGroup the userGroup
   * @return the userGroup
   */
  UserGroup save(UserGroup userGroup);

  /**
   * List page response.
   *
   * @param accountId
   * @param req            the req
   * @param searchTermType
   * @param searchTerm
   * @param hitSecondary
   * @return the page response
   */
  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserGroupService#list(software.wings.dl.PageRequest)
   */
  PageResponse<UserGroup> list(String accountId, PageRequest<UserGroup> req, boolean loadUsers,
      UserGroupSearchTermType searchTermType, String searchTerm, boolean hitSecondary);
  long getCountOfUserGroups(String accountId);
  /**
   * list user groups by name.
   */
  @Nullable List<UserGroup> listByName(@NotEmpty String accountId, @NotEmpty List<String> names);

  UserGroup getUserGroupSummary(UserGroup userGroup);

  List<UserGroup> getUserGroupSummary(List<UserGroup> userGroupList);

  List<UserGroup> filter(String accountId, List<String> userGroupIds);

  List<UserGroup> filter(String accountId, List<String> userGroupIds, List<String> fieldsNeededInResponse);

  /**
   * Find by uuid.
   *
   * @param uuid the uuid
   * @param accountId the accountId
   * @return the userGroup
   */
  UserGroup get(@NotEmpty String accountId, @NotEmpty String uuid);

  UserGroup get(@NotEmpty String uuid);

  /**
   * Find by name.
   */
  @Nullable UserGroup getByName(@NotEmpty String accountId, @NotEmpty String name);

  /**
   * Find by uuid with optional loadUsers flag.
   *
   * @param accountId the accountId
   * @param uuid the uuid
   * @param loadUsers populate users flag
   * @return the userGroup
   */
  UserGroup get(@NotEmpty String accountId, @NotEmpty String uuid, boolean loadUsers);

  /**
   * Update Overview.
   *
   * @param userGroup the userGroup
   * @return the userGroup
   */
  UserGroup updateOverview(UserGroup userGroup);

  UserGroup updateNotificationSettings(String accountId, String groupId, NotificationSettings newNotificationSettings);

  /**
   * Same as above, but with a boolean flag to perform or skip auditing
   */
  UserGroup updateMembers(UserGroup userGroup, boolean sendNotification, boolean toBeAudited);

  /**
   * Remove members from the userGroup
   *
   * @return the userGroup
   */
  UserGroup removeMembers(UserGroup userGroup, Collection<User> members, boolean sendNotification, boolean toBeAudited);

  /**
   * Update UserGroup Permissions.
   *
   * @return the userGroup
   */
  UserGroup setUserGroupPermissions(
      String accountId, String userGroupId, AccountPermissions accountPermissions, Set<AppPermission> appPermission);
  /**
   * Update Overview.
   *
   * @param userGroup the userGroup
   * @return the userGroup
   */
  UserGroup updatePermissions(UserGroup userGroup);

  /**
   * Return if there exists any user group linked to given sso provider id
   *
   * @param ssoId     SSO Provider ID
   * @param accountId
   * @return
   */
  boolean existsLinkedUserGroup(String accountId, @NotBlank String ssoId);

  boolean delete(String accountId, String userGroupId, boolean forceDelete);

  /**
   * Gets default "Account Administrator" user group for an account.
   *
   * @param accountId
   * @return
   */
  @Nullable UserGroup getDefaultUserGroup(String accountId);

  /**
   * Clone the given User Group with a new name
   * @param accountId The id of the account of the user group
   * @param uuid The id of the user group
   * @param newName The new name to be used for creating the cloned object
   * @param newDescription The description of the new cloned user group
   * @return The newly created clone.
   */
  UserGroup cloneUserGroup(
      @NotEmpty String accountId, @NotEmpty String uuid, @NotEmpty String newName, String newDescription);

  List<UserGroup> listByAccountId(String accountId, User user, boolean loadUsers);

  List<UserGroup> listByAccountId(String accountId);

  List<String> fetchUserGroupsMemberIds(String accountId, List<String> userGroupIds);

  List<UserGroup> fetchUserGroupNamesFromIds(Collection<String> userGroupIds);

  List<UserGroup> fetchUserGroupNamesFromIdsUsingSecondary(Collection<String> userGroupIds);

  boolean verifyUserAuthorizedToAcceptOrRejectApproval(String accountId, List<String> userGroupIds);

  boolean verifyApiKeyAuthorizedToAcceptOrRejectApproval(List<String> apiKeysUserGroupIds, List<String> userGroupIds);

  UserGroup linkToSsoGroup(@NotBlank String accountId, @NotBlank String userGroupId, @NotNull SSOType ssoType,
      @NotBlank String ssoId, @NotBlank String ssoGroupId, @NotBlank String ssoGroupName);

  UserGroup unlinkSsoGroup(@NotBlank String accountId, @NotBlank String userGroupId, boolean retainMembers);

  /**
   * Remove app ids from permissions, remove empty app permissions and save UserGroup
   * @param userGroup user group
   * @param appIds collection of app ids
   */
  void removeAppIdsFromAppPermissions(UserGroup userGroup, Set<String> appIds);

  /**
   * Get list of user groups linked to given sso id
   *
   * @param accountId account id
   * @param ssoId linked sso id
   * @return list of user groups
   */
  List<UserGroup> getUserGroupsBySsoId(@NotBlank String accountId, @NotBlank String ssoId);

  List<UserGroup> getUserGroupsFromUserInvite(UserInvite userInvite);

  UserGroup fetchUserGroupByName(
      @NotEmpty String accountId, @NotEmpty(message = "User group name cannot be null") String groupName);

  UserGroup getAdminUserGroup(String accountId);

  boolean deleteNonAdminUserGroups(String accountId);

  boolean deleteUserGroupsByName(String accountId, List<String> userGroupsToRetain);

  void maskCePermissions(UserGroup userGroup);

  void maintainTemplatePermissions(UserGroup userGroup);

  void addParentsReference(String userGroupId, String accountId, String appId, String entityId, String entityType);

  void removeParentsReference(String userGroupId, String accountId, String appId, String entityId, String entityType);
}
