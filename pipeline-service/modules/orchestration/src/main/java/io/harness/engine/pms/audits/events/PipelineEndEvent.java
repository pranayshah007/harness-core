/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.audits.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PipelineEndEvent extends NodeExecutionEvent {
  private TriggerType triggerType;
  private TriggeredBy triggeredBy;
  private Status status;
  private long startTs;
  private long endTs;

  @Builder
  public PipelineEndEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId, TriggerType triggerType, TriggeredBy triggeredBy,
      Status status, Long startTs, Long endTs) {
    super(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, planExecutionId);
    this.triggerType = triggerType;
    this.triggeredBy = triggeredBy;
    this.status = status;
    this.startTs = startTs;
    this.endTs = endTs;
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return NodeExecutionOutboxEvents.PIPELINE_END;
  }
}