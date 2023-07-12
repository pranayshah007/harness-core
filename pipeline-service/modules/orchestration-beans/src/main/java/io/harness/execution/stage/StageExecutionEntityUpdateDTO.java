/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.stage;

import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.utils.StageStatus;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StageExecutionEntityUpdateDTO {
  private String stageName;
  private String stageIdentifier;
  private Map<String, String> tags;
  private FailureInfo failureInfo;
  private StageExecutionSummaryDetails stageExecutionSummaryDetails;
  private Status status;
  private StageStatus stageStatus;
  private Long endTs;
}
