/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import io.harness.pms.execution.ExecutionStatus;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionSummaryInfo {
  long lastExecutionTs;
  ExecutionStatus lastExecutionStatus;
  @Builder.Default Map<String, Integer> numOfErrors = new HashMap<>(); // total number of errors in the last ten days
  @Builder.Default
  Map<String, Integer> deployments =
      new HashMap<>(); // no of deployments for each of the last 10 days, most recent first
  String lastExecutionId;
}
