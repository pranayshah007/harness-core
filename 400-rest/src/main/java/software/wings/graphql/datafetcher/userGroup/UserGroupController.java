/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.userGroup;

import static io.harness.beans.FeatureName.SPG_GRAPHQL_VERIFY_APPLICATION_FROM_USER_GROUP;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.ccm.config.CCMSettingService;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.notification.SlackNotificationConfiguration;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOType;
import software.wings.graphql.schema.mutation.userGroup.input.QLCreateUserGroupInput;
import software.wings.graphql.schema.mutation.userGroup.payload.QLAddUserToUserGroupPayload;
import software.wings.graphql.schema.mutation.userGroup.payload.QLCreateUserGroupPayload;
import software.wings.graphql.schema.mutation.userGroup.payload.QLRemoveUserFromUserGroupPayload;
import software.wings.graphql.schema.mutation.userGroup.payload.QLUpdateUserGroupPayload;
import software.wings.graphql.schema.type.permissions.QLGroupPermissions;
import software.wings.graphql.schema.type.usergroup.QLLDAPSettings;
import software.wings.graphql.schema.type.usergroup.QLLDAPSettingsInput;
import software.wings.graphql.schema.type.usergroup.QLLinkedSSOSetting;
import software.wings.graphql.schema.type.usergroup.QLNotificationSettings;
import software.wings.graphql.schema.type.usergroup.QLSAMLSettings;
import software.wings.graphql.schema.type.usergroup.QLSAMLSettingsInput;
import software.wings.graphql.schema.type.usergroup.QLSSOSettingInput;
import software.wings.graphql.schema.type.usergroup.QLSlackNotificationSetting;
import software.wings.graphql.schema.type.usergroup.QLUserGroup;
import software.wings.graphql.schema.type.usergroup.QLUserGroup.QLUserGroupBuilder;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
@OwnedBy(HarnessTeam.PL)
public class UserGroupController {
  @Inject private UserGroupPermissionValidator userGroupPermissionValidator;
  @Inject private UserGroupService userGroupService;
  @Inject private UserService userService;
  @Inject private SSOSettingService ssoSettingService;
  @Inject private AccountService accountService;
  @Inject private UserGroupPermissionsController userGroupPermissionsController;
  @Inject private CCMSettingService ccmSettingService;
  @Inject private AppService appService;
  @Inject private FeatureFlagService featureFlagService;

  UserGroup validateAndGetUserGroup(String accountId, String userGroupId) {
    UserGroup userGroup = userGroupService.get(accountId, userGroupId);
    if (userGroup == null) {
      throw new InvalidRequestException(String.format("No user group exists with the id %s", userGroupId));
    }
    return userGroup;
  }

  private QLLinkedSSOSetting populateLDAPSettings(UserGroup userGroup) {
    return QLLDAPSettings.builder()
        .ssoProviderId(userGroup.getLinkedSsoId())
        .groupName(userGroup.getSsoGroupName())
        .groupDN(userGroup.getSsoGroupId())
        .build();
  }

  private QLLinkedSSOSetting populateSAMLSettings(UserGroup userGroup) {
    return QLSAMLSettings.builder()
        .groupName(userGroup.getSsoGroupName())
        .ssoProviderId(userGroup.getLinkedSsoId())
        .build();
  }

  private QLLinkedSSOSetting populateSSOProvider(UserGroup userGroup) {
    if (userGroup.getLinkedSsoType() == SSOType.LDAP) {
      return populateLDAPSettings(userGroup);
    } else if (userGroup.getLinkedSsoType() == SSOType.SAML) {
      return populateSAMLSettings(userGroup);
    }
    return null;
  }

