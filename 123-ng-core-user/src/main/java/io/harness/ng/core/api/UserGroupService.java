/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.accesscontrol.scopes.ScopeNameDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.user.entities.UserGroup;
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

@OwnedBy(PL)
public interface UserGroupService {
  UserGroup create(UserGroupDTO userGroup);

  boolean copy(String accountIdentifier, String userGroupIdentifier, List<ScopeDTO> scopes);

  Optional<UserGroup> get(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  List<UserGroup> getUserGroupsBySsoId(String accountIdentifier, String ssoId);

  List<UserGroup> getUserGroupsBySsoId(String ssoId);

  List<UserGroup> getExternallyManagedGroups(String accountIdentifier);

  boolean isExternallyManaged(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String groupIdentifier);

  UserGroup update(UserGroupDTO userGroupDTO);

  Page<UserGroup> list(String accountIdentifier, String orgIdentifier, String projectIdentifier, String searchTerm,
      UserGroupFilterType filterType, Pageable pageable);

  List<ScopeNameDTO> getInheritingChildScopeList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userGroupIdentifier);

  List<UserGroup> list(Criteria criteria);

  List<UserGroup> list(UserGroupFilterDTO userGroupFilterDTO);

  PageResponse<UserMetadataDTO> listUsersInUserGroup(
      Scope scope, String userGroupIdentifier, UserFilter userFilter, PageRequest pageRequest);

  List<UserMetadataDTO> getUsersInUserGroup(Scope scope, String userGroupIdentifier);

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
}
