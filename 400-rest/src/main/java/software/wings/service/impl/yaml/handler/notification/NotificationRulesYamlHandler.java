/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.notification;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.NotificationGroup.NotificationGroupBuilder.aNotificationGroup;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.eraro.ErrorCode;
import io.harness.exception.HarnessException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.ExecutionScope;
import software.wings.beans.NotificationGroup;
import software.wings.beans.NotificationRule;
import software.wings.beans.NotificationRule.NotificationRuleBuilder;
import software.wings.beans.NotificationRule.Yaml;
import software.wings.beans.security.UserGroup;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.UserGroupService;
import software.wings.utils.Utils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 10/28/17
 */
@Singleton
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class NotificationRulesYamlHandler extends BaseYamlHandler<NotificationRule.Yaml, NotificationRule> {
  @Inject NotificationSetupService notificationSetupService;
  @Inject UserGroupService userGroupService;
  @Inject FeatureFlagService featureFlagService;
  @Inject AppService appService;

  private NotificationRule toBean(ChangeContext<Yaml> changeContext, List<ChangeContext> changeContextList) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    ExecutionScope executionScope = Utils.getEnumFromString(ExecutionScope.class, yaml.getExecutionScope());

    List<NotificationGroup> notificationGroups = Lists.newArrayList();

    if (isNotEmpty(yaml.getNotificationGroups())) {
      notificationGroups =
          yaml.getNotificationGroups()
              .stream()
              .map(notificationGroupName -> {
                if (yaml.isNotificationGroupAsExpression()) {
                  return aNotificationGroup().withName(notificationGroupName).build();
                }
                NotificationGroup notificationGroup =
                    notificationSetupService.readNotificationGroupByName(accountId, notificationGroupName);
                notNullCheck(
                    "Invalid notification group for the given name: " + notificationGroupName, notificationGroup, USER);
                // We only store couple of fields in workflow when created from the normal ui.
                // Sticking to the same approach.
                return aNotificationGroup()
                    .withUuid(notificationGroup.getUuid())
                    .withEditable(notificationGroup.isEditable())
                    .build();
              })
              .collect(toList());
    }

    List<ExecutionStatus> conditions = yaml.getConditions()
                                           .stream()
                                           .map(condition -> Utils.getEnumFromString(ExecutionStatus.class, condition))
                                           .collect(toList());

    List<String> userGroupIds = new ArrayList<>();
    if (isNotEmpty(yaml.getUserGroupNames())) {
      for (String userGroupName : yaml.getUserGroupNames()) {
        UserGroup userGroup = userGroupService.fetchUserGroupByName(accountId, userGroupName);
        notNullCheck("User group " + userGroupName + " doesn't exist", userGroup);
        userGroupIds.add(userGroup.getUuid());
      }
    } else {
      userGroupIds = yaml.getUserGroupIds();
    }

    return NotificationRuleBuilder.aNotificationRule()
        .withUuid(generateUuid())
        .withActive(true)
        .withBatchNotifications(false)
        .withConditions(conditions)
        .withExecutionScope(executionScope)
        .withNotificationGroupAsExpression(yaml.isNotificationGroupAsExpression())
        .withUserGroupAsExpression(yaml.isUserGroupAsExpression())
        .withUserGroupExpression(yaml.getUserGroupExpression())
        .withUserGroupIds(userGroupIds)
        .withNotificationGroups(notificationGroups)
        .build();
  }

  @Override
  public Yaml toYaml(NotificationRule notificationRule, String appId) {
    List<String> conditionList = notificationRule.getConditions().stream().map(Enum::name).collect(toList());

    List<String> notificationGroupList =
        notificationRule.getNotificationGroups()
            .stream()
            .map(notificationGroup -> {
              if (!notificationRule.isNotificationGroupAsExpression()) {
                NotificationGroup notificationGroupFromDB = notificationSetupService.readNotificationGroup(
                    notificationGroup.getAccountId(), notificationGroup.getUuid());
                notNullCheck("Invalid notification group for the given id: " + notificationGroup.getUuid(),
                    notificationGroupFromDB, USER);
                return notificationGroupFromDB.getName();
              }
              return notificationGroup.getName();
            })
            .collect(toList());
    String accountId = appService.getAccountIdByAppId(appId);

    List<String> userGroupNames = new ArrayList<>();
    if (isNotEmpty(notificationRule.getUserGroupIds())) {
      notificationRule.getUserGroupIds().forEach(userGroupId -> {
        UserGroup userGroup = userGroupService.get(userGroupId);
        if (!featureFlagService.isEnabled(FeatureName.REMOVE_USERGROUP_CHECK, accountId)) {
          notNullCheck(String.format("User group with id %s doesnot exist.", userGroupId), userGroup);
        } else {
          if (userGroup == null) {
            log.error("User group with id {} does not exist for notification rule {}", userGroupId, notificationRule,
                new Exception());
          }
        }
        if (userGroup != null) {
          userGroupNames.add(userGroup.getName());
        }
      });
    }

    return Yaml.builder()
        .conditions(conditionList)
        .executionScope(Utils.getStringFromEnum(notificationRule.getExecutionScope()))
        .notificationGroups(notificationGroupList)
        .notificationGroupAsExpression(notificationRule.isNotificationGroupAsExpression())
        .userGroupNames(userGroupNames)
        .userGroupAsExpression(notificationRule.isUserGroupAsExpression())
        .userGroupExpression(notificationRule.getUserGroupExpression())
        .build();
  }

  @Override
  public NotificationRule upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext)
      throws HarnessException {
    return toBean(changeContext, changeSetContext);
  }

  @Override
  public Class getYamlClass() {
    return NotificationRule.Yaml.class;
  }

  @Override
  public NotificationRule get(String accountId, String yamlFilePath) {
    throw new WingsException(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION);
  }

  @Override
  public void delete(ChangeContext<Yaml> changeContext) {
    // Do nothing
  }
}
