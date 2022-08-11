/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;

import java.util.List;

@OwnedBy(HarnessTeam.DX)
public interface IInstanceSyncHandler {
  // Maps server instance dtos to the instance info dtos required for the instance entities
  List<InstanceInfoDTO> getInstanceDetailsFromServerInstances(List<ServerInstanceInfo> serverInstanceInfoList);

  // Create unique string instance key param from instance info that uniquely identifies the particular instance
  String getInstanceKey(InstanceInfoDTO instanceInfoDTO);

  // Get instance sync handler key from instance info that could relate all instances corresponding to a deployment info
  String getInstanceSyncHandlerKey(InstanceInfoDTO instanceInfoDTO);

  String getInstanceSyncHandlerKey(DeploymentInfoDTO deploymentInfoDTO);

  // Update and return instance with its corresponding instance info from server, if required
  // It will be no-op by default, responsibility on handler to implement it if required
  InstanceDTO updateInstance(InstanceDTO instanceDTO, InstanceInfoDTO instanceInfoFromServer);
}
