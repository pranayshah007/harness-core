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

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
public class PipelineAbortEvent extends NodeExecutionEvent {
  private TriggeredInfo triggeredInfo;

  @Builder
  public PipelineAbortEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId, TriggeredInfo triggeredInfo, Integer runSequence) {
    super(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, planExecutionId, runSequence);
    this.triggeredInfo = triggeredInfo;
  }

  @Override
  public String getEventType() {
    return NodeExecutionOutboxEventConstants.PIPELINE_ABORT;
  }
}