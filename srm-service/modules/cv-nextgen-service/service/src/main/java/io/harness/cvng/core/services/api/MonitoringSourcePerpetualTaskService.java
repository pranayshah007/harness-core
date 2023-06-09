/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.CVNGPerpetualTaskDTO;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.encryption.Scope;

import java.util.List;
import java.util.Optional;

public interface MonitoringSourcePerpetualTaskService extends DeleteEntityByHandler<MonitoringSourcePerpetualTask> {
  void createTask(String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier,
      String monitoringSourceIdentifier, boolean isDemo);

  void createDeploymentTaskAndPerpetualTaskInSyncForTemplateCV(String accountId, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, String monitoringSourceIdentifier, boolean isDemo);
  void deleteTask(String accountId, String orgIdentifier, String projectIdentifier, String monitoringSourceIdentifier);

  void deleteTask(String accountId, String orgIdentifier, String projectIdentifier, String monitoringSourceIdentifier,
      String connectorIdentifier);

  List<MonitoringSourcePerpetualTask> listByConnectorIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, Scope scope);
  void createPerpetualTask(MonitoringSourcePerpetualTask monitoringSourcePerpetualTask);
  void resetLiveMonitoringPerpetualTask(MonitoringSourcePerpetualTask monitoringSourcePerpetualTask);
  String getLiveMonitoringWorkerId(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String monitoringSourceIdentifier);

  String getDeploymentWorkerId(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String monitoringSourceIdentifier);

  Optional<CVNGPerpetualTaskDTO> getPerpetualTaskStatus(String dataCollectionWorkerId);
}
