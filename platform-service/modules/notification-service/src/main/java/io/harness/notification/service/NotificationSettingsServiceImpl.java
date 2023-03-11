/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.common.ExpressionConstants.EXPR_END;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.utils.DelegateOwner.NG_DELEGATE_OWNER_CONSTANT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.notification.NotificationSettingConfigDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.SmtpConfig;
import io.harness.notification.entities.NotificationSetting;
import io.harness.notification.evaluator.SecretExpressionEvaluator;
import io.harness.notification.remote.SmtpConfigClient;
import io.harness.notification.remote.SmtpConfigResponse;
import io.harness.notification.repositories.NotificationSettingRepository;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.remote.client.CGRestUtils;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;
import io.harness.usergroups.UserGroupClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class NotificationSettingsServiceImpl implements NotificationSettingsService {
  private final UserGroupClient userGroupClient;
  private final UserClient userClient;
  private final NotificationSettingRepository notificationSettingRepository;
  private final SmtpConfigClient smtpConfigClient;
  private static final Pattern VALID_EXPRESSION_PATTERN =
      Pattern.compile("\\<\\+secrets.getValue\\((\\\"|\\')\\w*[\\.]?\\w*(\\\"|\\')\\)>");
  private static final String INVALID_EXPRESSION_EXCEPTION = "Expression provided is not valid";
  private static final Pattern SECRET_EXPRESSION = Pattern.compile(
      "\\$\\{ngSecretManager\\.obtain\\(\\\"\\w*[\\.]?\\w*\\\"\\, ([+-]?\\d*|0)\\)\\}|\\$\\{sweepingOutputSecrets\\.obtain\\(\"[\\S|.]+?\",\"[\\S|.]+?\"\\)}");

  private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final int INITIAL_MAP_SIZE = 4;

  private List<UserGroupDTO> getUserGroups(List<String> userGroupIds) {
    if (isEmpty(userGroupIds)) {
      return new ArrayList<>();
    }
    List<UserGroupDTO> userGroups = new ArrayList<>();
    try {
      UserGroupFilterDTO userGroupFilterDTO =
          UserGroupFilterDTO.builder().databaseIdFilter(new HashSet<>(userGroupIds)).build();
      userGroups = getResponse(userGroupClient.getFilteredUserGroups(userGroupFilterDTO));
    } catch (Exception ex) {
      log.error("Error while fetching user groups.", ex);
    }
    return userGroups;
  }

  private List<UserGroupDTO> getUserGroups(List<NotificationRequest.UserGroup> userGroups, String accountIdentifier) {
    if (isEmpty(userGroups)) {
      return new ArrayList<>();
    }
    List<UserGroupDTO> userGroupDTOS = new ArrayList<>();
    try {
      List<UserGroupFilterDTO> userGroupFilterDTO =
          userGroups.stream()
              .map(userGroup -> {
                String orgIdentifier = isEmpty(userGroup.getOrgIdentifier()) ? null : userGroup.getOrgIdentifier();
                String projectIdentifier =
                    isEmpty(userGroup.getProjectIdentifier()) ? null : userGroup.getProjectIdentifier();
                return UserGroupFilterDTO.builder()
                    .accountIdentifier(accountIdentifier)
                    .identifierFilter(Sets.newHashSet(ImmutableList.of(userGroup.getIdentifier())))
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .build();
              })
              .collect(Collectors.toList());

      for (UserGroupFilterDTO filterDTO : userGroupFilterDTO) {
        userGroupDTOS.addAll(getResponse(userGroupClient.getFilteredUserGroups(filterDTO)));
      }

    } catch (Exception ex) {
      log.error("Error while fetching user groups.", ex);
    }
    return userGroupDTOS;
  }

  @VisibleForTesting
  List<String> getEmailsForUserIds(List<String> userIds, String accountId) {
    if (isEmpty(userIds)) {
      return new ArrayList<>();
    }
    List<UserInfo> users = new ArrayList<>();
    try {
      users = CGRestUtils.getResponse(userClient.listUsers(accountId, UserFilterNG.builder().userIds(userIds).build()));
    } catch (Exception exception) {
      log.error("Failure while fetching emails of users from userIds", exception);
    }
    return users.stream().map(UserInfo::getEmail).collect(Collectors.toList());
  }

  @Override
  public List<String> getNotificationRequestForUserGroups(List<NotificationRequest.UserGroup> notificationUserGroups,
      NotificationChannelType notificationChannelType, String accountId, long expressionFunctorToken) {
    List<UserGroupDTO> userGroups = getUserGroups(notificationUserGroups, accountId);
    List<String> notificationSetting = getNotificationSettings(notificationChannelType, userGroups, accountId);
    return resolveUserGroups(notificationChannelType, notificationSetting, expressionFunctorToken);
  }

  @Override
  public List<String> getNotificationSettingsForGroups(
      List<String> userGroupIds, NotificationChannelType notificationChannelType, String accountId) {
    // get user groups by ids
    List<UserGroupDTO> userGroups = getUserGroups(userGroupIds);
    return getNotificationSettings(notificationChannelType, userGroups, accountId);
  }

  @VisibleForTesting
  List<String> getNotificationSettings(
      NotificationChannelType notificationChannelType, List<UserGroupDTO> userGroups, String accountId) {
    Set<String> notificationSettings = new HashSet<>();
    for (UserGroupDTO userGroupDTO : userGroups) {
      if (userGroupDTO.getNotificationConfigs() != null && isNotEmpty(userGroupDTO.getNotificationConfigs())) {
        for (NotificationSettingConfigDTO notificationSettingConfigDTO : userGroupDTO.getNotificationConfigs()) {
          if (notificationSettingConfigDTO.getType().equals(notificationChannelType)) {
            if (NotificationChannelType.EMAIL.equals(notificationChannelType)) {
              if (((EmailConfigDTO) notificationSettingConfigDTO).getSendEmailToAllUsers()) {
                notificationSettings.addAll(getEmailsForUserIds(userGroupDTO.getUsers(), accountId));
              }
            }
            if (notificationSettingConfigDTO.getSetting().isPresent()) {
              notificationSettings.add(notificationSettingConfigDTO.getSetting().get());
            }
          }
        }
      }
    }
    return Lists.newArrayList(notificationSettings);
  }

  @VisibleForTesting
  List<String> resolveUserGroups(
      NotificationChannelType notificationChannelType, List<String> notificationSetting, long expressionFunctorToken) {
    if (!notificationChannelType.equals(NotificationChannelType.EMAIL) && !notificationSetting.isEmpty()) {
      if (notificationSetting.get(0).startsWith(EXPR_START) && notificationSetting.get(0).endsWith(EXPR_END)) {
        if (!VALID_EXPRESSION_PATTERN.matcher(notificationSetting.get(0)).matches()) {
          throw new InvalidRequestException(INVALID_EXPRESSION_EXCEPTION);
        }
        log.info("Resolving UserGroup secrets expression");
        SecretExpressionEvaluator evaluator = new SecretExpressionEvaluator(expressionFunctorToken);
        Object resolvedExpressions = evaluator.resolve(notificationSetting, true);
        if (resolvedExpressions == null) {
          throw new InvalidRequestException(INVALID_EXPRESSION_EXCEPTION);
        }
        return (List<String>) resolvedExpressions;
      }
    }
    return notificationSetting;
  }

  @Override
  public Optional<NotificationSetting> getNotificationSetting(String accountId) {
    return notificationSettingRepository.findByAccountId(accountId);
  }

  @Override
  public boolean getSendNotificationViaDelegate(String accountId) {
    Optional<NotificationSetting> notificationSettingOptional =
        notificationSettingRepository.findByAccountId(accountId);
    return notificationSettingOptional.map(NotificationSetting::isSendNotificationViaDelegate).orElse(false);
  }

  @Override
  public Optional<SmtpConfig> getSmtpConfig(String accountId) {
    Optional<NotificationSetting> notificationSettingOptional =
        notificationSettingRepository.findByAccountId(accountId);
    return Optional.ofNullable(notificationSettingOptional.map(NotificationSetting::getSmtpConfig).orElse(null));
  }

  @Override
  public SmtpConfigResponse getSmtpConfigResponse(String accountId) {
    SmtpConfigResponse smtpConfigResponse = null;
    try {
      smtpConfigResponse = CGRestUtils.getResponse(smtpConfigClient.getSmtpConfig(accountId));
    } catch (Exception ex) {
      log.error("Rest call for getting smtp config failed: ", ex);
    }
    return smtpConfigResponse;
  }

  @Override
  public boolean checkIfWebhookIsSecret(List<String> webhooks) {
    for (String webhook : webhooks) {
      if (SECRET_EXPRESSION.matcher(webhook).matches()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public NotificationSetting setSendNotificationViaDelegate(String accountId, boolean sendNotificationViaDelegate) {
    Optional<NotificationSetting> notificationSettingOptional =
        notificationSettingRepository.findByAccountId(accountId);
    NotificationSetting notificationSetting =
        notificationSettingOptional.orElse(NotificationSetting.builder().accountId(accountId).build());
    notificationSetting.setSendNotificationViaDelegate(sendNotificationViaDelegate);
    notificationSettingRepository.save(notificationSetting);
    return notificationSetting;
  }

  @Override
  public NotificationSetting setSmtpConfig(String accountId, SmtpConfig smtpConfig) {
    Optional<NotificationSetting> notificationSettingOptional =
        notificationSettingRepository.findByAccountId(accountId);
    NotificationSetting notificationSetting =
        notificationSettingOptional.orElse(NotificationSetting.builder().accountId(accountId).build());
    notificationSetting.setSmtpConfig(smtpConfig);
    notificationSettingRepository.save(notificationSetting);
    return notificationSetting;
  }

  @Override
  public Map<String, String> buildTaskAbstractions(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(INITIAL_MAP_SIZE);
    String owner = taskSetupAbstractionHelper.getOwner(accountIdentifier, orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(NG_DELEGATE_OWNER_CONSTANT, owner);
    }

    abstractions.put(ACCOUNT_IDENTIFIER, accountIdentifier);
    abstractions.put(ORG_IDENTIFIER, orgIdentifier);
    abstractions.put(PROJECT_IDENTIFIER, projectIdentifier);

    return abstractions;
  }

  @Override
  public void deleteByAccount(String accountId) {
    notificationSettingRepository.deleteAllByAccountId(accountId);
  }
}
