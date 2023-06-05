/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.models;

import io.harness.entities.Instance;
import io.harness.entities.RollbackStatus;
import io.harness.pms.contracts.execution.Status;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InstanceGroupedByPipelineExecution {
  private String lastPipelineExecutionName;
  private String lastPipelineExecutionId;
  private long lastDeployedAt;
  private String stageNodeExecutionId;
  private Status stageStatus;
  private String stageSetupId;
  private RollbackStatus rollbackStatus;
  private List<Instance> instances;
}
