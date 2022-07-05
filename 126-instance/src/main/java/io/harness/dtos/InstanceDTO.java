/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.InstanceType;
import io.harness.ng.core.environment.beans.EnvironmentType;

import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@OwnedBy(HarnessTeam.DX)
@Value
@Builder
public class InstanceDTO {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String instanceKey;
  InstanceType instanceType;
  String envIdentifier;
  String envName;
  EnvironmentType envType;
  String serviceIdentifier;
  String serviceName;
  String infrastructureMappingId;
  String infrastructureKind;
  String infraIdentifier;
  String infraName;
  String connectorRef;
  ArtifactDetails primaryArtifact;
  String lastDeployedById;
  String lastDeployedByName;
  @NonFinal @Setter long lastDeployedAt;
  String lastPipelineExecutionId;
  String lastPipelineExecutionName;
  InstanceInfoDTO instanceInfoDTO;
  boolean isDeleted;
  long deletedAt;
  long createdAt;
  long lastModifiedAt;
}
