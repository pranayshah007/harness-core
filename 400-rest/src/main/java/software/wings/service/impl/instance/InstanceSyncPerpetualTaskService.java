/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import software.wings.api.DeploymentSummary;
import software.wings.beans.InfrastructureMapping;
import software.wings.service.intfc.ownership.OwnedByInfrastructureMapping;

import java.util.List;

public interface InstanceSyncPerpetualTaskService extends OwnedByInfrastructureMapping {
  void createPerpetualTasks(InfrastructureMapping infrastructureMapping);

  void createPerpetualTasksForNewDeployment(
      InfrastructureMapping infrastructureMapping, List<DeploymentSummary> deploymentSummaries);

  void deletePerpetualTasks(InfrastructureMapping infrastructureMapping);

  void deletePerpetualTasks(String accountId, String infrastructureMappingId);

  void resetPerpetualTask(String accountId, String perpetualTaskId);

  default void deletePerpetualTask(String accountId, String infrastructureMappingId, String perpetualTaskId) {
    deletePerpetualTask(accountId, infrastructureMappingId, perpetualTaskId, false);
  }

  void deletePerpetualTask(String accountId, String infrastructureMappingId, String perpetualTaskId, boolean backup);

  void restorePerpetualTasks(String accountId, InfrastructureMapping infrastructureMapping);
}
