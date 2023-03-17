/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.beans.custom.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.audit.beans.custom.AuditEventDataTypeConstants.NODE_EXECUTION_EVENT_DATA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditEventData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PIPELINE)
@Data
@Builder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(NODE_EXECUTION_EVENT_DATA)
@TypeAlias(NODE_EXECUTION_EVENT_DATA)
public class NodeExecutionEventData extends AuditEventData {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String pipelineIdentifier;
  String stageIdentifier;
  String stageType;
  String planExecutionId;
  String nodeExecutionId;
  String status;
  TriggeredByInfoAuditDetails triggeredBy;
  long startTs;
  long endTs;

  @Builder
  public NodeExecutionEventData(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String stageIdentifier, String stageType, String planExecutionId,
      String nodeExecutionId, String status, TriggeredByInfoAuditDetails triggeredBy, long startTs, long endTs) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.pipelineIdentifier = pipelineIdentifier;
    this.stageIdentifier = stageIdentifier;
    this.stageType = stageType;
    this.planExecutionId = planExecutionId;
    this.nodeExecutionId = nodeExecutionId;
    this.status = status;
    this.triggeredBy = triggeredBy;
    this.startTs = startTs;
    this.endTs = endTs;
    this.type = NODE_EXECUTION_EVENT_DATA;
  }
}