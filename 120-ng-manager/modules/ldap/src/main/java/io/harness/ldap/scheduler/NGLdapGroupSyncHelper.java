/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.scheduler;

import static io.harness.NGConstants.ACCOUNT_VIEWER_ROLE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.invites.InviteType;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.remote.client.RestClientUtils;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapUserResponse;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;

@OwnedBy(PL)
@Slf4j
public class NGLdapGroupSyncHelper {
  @Inject private NgUserService ngUserService;
  @Inject private InviteService inviteService;
  @Inject private UserClient userClient;
  @Inject private UserGroupService userGroupService;

  public void reconcileAllUserGroups(
      Map<UserGroup, LdapGroupResponse> userGroupLdapGroupResponseMap, String ssoId, String accountId) {
    List<UserGroup> failedUserGroups = new ArrayList<>();
    for (Map.Entry<UserGroup, LdapGroupResponse> responseEntry : userGroupLdapGroupResponseMap.entrySet()) {
      reconcileUserGroupWithLdapGroup(responseEntry.getKey(), responseEntry.getValue(), ssoId, failedUserGroups);
    }
    log.info(
        "NGLDAP: LDAP Sync for all linked groups in account {}, where the total count of groups are: {}, and has failed for: {} groups",
        accountId, userGroupLdapGroupResponseMap.size(), failedUserGroups.size());
  }

  private void reconcileUserGroupWithLdapGroup(
      UserGroup userGroup, LdapGroupResponse ldapGroup, String ssoId, List<UserGroup> failedUserGroups) {
    log.info("NGLDAP: Starting sync for user group {}, in account {}, with corresponding ldap group dn {}",
        userGroup.getIdentifier(), userGroup.getAccountIdentifier(), ldapGroup.getDn());
    try {
      syncUserGroupMetadata(userGroup, ldapGroup);
      Set<String> ldapUserEmails =
          ldapGroup.getUsers().stream().map(LdapUserResponse::getEmail).collect(Collectors.toSet());
      List<UserInfo> usersInfo = new ArrayList<>();

      // can cause issue, check if our APIs support querying ~1K list of users
      if (isNotEmpty(userGroup.getUsers())) {
        usersInfo.addAll(ngUserService.listCurrentGenUsers(
            userGroup.getAccountIdentifier(), UserFilterNG.builder().userIds(userGroup.getUsers()).build()));
      }

      Map<String, UserInfo> emailToUserInfoMap = new HashMap<>();
      Set<String> userGroupUserEmails = new HashSet<>();

      for (UserInfo info : usersInfo) {
        if (isNotEmpty(info.getEmail())) {
          emailToUserInfoMap.put(info.getEmail().toLowerCase(), info);
          userGroupUserEmails.add(info.getEmail().toLowerCase());
        }
      }

      Set<String> usersToRemove = SetUtils.difference(userGroupUserEmails, ldapUserEmails);
      Set<String> usersToAdd = SetUtils.difference(ldapUserEmails, userGroupUserEmails);

      for (LdapUserResponse userResponse : ldapGroup.getUsers()) {
        if (usersToAdd.contains(userResponse.getEmail())) {
          // add to userGroup
          addMemberToGroup(userGroup, userResponse);
        } else {
          // update user name
          updateUserInGroup(userGroup, userResponse);
        }
      }

      if (isNotEmpty(usersToRemove)) {
        // remove from userGroup
        removeUserFromGroup(userGroup, emailToUserInfoMap, usersToRemove);
      }
    } catch (Exception exc) {
      log.error("NGLDAP: Sync Error while updating user Group or its users {} in account {}", userGroup.getIdentifier(),
          userGroup.getAccountIdentifier());
      failedUserGroups.add(userGroup);
    }
  }

