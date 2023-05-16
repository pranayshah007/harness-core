/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.DeploymentInfoDetailsDTO;
import io.harness.entities.InstanceType;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.DX)
public abstract class AbstractInstanceSyncHandler implements IInstanceSyncHandler {
  /**
   * Refer {@link io.harness.perpetualtask.PerpetualTaskType}
   * We need to do similar in NG. Every handler must return appropriate task type
   */
  public abstract String getPerpetualTaskType();

  public String getPerpetualTaskV2Type() {
    throw new UnsupportedOperationException();
  }

  public DeploymentReleaseDetails getDeploymentReleaseDetails(
      List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOList) {
    throw new UnsupportedOperationException();
  }

  public abstract InstanceType getInstanceType();

  /**
   * Refer {@link software.wings.beans.InfrastructureMappingType}
   * Need to do similar mapping in NG
   */
  public abstract String getInfrastructureKind();

  // Used for Dashboarding purposes
  public abstract InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO);

  public abstract DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList);

  protected abstract InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo);

  public final List<InstanceInfoDTO> getInstanceDetailsFromServerInstances(
      List<ServerInstanceInfo> serverInstanceInfoList) {
    List<InstanceInfoDTO> instanceInfoList = new ArrayList<>();
    serverInstanceInfoList.forEach(
        serverInstanceInfo -> instanceInfoList.add(getInstanceInfoForServerInstance(serverInstanceInfo)));
    return instanceInfoList;
  }

  public final List<InstanceInfoDTO> getInstanceDetailsFromInstances(List<InstanceDTO> serverInstanceInfoList) {
    List<InstanceInfoDTO> instanceInfoList = new ArrayList<>();
    serverInstanceInfoList.forEach(serverInstanceInfo -> instanceInfoList.add(serverInstanceInfo.getInstanceInfoDTO()));
    return instanceInfoList;
  }

  @Override
  public String getInstanceSyncHandlerKey(InstanceInfoDTO instanceInfoDTO) {
    return instanceInfoDTO.prepareInstanceSyncHandlerKey();
  }

  @Override
  public String getInstanceSyncHandlerKey(DeploymentInfoDTO deploymentInfoDTO) {
    return deploymentInfoDTO.prepareInstanceSyncHandlerKey();
  }

  @Override
  public String getInstanceKey(InstanceInfoDTO instanceInfoDTO) {
    return instanceInfoDTO.prepareInstanceKey();
  }

  @Override
  public InstanceDTO updateInstance(InstanceDTO instanceDTO, InstanceInfoDTO instanceInfoFromServer) {
    // Do nothing, handler should override it if required
    return instanceDTO;
  }
}