  public QLUserGroupBuilder populateUserGroupOutput(UserGroup userGroup, QLUserGroupBuilder builder) {
    if (!ccmSettingService.isCloudCostEnabled(userGroup.getAccountId())) {
      userGroupService.maskCePermissions(userGroup);
    }
    if (featureFlagService.isEnabled(SPG_GRAPHQL_VERIFY_APPLICATION_FROM_USER_GROUP, userGroup.getAccountId())) {
      sanitizeAppPermissions(userGroup);
    }
    QLGroupPermissions permissions = userGroupPermissionsController.populateUserGroupPermissions(userGroup);
    QLNotificationSettings notificationSettings = populateNotificationSettings(userGroup);
    return builder.name(userGroup.getName())
        .id(userGroup.getUuid())
        .description(userGroup.getDescription())
        .permissions(permissions)
        .ssoSetting(populateSSOProvider(userGroup))
        .isSSOLinked(userGroup.isSsoLinked())
        .importedByScim(userGroup.isImportedByScim())
        .notificationSettings(notificationSettings);
  }

  @VisibleForTesting
  void sanitizeAppPermissions(final UserGroup userGroup) {
    try {
      Predicate<String> notExist = id -> !appService.exist(id);

      CollectionUtils.emptyIfNull(userGroup.getAppPermissions()).forEach(p -> {
        if (p != null && p.getAppFilter() != null) {
          CollectionUtils.emptyIfNull(p.getAppFilter().getIds()).removeIf(notExist);
        }
      });
    } catch (RuntimeException e) {
      log.error(String.format("Unable to sanitize application permissions of user group <%s>", userGroup), e);
    }
  }

  public QLCreateUserGroupPayload populateCreateUserGroupPayload(UserGroup userGroup, String requestId) {
    QLUserGroupBuilder builder = QLUserGroup.builder();
    QLUserGroup userGroupOutput = populateUserGroupOutput(userGroup, builder).build();
    return QLCreateUserGroupPayload.builder().clientMutationId(requestId).userGroup(userGroupOutput).build();
  }

  public QLUpdateUserGroupPayload populateUpdateUserGroupPayload(UserGroup userGroup, String requestId) {
    QLUserGroupBuilder builder = QLUserGroup.builder();
    QLUserGroup userGroupOutput = populateUserGroupOutput(userGroup, builder).build();
    return QLUpdateUserGroupPayload.builder().clientMutationId(requestId).userGroup(userGroupOutput).build();
  }

  public QLAddUserToUserGroupPayload populateAddUserToUserGroupPayload(UserGroup userGroup, String requestId) {
    QLUserGroupBuilder builder = QLUserGroup.builder();
    QLUserGroup userGroupOutput = populateUserGroupOutput(userGroup, builder).build();
    return QLAddUserToUserGroupPayload.builder().clientMutationId(requestId).userGroup(userGroupOutput).build();
  }

  public QLRemoveUserFromUserGroupPayload populateRemoveUserFromUserGroupPayload(
      UserGroup userGroup, String requestId) {
    QLUserGroupBuilder builder = QLUserGroup.builder();
    QLUserGroup userGroupOutput = populateUserGroupOutput(userGroup, builder).build();
    return QLRemoveUserFromUserGroupPayload.builder().clientMutationId(requestId).userGroup(userGroupOutput).build();
  }

  private QLNotificationSettings populateNotificationSettings(UserGroup userGroup) {
    NotificationSettings notificationSettings = userGroup.getNotificationSettings();
    if (notificationSettings == null) {
      return null;
    }
    return QLNotificationSettings.builder()
        .sendNotificationToMembers(notificationSettings.isUseIndividualEmails())
        .sendMailToNewMembers(notificationSettings.isSendMailToNewMembers())
        .groupEmailAddresses(notificationSettings.getEmailAddresses())
        .slackNotificationSetting(populateSlackNotificationSettings(notificationSettings))
        .microsoftTeamsWebhookUrl(notificationSettings.getMicrosoftTeamsWebhookUrl())
        .pagerDutyIntegrationKey(notificationSettings.getPagerDutyIntegrationKey())
        .build();
  }

  private QLSlackNotificationSetting populateSlackNotificationSettings(NotificationSettings notificationSettings) {
    SlackNotificationConfiguration slackconfig = notificationSettings.getSlackConfig();
    if (slackconfig == null) {
      return null;
    }
    return QLSlackNotificationSetting.builder()
        .slackChannelName(slackconfig.getName())
        .slackWebhookURL(slackconfig.getOutgoingWebhookUrl())
        .build();
  }