  private void updateUserInGroup(UserGroup userGroup, LdapUserResponse userResponse) {
    log.info("NGLDAP: updating user {}, in group: {} for account {} and externalUserId {}", userResponse.getEmail(),
        userGroup.getIdentifier(), userGroup.getAccountIdentifier(), userResponse.getUserId());
    RestClientUtils.getResponse(
        userClient.updateUser(UserInfo.builder().name(userResponse.getName()).email(userResponse.getEmail()).build()));
  }

  private void removeUserFromGroup(
      UserGroup userGroup, Map<String, UserInfo> emailToUserInfoMap, Set<String> usersToRemove) {
    Scope scope =
        Scope.of(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier());
    for (String emailStr : usersToRemove) {
      String userId = emailToUserInfoMap.containsKey(emailStr) ? emailToUserInfoMap.get(emailStr).getUuid() : null;
      if (isNotEmpty(userId)) {
        log.info("NGLDAP: removing user {}, from group: {} for account {}", userId, userGroup.getIdentifier(),
            userGroup.getAccountIdentifier());
        userGroupService.removeMember(scope, userGroup.getIdentifier(), userId);
      }
    }
  }

  private void addMemberToGroup(UserGroup userGroup, LdapUserResponse userResponse) {
    Optional<UserInfo> userInfoOptional = ngUserService.getUserInfoByEmailFromCG(userResponse.getEmail());
    if (userInfoOptional.isEmpty() || !checkUserPartOfAccount(userGroup.getAccountIdentifier(), userInfoOptional)) {
      inviteUserToAccount(userResponse, userGroup.getAccountIdentifier());
    }
    if (userInfoOptional.isEmpty()) {
      // re-get newly created user with invite (less probable case try to optimize if possible later)
      userInfoOptional = ngUserService.getUserInfoByEmailFromCG(userResponse.getEmail());
    }
    if (userInfoOptional.isEmpty()) {
      // something went wrong and ignoring this 'user', it will get synced in next iteration
      // do not fail sync for partial failures
      return;
    }
    log.info("NGLDAP: adding new user {}, to group: {} in account {} and externalUserId {}",
        userInfoOptional.get().getUuid(), userGroup.getIdentifier(), userGroup.getAccountIdentifier(),
        userResponse.getUserId());
    userGroupService.addMember(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
        userGroup.getProjectIdentifier(), userGroup.getIdentifier(), userInfoOptional.get().getUuid());
  }

  private boolean checkUserPartOfAccount(String accountId, Optional<UserInfo> userInfoOptional) {
    if (userInfoOptional.isPresent() && isNotEmpty(userInfoOptional.get().getAccounts())) {
      return userInfoOptional.get().getAccounts().stream().anyMatch(account -> accountId.equals(account.getUuid()));
    }
    return false;
  }

  private void inviteUserToAccount(LdapUserResponse ldapUserResponse, String accountId) {
    Invite invite = Invite.builder()
                        .accountIdentifier(accountId)
                        .approved(true)
                        .email(ldapUserResponse.getEmail())
                        .name(ldapUserResponse.getName())
                        .inviteType(InviteType.ADMIN_INITIATED_INVITE)
                        .build();

    invite.setRoleBindings(
        Collections.singletonList(RoleBinding.builder().roleIdentifier(ACCOUNT_VIEWER_ROLE).build()));
    log.info("NGLDAP: creating user invite for account {} and user Invite {} and externalUserId {}", accountId,
        invite.getEmail(), ldapUserResponse.getUserId());
    inviteService.create(invite, true, false);
  }

  private void syncUserGroupMetadata(UserGroup userGroup, LdapGroupResponse groupResponse) {
    UserGroupDTO userGroupDTO = toDTO(userGroup);
    userGroupDTO.setLinkedSsoDisplayName(groupResponse.getName());
    log.info("NGLDAP: Updating user group {} in account {} with name: {}", userGroup.getIdentifier(),
        userGroup.getAccountIdentifier(), groupResponse.getName());
    userGroupService.update(userGroupDTO);
  }
}
