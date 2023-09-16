/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoUtils.setUnset;

import static software.wings.beans.EntityType.WORKFLOW;

import io.harness.migrations.Migration;
import io.harness.mongo.MongoPersistence;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.NotificationRule;
import software.wings.beans.Workflow;
import software.wings.beans.entity.UserGroupEntityReference;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddNotificationGroupReferenceToUserGroups implements Migration {
  private final String DEBUG_LINE = "USER-GROUP_ADD_NOTIFICATION_REF_MIGRATION: ";

  @Inject private WingsPersistence wingsPersistence;

  @Inject private WorkflowService workflowService;

  @Inject private UserGroupService userGroupService;

  @Inject private MongoPersistence mongoPersistence;

  public static class WorkflowMetadata {
    String accountId;
    String appId;
    String uuid;
    String entityType;
    WorkflowMetadata(String accountId, String appId, String uuid, String entityType) {
      this.accountId = accountId;
      this.appId = appId;
      this.uuid = uuid;
      this.entityType = entityType;
    }
  }

  @Override
  public void migrate() {
    log.info(DEBUG_LINE + "Starting to add notification rules reference in userGroups.");
    List<Account> allAccounts = new ArrayList<>();
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        allAccounts.add(accounts.next());
      }
    } catch (Exception ex) {
      log.error(DEBUG_LINE + "Exception while fetching Accounts");
    }
    for (Account account : allAccounts) {
      log.info(DEBUG_LINE + "Starting to add notification rules reference for account {}.", account.getAccountName());
      migrateWorkflowsForAccount(account);
    }
  }

  private void migrateWorkflowsForAccount(Account account) {
    Map<String, Set<WorkflowMetadata>> usergroupWorkflowMapping = new HashMap<String, Set<WorkflowMetadata>>() {
      @Override
      public Set<WorkflowMetadata> get(Object key) {
        Set<WorkflowMetadata> set = super.get(key);
        if (set == null) {
          set = new HashSet<WorkflowMetadata>();
          put((String) key, set);
        }
        return set;
      }
    };
    try {
      List<Workflow> workflows = WorkflowAndPipelineMigrationUtils.fetchAllWorkflowsForAccount(
          wingsPersistence, workflowService, account.getUuid());
      for (Workflow workflow : workflows) {
        CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
            (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
        Set<String> currentUserGroups = new HashSet<>();
        if (isNotEmpty(canaryOrchestrationWorkflow.getNotificationRules())) {
          for (NotificationRule notificationRule : canaryOrchestrationWorkflow.getNotificationRules()) {
            if (!notificationRule.isUserGroupAsExpression()) {
              if (isNotEmpty(notificationRule.getUserGroupIds())) {
                currentUserGroups.addAll(notificationRule.getUserGroupIds());
              }
            }
          }
        }
        for (String userGroup : currentUserGroups) {
          Set<WorkflowMetadata> wfMetadata = usergroupWorkflowMapping.get(userGroup);
          wfMetadata.add(
              new WorkflowMetadata(workflow.getAccountId(), workflow.getAppId(), workflow.getUuid(), WORKFLOW.name()));
          usergroupWorkflowMapping.put(userGroup, wfMetadata);
        }
      }
    } catch (Exception ex) {
      log.error(DEBUG_LINE + "Exception while fetching workflows for Account {} ", account.getUuid(), ex);
    }
    try {
      updateWorkflowReferenceInUserGroup(usergroupWorkflowMapping);
    } catch (Exception e) {
      log.error(DEBUG_LINE + "An error occurred when trying to reference workflows in userGroups ", e);
    }
  }

  private void updateWorkflowReferenceInUserGroup(Map<String, Set<WorkflowMetadata>> userGroupMapping) {
    for (Map.Entry<String, Set<WorkflowMetadata>> entry : userGroupMapping.entrySet()) {
      UserGroup userGroup = Optional.ofNullable(mongoPersistence.get(UserGroup.class, entry.getKey())).orElse(null);
      if (userGroup == null) {
        // log statement for userGroups which are deleted but are being referenced in a workflow's
        log.error(DEBUG_LINE + "UserGroup with id {} does not exist but are referenced in workflow's", entry.getKey());
        continue;
      }
      for (WorkflowMetadata wfMetadata : entry.getValue()) {
        log.info(DEBUG_LINE + "Adding workflow {} reference in userGroup {} ", wfMetadata.uuid, entry.getKey());
        try {
          userGroup.addParent(UserGroupEntityReference.builder()
                                  .entityType(wfMetadata.entityType)
                                  .id(wfMetadata.uuid)
                                  .appId(wfMetadata.appId)
                                  .accountId(wfMetadata.accountId)
                                  .build());
          UpdateOperations<UserGroup> ops = mongoPersistence.createUpdateOperations(UserGroup.class);
          setUnset(ops, "parents", userGroup.getParents());
          mongoPersistence.update(userGroup, ops);
        } catch (Exception e) {
          log.error(DEBUG_LINE + "An error occurred when trying to reference this workflow {} in userGroups ",
              wfMetadata.uuid, e);
        }
        log.info(DEBUG_LINE + "Added workflow {} reference in userGroup {} ", wfMetadata.uuid, entry.getKey());
      }
    }
  }
}