  public NotificationSettings populateNotificationSettingsEntity(QLNotificationSettings notificationSetting) {
    if (notificationSetting == null) {
      return new NotificationSettings(false, false, Collections.emptyList(), null, "", "");
    }
    QLSlackNotificationSetting slackNotificationInput = notificationSetting.getSlackNotificationSetting();
    SlackNotificationSetting slackConfig = null;
    if (slackNotificationInput != null) {
      slackConfig = new SlackNotificationSetting(
          slackNotificationInput.getSlackChannelName(), slackNotificationInput.getSlackWebhookURL());
    } else {
      slackConfig = new SlackNotificationSetting("", "");
    }
    boolean sendEmailToNewMembers = false;
    boolean sendNotitficationToNewMembers = false;
    if (notificationSetting.getSendNotificationToMembers() != null) {
      sendNotitficationToNewMembers = notificationSetting.getSendNotificationToMembers();
    }

    if (notificationSetting.getSendMailToNewMembers() != null) {
      sendEmailToNewMembers = notificationSetting.getSendMailToNewMembers();
    }

    return new NotificationSettings(sendNotitficationToNewMembers, sendEmailToNewMembers,
        notificationSetting.getGroupEmailAddresses() == null ? Collections.emptyList()
                                                             : notificationSetting.getGroupEmailAddresses(),
        slackConfig, notificationSetting.getPagerDutyIntegrationKey(),
        notificationSetting.getMicrosoftTeamsWebhookUrl());
  }

  public void validateTheUserIds(List<String> userIds, String accountId) {
    if (isEmpty(userIds)) {
      return;
    }
    List<String> idsInput = new ArrayList<>(userIds);
    Account account = accountService.get(accountId);
    PageRequest<User> req = aPageRequest()
                                .addFieldsIncluded("_id")
                                .addFilter(UserKeys.accounts, SearchFilter.Operator.IN, account)
                                .addFilter("_id", IN, userIds.toArray())
                                .build();
    PageResponse<User> res = userService.list(req, false);
    // This Ids are wrong
    List<String> idsPresent = res.stream().map(User::getUuid).collect(Collectors.toList());
    idsInput.removeAll(idsPresent);
    if (isNotEmpty(idsInput)) {
      throw new InvalidRequestException(
          String.format("Invalid id/s %s provided in the request", String.join(", ", idsInput)));
    }
  }

  public List<User> populateUserGroupMembersField(List<String> userGroupIds) {
    if (userGroupIds == null) {
      return null;
    }
    return userGroupIds.stream().map(id -> userService.get(id)).collect(Collectors.toList());
  }

  public UserGroupSSOSettings populateUserGroupSSOSettings(QLSSOSettingInput ssoProvider) {
    boolean isSSOLinked = false;
    String linkedSSOId = null;
    SSOType ssoType = null;
    String ssoGroupName = null;
    String linkedSsoDisplayName = null;
    String ssoGroupId = null;
    if (ssoProvider != null) {
      if (ssoProvider.getLdapSettings() != null && ssoProvider.getSamlSettings() != null) {
        throw new InvalidRequestException("Only one of saml/ldap setting can be set for a user group");
      }
      if (ssoProvider.getLdapSettings() != null || ssoProvider.getSamlSettings() != null) {
        isSSOLinked = true;
        QLLDAPSettingsInput ldapSettings = ssoProvider.getLdapSettings();
        QLSAMLSettingsInput samlSettings = ssoProvider.getSamlSettings();
        if (ldapSettings != null) {
          ssoGroupName = ldapSettings.getGroupName();
          linkedSSOId = ldapSettings.getSsoProviderId();
          ssoGroupId = ldapSettings.getGroupDN();
        }
        if (samlSettings != null) {
          ssoGroupName = samlSettings.getGroupName();
          linkedSSOId = samlSettings.getSsoProviderId();
          ssoGroupId = samlSettings.getGroupName();
        }
        if (isBlank(linkedSSOId)) {
          throw new InvalidRequestException("Invalid sso provider id given in the request");
        }
        SSOSettings ssoSettings = ssoSettingService.getSsoSettings(linkedSSOId);
        if (ssoSettings == null) {
          throw new InvalidRequestException(String.format("No sso settings exists for the id %s", linkedSSOId));
        }
        linkedSsoDisplayName = ssoSettings.getDisplayName();
        ssoType = ssoSettings.getType();
        if (ssoType == SSOType.SAML && samlSettings == null) {
          throw new InvalidRequestException(
              String.format("No saml setting provided for the saml sso Provider with id %s", linkedSSOId));
        }
        if (ssoType == SSOType.LDAP && ldapSettings == null) {
          throw new InvalidRequestException(
              String.format("No ldap setting provided for the ldap sso Provider with id %s", linkedSSOId));
        }
      }
    }
    return UserGroupSSOSettings.builder()
        .isSSOLinked(isSSOLinked)
        .linkedSSOId(linkedSSOId)
        .ssoType(ssoType)
        .ssoGroupName(ssoGroupName)
        .linkedSsoDisplayName(linkedSsoDisplayName)
        .ssoGroupId(ssoGroupId)
        .build();
  }

