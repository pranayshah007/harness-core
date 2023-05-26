/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.models;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.models.infrastructuredetails.InfrastructureDetails;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.DX)
@Value
@Builder
public class InstanceDetailsDTO {
  String podName;
  String artifactName;
  String connectorRef;
  InfrastructureDetails infrastructureDetails;
  String terraformInstance;
  long deployedAt;
  String deployedById;
  String deployedByName;
  String pipelineExecutionName;
  InstanceInfoDTO instanceInfoDTO;
  String infrastructureMappingId;
  String instanceKey;
}
