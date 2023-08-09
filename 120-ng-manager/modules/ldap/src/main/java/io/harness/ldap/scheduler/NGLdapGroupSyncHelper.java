/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.scheduler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.common.beans.UserSource.LDAP;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.invites.InviteType;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.remote.client.CGRestUtils;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapUserResponse;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    log.info(
        "NGLDAP: Starting sync for user group {}, in account {}, with corresponding ldap group dn {}. The number of users returned for LdapGroup is: {}",
        userGroup.getIdentifier(), userGroup.getAccountIdentifier(), ldapGroup.getDn(), ldapGroup.getTotalMembers());
    try {
      syncUserGroupMetadata(userGroup, ldapGroup);
      Set<String> ldapUserEmails = ldapGroup.getUsers()
                                       .stream()
                                       .map(LdapUserResponse::getEmail)
                                       .filter(Objects::nonNull)
                                       .collect(Collectors.toSet());

      log.info("NGLDAP: Users email list received from LDAP on group dn {}, linked to user group {} are {}",
          ldapGroup.getDn(), userGroup.getIdentifier(), getStringBuilderForEmails(ldapUserEmails).toString());
      List<UserInfo> usersInfo = new ArrayList<>();

      // can cause issue, check if our APIs support querying ~1K list of users
      if (isNotEmpty(userGroup.getUsers())) {
        log.info("NGLDAP: Get list of CG users for user group: {}, which has user count: {} in account: {}",
            userGroup.getIdentifier(), userGroup.getUsers().size(), userGroup.getAccountIdentifier());
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

      log.info(
          "NGLDAP: Users email after getting user records from CG for user group {}, has received count: {} and are having emails as {}",
          userGroup.getIdentifier(), userGroupUserEmails.size(),
          getStringBuilderForEmails(userGroupUserEmails).toString());

      Set<String> usersToRemove = SetUtils.difference(userGroupUserEmails, ldapUserEmails);
      Set<String> usersToAdd = SetUtils.difference(ldapUserEmails, userGroupUserEmails);

      for (LdapUserResponse userResponse : ldapGroup.getUsers()) {
        try {
          if (usersToAdd.contains(userResponse.getEmail())) {
            // add to userGroup
            addMemberToGroup(userGroup, userResponse);
          } else {
            // update user name
            updateUserInGroup(userGroup, userResponse);
          }
        } catch (Exception exception) {
          log.error(
              "NGLDAP: Skipping : Add/update user with ldap externalUserId {}, email: {} to User group: {} in account: {}, organization: {}, project: {} failed",
              userResponse.getUserId(), userResponse.getEmail(), userGroup, userGroup.getAccountIdentifier(),
              userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier(), exception);
        }
      }

      if (isNotEmpty(usersToRemove)) {
        removeUserFromGroup(userGroup, emailToUserInfoMap, usersToRemove);
      }
    } catch (Exception exc) {
      log.error("NGLDAP: Sync Error while updating user Group or its users {}: in account {} : ",
          userGroup.getIdentifier(), userGroup.getAccountIdentifier(), exc);
      failedUserGroups.add(userGroup);
    }
  }

  private void updateUserInGroup(UserGroup userGroup, LdapUserResponse userResponse) {
    if (isNotEmpty(userResponse.getEmail())) {
      log.info("NGLDAP: updating user {}, in group: {} for account {} and externalUserId {}", userResponse.getEmail(),
          userGroup.getIdentifier(), userGroup.getAccountIdentifier(), userResponse.getUserId());
      CGRestUtils.getResponse(userClient.updateUser(
          UserInfo.builder().name(userResponse.getName()).email(userResponse.getEmail()).build()));
    }
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
        try {
          userGroupService.removeMember(scope, userGroup.getIdentifier(), userId);
        } catch (Exception exception) {
          log.error(
              "NGLDAP: Skipping : Remove user with harness userId {}, email: {} to User group: {} in account: {}, organization: {}, project: {} failed",
              userId, emailStr, userGroup, userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
              userGroup.getProjectIdentifier(), exception);
        }
      }
    }
  }

  private void addMemberToGroup(UserGroup userGroup, LdapUserResponse userResponse) {
    if (isNotEmpty(userResponse.getEmail())) {
      Optional<UserInfo> userInfoOptional = ngUserService.getUserInfoByEmailFromCG(userResponse.getEmail());
      if (userInfoOptional.isEmpty() || !checkUserPartOfAccount(userGroup.getAccountIdentifier(), userInfoOptional)) {
        inviteUserToAccount(userResponse, userGroup.getAccountIdentifier());
      }

      Optional<UserMetadataDTO> userOptional = ngUserService.getUserByEmail(userResponse.getEmail(), false);

      if (userInfoOptional.isPresent() && userOptional.isEmpty()) {
        log.info(
            "NGLDAP: User {} with externalUserId {}, not present in NG. Adding to NG at user group {}, in account: {}.",
            userInfoOptional.get().getUuid(), userResponse.getUserId(), userGroup.getIdentifier(),
            userGroup.getAccountIdentifier());
        userOptional = addUserToScopeAndReturnMetadataDTO(userResponse, userInfoOptional.get().getUuid(),
            userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier());
      }

      if (userOptional.isPresent()
          && !checkUserPartOfAccountInNg(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
              userGroup.getProjectIdentifier(), userOptional.get())) {
        log.info(
            "NGLDAP: User {} with externalUserId {}, present in NG but not in this account {}. Adding to NG for the user group {}.",
            userInfoOptional.get().getUuid(), userResponse.getUserId(), userGroup.getAccountIdentifier(),
            userGroup.getIdentifier());
        userOptional = addUserToScopeAndReturnMetadataDTO(userResponse, userOptional.get().getUuid(),
            userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier());
      }

      if (userOptional.isEmpty()
          || !checkUserPartOfAccountInNg(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
              userGroup.getProjectIdentifier(), userOptional.get())) {
        log.warn(
            "NGLDAP: Invite user with ldap externalUserId {}, or adding user to scope- account: {}, organization: {}, project: {} failed",
            userResponse.getUserId(), userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
            userGroup.getProjectIdentifier());
        // throw here to be caught above and added to 'failedUserGroups' count
        throw new IllegalStateException("NGLDAP: Illegal state value of user to be added as member to user group");
      }

      log.info("NGLDAP: adding new user {} having email: {} to group: {} in account {} and externalUserId {}",
          userOptional.get().getUuid(), userOptional.get().getEmail(), userGroup.getIdentifier(),
          userGroup.getAccountIdentifier(), userResponse.getUserId());
      userGroupService.addMember(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
          userGroup.getProjectIdentifier(), userGroup.getIdentifier(), userOptional.get().getUuid());
    }
  }

  private boolean checkUserPartOfAccount(String accountId, Optional<UserInfo> userInfoOptional) {
    if (userInfoOptional.isPresent() && isNotEmpty(userInfoOptional.get().getAccounts())) {
      return userInfoOptional.get().getAccounts().stream().anyMatch(account -> accountId.equals(account.getUuid()));
    }
    return false;
  }

  private boolean checkUserPartOfAccountInNg(
      String accountId, String orgId, String projectId, UserMetadataDTO userOptional) {
    return ngUserService.isUserAtScope(userOptional.getUuid(),
        Scope.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build());
  }

  private Optional<UserMetadataDTO> addUserToScopeAndReturnMetadataDTO(LdapUserResponse userResponse, final String uuid,
      final String accountId, final String orgId, final String projectId) {
    log.info("NGLDAP: adding user {} with externalUserId {}, to scope- account: {}, organization: {}, project: {}",
        uuid, userResponse.getUserId(), accountId, orgId, projectId);
    ngUserService.addUserToScope(
        uuid, Scope.of(accountId, orgId, projectId), emptyList(), emptyList(), UserMembershipUpdateSource.SYSTEM);
    ngUserService.updateNGUserToCGWithSource(uuid, Scope.builder().accountIdentifier(accountId).build(), LDAP);
    return ngUserService.getUserByEmail(userResponse.getEmail(), false);
  }

  private void inviteUserToAccount(LdapUserResponse ldapUserResponse, String accountId) {
    Invite invite = Invite.builder()
                        .accountIdentifier(accountId)
                        .approved(true)
                        .email(ldapUserResponse.getEmail())
                        .name(ldapUserResponse.getName())
                        .inviteType(InviteType.ADMIN_INITIATED_INVITE)
                        .build();

    invite.setRoleBindings(emptyList());
    log.info("NGLDAP: creating user invite for account {} and user Invite {} and externalUserId {}", accountId,
        invite.getEmail(), ldapUserResponse.getUserId());
    inviteService.create(invite, false, true);
  }

  private void syncUserGroupMetadata(UserGroup userGroup, LdapGroupResponse groupResponse) {
    UserGroupDTO userGroupDTO = toDTO(userGroup);
    if (null != userGroupDTO.getSsoGroupName() && null != groupResponse.getName()
        && groupResponse.getName().equals(userGroupDTO.getSsoGroupName())) {
      return;
    }
    userGroupDTO.setSsoGroupName(groupResponse.getName());
    log.info("NGLDAP: Updating user group {} in account {} with name: {}", userGroup.getIdentifier(),
        userGroup.getAccountIdentifier(), groupResponse.getName());
    userGroupService.update(userGroupDTO);
  }

  private StringBuilder getStringBuilderForEmails(Set<String> emails) {
    StringBuilder sb = new StringBuilder();
    if (isNotEmpty(emails)) {
      emails.forEach(id -> sb.append(id).append(" "));
    }
    return sb;
  }
}
