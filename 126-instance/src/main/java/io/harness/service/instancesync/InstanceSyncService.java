/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesync;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.models.DeploymentEvent;
import io.harness.perpetualtask.instancesync.InstanceSyncTaskDetails;

@OwnedBy(HarnessTeam.DX)
public interface InstanceSyncService {
  void processInstanceSyncForNewDeployment(DeploymentEvent deploymentEvent);
  void processInstanceSyncByPerpetualTask(String accountIdentifier, String perpetualTaskId,
      InstanceSyncPerpetualTaskResponse instanceSyncPerpetualTaskResponse);

  InstanceSyncTaskDetails fetchTaskDetails(String accountIdentifier, String perpetualTaskId);
}
