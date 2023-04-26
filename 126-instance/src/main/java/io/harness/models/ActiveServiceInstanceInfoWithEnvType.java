/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.models;

import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.contracts.execution.Status;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class ActiveServiceInstanceInfoWithEnvType {
  private String instanceKey;
  private String infrastructureMappingId;
  private String envIdentifier;
  private String envName;
  private EnvironmentType envType;
  private String infraIdentifier;
  private String infraName;
  private String clusterIdentifier;
  private String agentIdentifier;
  private long lastDeployedAt;
  private String displayName;
  private Integer count;
  private String lastPipelineExecutionName;
  private String lastPipelineExecutionId;
  private String stageNodeExecutionId;
  private Status stageStatus;
}
