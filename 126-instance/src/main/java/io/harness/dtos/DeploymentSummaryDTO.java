/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.RollbackStatus;
import io.harness.pms.contracts.execution.Status;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(HarnessTeam.DX)
@Getter
@Builder
public class DeploymentSummaryDTO {
  String id;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String pipelineExecutionId;
  String pipelineExecutionName;
  String stageNodeExecutionId;
  Status stageStatus;
  String stageSetupId;
  RollbackStatus rollbackStatus;
  // TODO create dto for artifact details
  @Setter ArtifactDetails artifactDetails;
  String deployedById;
  String deployedByName;
  String infrastructureMappingId;
  String infrastructureIdentifier;
  String infrastructureName;
  String instanceSyncKey;
  @Setter @Nullable InfrastructureMappingDTO infrastructureMapping;
  // status of server instances when deployment happened
  // only used during new deployment instance sync flow
  @Setter List<ServerInstanceInfo> serverInstanceInfoList;
  DeploymentInfoDTO deploymentInfoDTO;
  @Setter boolean isRollbackDeployment;
  long deployedAt;
  long createdAt;
  long lastModifiedAt;

  private String envGroupRef;
}