  public UserGroup populateUserGroupEntity(QLCreateUserGroupInput userGroupInput, String accountId) {
    userGroupPermissionValidator.validatePermission(userGroupInput.getPermissions(), accountId);
    AccountPermissions accountPermissions =
        userGroupPermissionsController.populateUserGroupAccountPermissionEntity(userGroupInput.getPermissions());
    Set<AppPermission> appPermissions =
        userGroupPermissionsController.populateUserGroupAppPermissionEntity(userGroupInput.getPermissions());
    validateTheUserIds(userGroupInput.getUserIds(), accountId);
    UserGroupSSOSettings userGroupSSOSettings = populateUserGroupSSOSettings(userGroupInput.getSsoSetting());
    if (isBlank(userGroupInput.getName())) {
      throw new InvalidRequestException("The user group name cannot be blank");
    }

    return UserGroup.builder()
        .name(userGroupInput.getName().trim())
        .description(userGroupInput.getDescription())
        .notificationSettings(populateNotificationSettingsEntity(userGroupInput.getNotificationSettings()))
        .memberIds(userGroupInput.getUserIds())
        .members(populateUserGroupMembersField(userGroupInput.getUserIds()))
        .isSsoLinked(userGroupSSOSettings.isSSOLinked())
        .linkedSsoId(userGroupSSOSettings.getLinkedSSOId())
        .linkedSsoDisplayName(userGroupSSOSettings.getLinkedSsoDisplayName())
        .linkedSsoType(userGroupSSOSettings.getSsoType())
        .ssoGroupId(userGroupSSOSettings.getSsoGroupId())
        .ssoGroupName(userGroupSSOSettings.getSsoGroupName())
        .appPermissions(appPermissions)
        .accountPermissions(accountPermissions)
        .build();
  }

  public void addUserToUserGroups(final User user, final List<String> userGroupIds, final String accountId) {
    final List<UserGroup> userGroups = userGroupService.fetchUserGroupNamesFromIds(userGroupIds);
    userService.updateUserGroupsOfUser(user.getUuid(), userGroups, accountId, true);
  }

  public void checkIfUserGroupIdsExist(final String accountId, List<String> userGroupIds) {
    if (isEmpty(userGroupIds)) {
      return;
    }
    List<String> idsInput = new ArrayList<>(userGroupIds);
    PageRequest<UserGroup> req = aPageRequest()
                                     .withLimit(Long.toString(userGroupService.getCountOfUserGroups(accountId)))
                                     .addFieldsIncluded("_id")
                                     .addFilter("_id", IN, userGroupIds.toArray())
                                     .addFilter("accountId", SearchFilter.Operator.EQ, accountId)
                                     .build();
    PageResponse<UserGroup> res = userGroupService.list(accountId, req, false, null, null);
    List<String> idsPresent = res.stream().map(UserGroup::getUuid).collect(Collectors.toList());
    idsInput.removeAll(idsPresent);
    if (isNotEmpty(idsInput)) {
      throw new InvalidRequestException(
          String.format("Invalid userGroupId: %s provided in the request", String.join(", ", idsInput)));
    }
  }
}
