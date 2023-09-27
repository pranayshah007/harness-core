/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.audits.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
public class StageEndEvent extends NodeExecutionEvent {
  private String stageIdentifier;
  private String stageType;
  private long startTs;
  private String nodeExecutionId;
  private long endTs;
  private String status;
  private TriggeredInfo triggeredInfo;

  @Builder
  public StageEndEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId, String stageIdentifier, String stageType, Long startTs,
      String nodeExecutionId, Long endTs, String status, Integer runSequence, TriggeredInfo triggeredInfo) {
    super(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, planExecutionId, runSequence);
    this.stageIdentifier = stageIdentifier;
    this.stageType = stageType;
    this.startTs = startTs;
    this.nodeExecutionId = nodeExecutionId;
    this.endTs = endTs;
    this.status = status;
    this.triggeredInfo = triggeredInfo;
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return NodeExecutionOutboxEventConstants.STAGE_END;
  }
}