/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.accesscontrol.scopes.ScopeNameDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ScopeSelector;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.dto.UserInfo;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;

import software.wings.beans.sso.SSOType;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.util.CloseableIterator;

/**
 * This interface exposes methods needed for User Group operations.
 */
@OwnedBy(PL)
public interface UserGroupService {
  UserGroup create(UserGroupDTO userGroup);

  Optional<UserGroup> get(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  List<UserGroup> getUserGroupsBySsoId(String accountIdentifier, String ssoId);

  List<UserGroup> getUserGroupsBySsoId(String ssoId);

  List<UserGroup> getExternallyManagedGroups(String accountIdentifier);

  boolean isExternallyManaged(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String groupIdentifier);

  UserGroup update(UserGroupDTO userGroupDTO);

  UserGroup updateWithCheckThatSCIMFieldsAreNotModified(UserGroupDTO userGroupDTO);

  List<UserInfo> getUserMetaData(List<String> uuids);

  List<String> getUserIds(List<String> emails);

  Page<UserGroup> list(String accountIdentifier, String orgIdentifier, String projectIdentifier, String searchTerm,
      UserGroupFilterType filterType, Pageable pageable);

  List<ScopeNameDTO> getInheritingChildScopeList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userGroupIdentifier);

  List<UserGroup> list(Criteria criteria, Integer skip, Integer limit);

  List<UserGroup> list(UserGroupFilterDTO userGroupFilterDTO);

  Page<UserGroup> list(List<ScopeSelector> scopeFilter, String userIdentifier, String searchTerm, Pageable pageable);

  PageResponse<UserMetadataDTO> listUsersInUserGroup(
      Scope scope, String userGroupIdentifier, UserFilter userFilter, PageRequest pageRequest);

  CloseableIterator<UserMetadata> getUsersInUserGroup(Scope scope, String userGroupIdentifier);

  UserGroup delete(Scope scope, String identifier);

  boolean deleteByScope(Scope scope);

  boolean checkMember(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userGroupIdentifier, String userIdentifier);

  UserGroup addMember(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userGroupIdentifier, String userIdentifier);

  void addUserToUserGroups(String accountIdentifier, String userId, List<UserGroup> userGroups);

  void addUserToUserGroups(Scope scope, String userId, List<String> userGroups);

  UserGroup removeMember(Scope scope, String userGroupIdentifier, String userIdentifier);

  void removeMemberAll(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String userIdentifier);

  UserGroup linkToSsoGroup(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotBlank String userGroupId, @NotNull SSOType ssoType, @NotBlank String ssoId, @NotBlank String ssoGroupId,
      @NotBlank String ssoGroupName);

  UserGroup unlinkSsoGroup(@NotBlank String accountId, String orgIdentifier, String projectIdentifier,
      @NotBlank String userGroupId, boolean retainMembers);

  void sanitize(Scope scope, String identifier);

  /**
   * This method is to be used only for Default User Group creation.
   * @param userGroupDTO UserGroup to be created
   * @return UserGroup This returns created user group.
   */
  UserGroup createDefaultUserGroup(UserGroupDTO userGroupDTO);

  /**
   * This method is to be used only for adding members to Default User Group.
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param userGroupIdentifier
   * @param userIdentifier
   * @return UserGroup This returns created user group.
   */
  UserGroup addMemberToDefaultUserGroup(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userGroupIdentifier, String userIdentifier);

  /**
   * This method is to be used only to update Default User Group.
   * @param userGroup UserGroup to be updated
   * @return UserGroup This returns created user group.
   */
  UserGroup updateDefaultUserGroup(UserGroup userGroup);

  /**
   * This method is to be used to return Permitted User Group.
   * @param userGroups List  of userGroups to be checked if permitted.
   * @return UserGroup This returns permitted user group.
   */
  List<UserGroup> getPermittedUserGroups(List<UserGroup> userGroups);

  Long countUserGroups(String accountIdentifier);

  List<UserGroup> getUserGroupsForUser(String accountIdentifier, String userId);
}