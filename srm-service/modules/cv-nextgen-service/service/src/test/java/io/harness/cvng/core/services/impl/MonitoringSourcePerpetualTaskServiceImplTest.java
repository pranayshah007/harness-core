/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.cvng.beans.DataCollectionType;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask.MonitoringSourcePerpetualTaskKeys;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.encryption.Scope;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class MonitoringSourcePerpetualTaskServiceImplTest extends CvNextGenTestBase {
  @Mock private VerificationManagerService verificationManagerService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private HPersistence hPersistence;
  private String accountId;
  private String connectorIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String monitoringSourceIdentifier;

  @Before
  public void setup() throws IllegalAccessException {
    accountId = generateUuid();
    orgIdentifier = generateUuid();
    projectIdentifier = generateUuid();
    connectorIdentifier = generateUuid();
    monitoringSourceIdentifier = generateUuid();
    FieldUtils.writeField(
        monitoringSourcePerpetualTaskService, "verificationManagerService", verificationManagerService, true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCreateTask() {
    monitoringSourcePerpetualTaskService.createTask(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, monitoringSourceIdentifier, false);
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority)
            .filter(MonitoringSourcePerpetualTaskKeys.accountId, accountId)
            .filter(MonitoringSourcePerpetualTaskKeys.orgIdentifier, orgIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.projectIdentifier, projectIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, monitoringSourceIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.connectorIdentifier, connectorIdentifier)
            .asList();
    assertThat(monitoringSourcePerpetualTasks).hasSize(2);
    assertThat(monitoringSourcePerpetualTasks.get(0).getPerpetualTaskId()).isNull();
    assertThat(monitoringSourcePerpetualTasks.get(1).getPerpetualTaskId()).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDeleteTask() {
    monitoringSourcePerpetualTaskService.createTask(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, monitoringSourceIdentifier, false);
    MonitoringSourcePerpetualTask monitoringSourcePerpetualTask =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority)
            .filter(MonitoringSourcePerpetualTaskKeys.accountId, accountId)
            .filter(MonitoringSourcePerpetualTaskKeys.orgIdentifier, orgIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.projectIdentifier, projectIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, monitoringSourceIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.connectorIdentifier, connectorIdentifier)
            .get();
    assertThat(monitoringSourcePerpetualTask).isNotNull();

    monitoringSourcePerpetualTaskService.deleteTask(
        accountId, orgIdentifier, projectIdentifier, monitoringSourceIdentifier);
    monitoringSourcePerpetualTask =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority)
            .filter(MonitoringSourcePerpetualTaskKeys.accountId, accountId)
            .filter(MonitoringSourcePerpetualTaskKeys.orgIdentifier, orgIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.projectIdentifier, projectIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, monitoringSourceIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.connectorIdentifier, connectorIdentifier)
            .get();
    assertThat(monitoringSourcePerpetualTask).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCreatePerpetualTask() {
    String taskId = generateUuid();
    when(verificationManagerService.createDataCollectionTask(
             eq(accountId), eq(orgIdentifier), eq(projectIdentifier), any(DataCollectionConnectorBundle.class)))
        .thenReturn(taskId);
    monitoringSourcePerpetualTaskService.createTask(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, monitoringSourceIdentifier, false);
    MonitoringSourcePerpetualTask monitoringSourcePerpetualTask =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority).get();
    assertThat(monitoringSourcePerpetualTask.getPerpetualTaskId()).isNull();
    monitoringSourcePerpetualTaskService.createPerpetualTask(monitoringSourcePerpetualTask);
    monitoringSourcePerpetualTask =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority).get();
    assertThat(monitoringSourcePerpetualTask.getPerpetualTaskId()).isEqualTo(taskId);
    assertThat(monitoringSourcePerpetualTask.getDataCollectionWorkerId())
        .isEqualTo(monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(
            accountId, orgIdentifier, projectIdentifier, connectorIdentifier, monitoringSourceIdentifier));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testFindByConnectorIdentifier_projectScope() {
    int numOfConnectors = 5;
    int numOfIdentifiers = 3;
    for (int i = 0; i < numOfIdentifiers; i++) {
      for (int j = 0; j < numOfConnectors; j++) {
        monitoringSourcePerpetualTaskService.createTask(accountId, orgIdentifier, projectIdentifier,
            "connectorIdentifier-" + j, "monitoringSourceIdentifier-" + i, false);
      }
    }

    for (int j = 0; j < numOfConnectors; j++) {
      List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
          monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
              accountId, orgIdentifier, projectIdentifier, "connectorIdentifier-" + j, Scope.PROJECT);
      assertThat(monitoringSourcePerpetualTasks.size()).isEqualTo(2 * numOfIdentifiers);

      monitoringSourcePerpetualTasks = monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
          accountId, orgIdentifier, projectIdentifier, "connectorIdentifier-" + j, Scope.ORG);
      assertThat(monitoringSourcePerpetualTasks).isEmpty();

      monitoringSourcePerpetualTasks = monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
          accountId, orgIdentifier, projectIdentifier, "connectorIdentifier-" + j, Scope.ACCOUNT);
      assertThat(monitoringSourcePerpetualTasks).isEmpty();
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testFindByConnectorIdentifier_accountScope() {
    int numOfConnectors = 5;
    int numOfIdentifiers = 3;
    for (int i = 0; i < numOfIdentifiers; i++) {
      for (int j = 0; j < numOfConnectors; j++) {
        monitoringSourcePerpetualTaskService.createTask(accountId, orgIdentifier, projectIdentifier,
            "account.connectorIdentifier-" + j, "monitoringSourceIdentifier-" + i, false);
      }
    }

    for (int j = 0; j < numOfConnectors; j++) {
      List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
          monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
              accountId, orgIdentifier, projectIdentifier, "connectorIdentifier-" + j, Scope.PROJECT);
      assertThat(monitoringSourcePerpetualTasks).isEmpty();

      monitoringSourcePerpetualTasks = monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
          accountId, orgIdentifier, projectIdentifier, "connectorIdentifier-" + j, Scope.ORG);
      assertThat(monitoringSourcePerpetualTasks).isEmpty();

      monitoringSourcePerpetualTasks = monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
          accountId, orgIdentifier, projectIdentifier, "connectorIdentifier-" + j, Scope.ACCOUNT);
      assertThat(monitoringSourcePerpetualTasks.size()).isEqualTo(2 * numOfIdentifiers);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testFindByConnectorIdentifier_OrgIdentifierScope() {
    int numOfConnectors = 5;
    int numOfIdentifiers = 3;
    for (int i = 0; i < numOfIdentifiers; i++) {
      for (int j = 0; j < numOfConnectors; j++) {
        monitoringSourcePerpetualTaskService.createTask(accountId, orgIdentifier, projectIdentifier,
            "org.connectorIdentifier-" + j, "monitoringSourceIdentifier-" + i, false);
      }
    }

    for (int j = 0; j < numOfConnectors; j++) {
      List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
          monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
              accountId, orgIdentifier, projectIdentifier, "connectorIdentifier-" + j, Scope.PROJECT);
      assertThat(monitoringSourcePerpetualTasks).isEmpty();

      monitoringSourcePerpetualTasks = monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
          accountId, orgIdentifier, projectIdentifier, "connectorIdentifier-" + j, Scope.ORG);
      assertThat(monitoringSourcePerpetualTasks.size()).isEqualTo(2 * numOfIdentifiers);

      monitoringSourcePerpetualTasks = monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
          accountId, orgIdentifier, projectIdentifier, "connectorIdentifier-" + j, Scope.ACCOUNT);
      assertThat(monitoringSourcePerpetualTasks).isEmpty();
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDeleteByProjectIdentifier() {
    int numOfProjects = 5;
    int numOfIdentifiers = 3;
    for (int i = 0; i < numOfProjects; i++) {
      for (int j = 0; j < numOfIdentifiers; j++) {
        monitoringSourcePerpetualTaskService.createTask(accountId, orgIdentifier, "projectIdentifier-" + i,
            connectorIdentifier, "monitoringSourceIdentifier-" + j, false);
      }
    }

    monitoringSourcePerpetualTaskService.deleteByProjectIdentifier(
        MonitoringSourcePerpetualTask.class, accountId, orgIdentifier, "projectIdentifier-2");
    for (int i = 0; i < numOfProjects; i++) {
      List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
          hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority)
              .filter(MonitoringSourcePerpetualTaskKeys.accountId, accountId)
              .filter(MonitoringSourcePerpetualTaskKeys.orgIdentifier, orgIdentifier)
              .filter(MonitoringSourcePerpetualTaskKeys.projectIdentifier, "projectIdentifier-" + i)
              .asList();

      if (i == 2) {
        assertThat(monitoringSourcePerpetualTasks).isEmpty();
      } else {
        assertThat(monitoringSourcePerpetualTasks.size()).isEqualTo(2 * numOfIdentifiers);
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDeleteByOrgIdentifier() {
    int numOfProjects = 5;
    int numOfIdentifiers = 3;
    int numOfOrgs = 7;
    for (int i = 0; i < numOfOrgs; i++) {
      for (int j = 0; j < numOfProjects; j++) {
        for (int k = 0; k < numOfIdentifiers; k++) {
          monitoringSourcePerpetualTaskService.createTask(accountId, "orgIdentifier-" + i, "projectIdentifier-" + j,
              connectorIdentifier, "monitoringSourceIdentifier-" + k, false);
        }
      }
    }

    monitoringSourcePerpetualTaskService.deleteByOrgIdentifier(
        MonitoringSourcePerpetualTask.class, accountId, "orgIdentifier-2");
    for (int i = 0; i < numOfOrgs; i++) {
      List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
          hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority)
              .filter(MonitoringSourcePerpetualTaskKeys.accountId, accountId)
              .filter(MonitoringSourcePerpetualTaskKeys.orgIdentifier, "orgIdentifier-" + i)
              .asList();

      if (i == 2) {
        assertThat(monitoringSourcePerpetualTasks).isEmpty();
      } else {
        assertThat(monitoringSourcePerpetualTasks.size()).isEqualTo(2 * numOfIdentifiers * numOfProjects);
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDeleteByAccountIdentifier() {
    int numOfProjects = 5;
    int numOfIdentifiers = 3;
    int numOfOrgs = 7;
    int numOfAccounts = 6;
    for (int i = 0; i < numOfAccounts; i++) {
      for (int j = 0; j < numOfOrgs; j++) {
        for (int k = 0; k < numOfProjects; k++) {
          for (int l = 0; l < numOfIdentifiers; l++) {
            monitoringSourcePerpetualTaskService.createTask("accountId-" + i, "orgIdentifier-" + j,
                "projectIdentifier-" + k, connectorIdentifier, "monitoringSourceIdentifier-" + l, false);
          }
        }
      }
    }

    monitoringSourcePerpetualTaskService.deleteByAccountIdentifier(MonitoringSourcePerpetualTask.class, "accountId-2");
    for (int i = 0; i < numOfAccounts; i++) {
      List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
          hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority)
              .filter(MonitoringSourcePerpetualTaskKeys.accountId, "accountId-" + i)
              .asList();

      if (i == 2) {
        assertThat(monitoringSourcePerpetualTasks).isEmpty();
      } else {
        assertThat(monitoringSourcePerpetualTasks.size()).isEqualTo(2 * numOfIdentifiers * numOfProjects * numOfOrgs);
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testResetLiveMonitoringPerpetualTask_whenNoTask() {
    monitoringSourcePerpetualTaskService.createTask(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, monitoringSourceIdentifier, false);
    MonitoringSourcePerpetualTask monitoringSourcePerpetualTask =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority).get();
    monitoringSourcePerpetualTaskService.resetLiveMonitoringPerpetualTask(monitoringSourcePerpetualTask);
    verify(verificationManagerService, never())
        .resetDataCollectionTask(
            anyString(), anyString(), anyString(), anyString(), any(DataCollectionConnectorBundle.class));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testResetLiveMonitoringPerpetualTask_whenTaskCreated() {
    monitoringSourcePerpetualTaskService.createTask(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, monitoringSourceIdentifier, false);
    MonitoringSourcePerpetualTask monitoringSourcePerpetualTask =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority).get();

    String taskId = generateUuid();
    when(verificationManagerService.createDataCollectionTask(
             eq(accountId), eq(orgIdentifier), eq(projectIdentifier), any(DataCollectionConnectorBundle.class)))
        .thenReturn(taskId);
    monitoringSourcePerpetualTaskService.createPerpetualTask(monitoringSourcePerpetualTask);
    monitoringSourcePerpetualTask =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority).get();
    monitoringSourcePerpetualTaskService.resetLiveMonitoringPerpetualTask(monitoringSourcePerpetualTask);
    verify(verificationManagerService, times(1))
        .resetDataCollectionTask(accountId, orgIdentifier, projectIdentifier, taskId,
            DataCollectionConnectorBundle.builder()
                .connectorIdentifier(connectorIdentifier)
                .sourceIdentifier(monitoringSourceIdentifier)
                .dataCollectionType(DataCollectionType.CV)
                .dataCollectionWorkerId(monitoringSourcePerpetualTask.getDataCollectionWorkerId())
                .build());
  }
}
