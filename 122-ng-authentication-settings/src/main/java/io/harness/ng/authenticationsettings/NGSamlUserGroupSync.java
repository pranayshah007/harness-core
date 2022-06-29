/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.Scope.ScopeKeys;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserGroup.UserGroupKeys;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;

import software.wings.security.authentication.SamlUserAuthorization;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Slf4j
public class NGSamlUserGroupSync {
  @Inject private UserGroupService userGroupService;
  @Inject private NgUserService ngUserService;

  public void syncUserGroup(
      final String accountIdentifier, final String ssoId, final String email, final List<String> userGroups) {
    SamlUserAuthorization samlUserAuthorization =
        SamlUserAuthorization.builder().email(email).userGroups(userGroups).build();

    // audting and handling notification service for that user to send an email is left
    List<UserGroup> userGroupsToSync = userGroupService.getUserGroupsBySsoId(ssoId);
    log.info("[NGSamlUserGroupSync] Syncing saml user groups for user: {}  userGroups: {}",
        samlUserAuthorization.getEmail(), userGroupsToSync);
    updateUserGroups(userGroupsToSync, samlUserAuthorization, accountIdentifier);
  }

  private void updateUserGroups(
      List<UserGroup> userGroupsToSync, SamlUserAuthorization samlUserAuthorization, String accountIdentifier) {
    Optional<UserMetadataDTO> userOpt = ngUserService.getUserByEmail(samlUserAuthorization.getEmail(), false);
    if (!userOpt.isPresent()) {
      return;
    }
    UserMetadataDTO user = userOpt.get();
    final List<String> newUserGroups =
        samlUserAuthorization.getUserGroups() != null ? samlUserAuthorization.getUserGroups() : new ArrayList<>();

    log.info("[NGSamlUserGroupSync] SAML authorisation user groups for user: {} are: {}",
        samlUserAuthorization.getEmail(), newUserGroups.toString());

    List<UserGroup> userAddedToGroups = new ArrayList<>();

    userGroupsToSync.forEach(userGroup -> {
      Scope scope =
          Scope.of(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier());
      if (!newUserGroups.contains(userGroup.getSsoGroupId())) {
        if (userGroupService.checkMember(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                scope.getProjectIdentifier(), userGroup.getIdentifier(), user.getUuid())) {
          log.info("[NGSamlUserGroupSync] Removing user: {} from user group: {} in account: {}", user.getUuid(),
              userGroup.getName(), userGroup.getAccountIdentifier());
          userGroupService.removeMember(scope, userGroup.getIdentifier(), user.getUuid());
        }
      } else if (!userGroupService.checkMember(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                     scope.getProjectIdentifier(), userGroup.getIdentifier(), user.getUuid())
          && newUserGroups.contains(userGroup.getSsoGroupId())) {
        log.info(
            "[NGSamlUserGroupSync] Adding user {} to scope account: [{}], org:[{}], project:[{}] and  groups {} in saml authorization.",
            samlUserAuthorization.getEmail(), scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), userAddedToGroups.toString());
        Optional<UserMembership> currentScopeUserMembership = ngUserService.getUserMembership(user.getUuid(), scope);
        if (!currentScopeUserMembership.isPresent()) {
          ngUserService.addUserToScope(user.getUuid(), scope, null,
              Collections.singletonList(userGroup.getIdentifier()), UserMembershipUpdateSource.SYSTEM);
        } else {
          userAddedToGroups.add(userGroup);
        }
      } else {
        log.info("[NGSamlUserGroupSync]: Should not come here User: {} Scope: {} UserGroup: {}", user.getUuid(), scope,
            userGroup.getName());
      }
    });
    userGroupService.addUserToUserGroups(accountIdentifier, user.getUuid(), userAddedToGroups);
  }

  @VisibleForTesting
  void removeUsersFromScopesPostSync(String userId) {
    log.info("[NGSamlUserGroupSync] Checking removal of user: {} from all diff scopes post sync", userId);
    int countOfProjectLevelUserGroups =
        userGroupService
            .list(Criteria.where(UserGroupKeys.projectIdentifier).exists(true).and(UserGroupKeys.users).in(userId),
                null, null)
            .size();

    if (countOfProjectLevelUserGroups == 0) {
      Criteria criteria = Criteria.where(UserMembershipKeys.userId).is(userId);
      criteria.and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier).exists(true);
      ngUserService.removeUserWithCriteria(userId, UserMembershipUpdateSource.SYSTEM, criteria);

      log.info(
          "[NGSamlUserGroupSync] Removing user: {} from all project scopes as it is not part of any project scope post sync",
          userId);

      int countOfOrgLevelUserGroups = userGroupService
                                          .list(Criteria.where(UserGroupKeys.orgIdentifier)
                                                    .exists(true)
                                                    .and(UserGroupKeys.projectIdentifier)
                                                    .exists(false)
                                                    .and(UserGroupKeys.users)
                                                    .in(userId),
                                              null, null)
                                          .size();

      if (countOfOrgLevelUserGroups == 0) {
        Criteria orgCriteria = Criteria.where(UserMembershipKeys.userId).is(userId);
        criteria.and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier).exists(false);
        criteria.and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier).exists(true);
        ngUserService.removeUserWithCriteria(userId, UserMembershipUpdateSource.SYSTEM, orgCriteria);

        log.info(
            "[NGSamlUserGroupSync] Removing user: {} from all org scopes as it is not part of any org scope post sync",
            userId);

        int countOfAccountLevelUserGroups = userGroupService
                                                .list(Criteria.where(UserGroupKeys.orgIdentifier)
                                                          .exists(false)
                                                          .and(UserGroupKeys.accountIdentifier)
                                                          .exists(true)
                                                          .and(UserGroupKeys.projectIdentifier)
                                                          .exists(false)
                                                          .and(UserGroupKeys.users)
                                                          .in(userId),
                                                    null, null)
                                                .size();

        if (countOfAccountLevelUserGroups == 0) {
          Criteria accountCriteria = Criteria.where(UserMembershipKeys.userId).is(userId);
          criteria.and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier).exists(false);
          criteria.and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier).exists(false);
          criteria.and(UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier).exists(true);
          log.info(
              "[NGSamlUserGroupSync] Removing user: {} from all account scopes as it is not part of any account scope post sync",
              userId);

          ngUserService.removeUserWithCriteria(userId, UserMembershipUpdateSource.SYSTEM, accountCriteria);
        }
      }
    }
  }
}