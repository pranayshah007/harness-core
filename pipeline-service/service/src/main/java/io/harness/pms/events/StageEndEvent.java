/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.steps.barriers.beans.StageDetail;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PIPELINE)
@Getter
@NoArgsConstructor
public class StageEndEvent implements Event {
  private String orgIdentifier;
  private String accountIdentifier;
  private String projectIdentifier;
  private String pipelineIdentifier;
  private String pipelineExecutionUuid;
  private StageDetail stageDetail;

  public StageEndEvent(String orgIdentifier, String accountIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineExecutionUuid, StageDetail stageDetail, String nodeExecutionId,
      Long startTs, Long endTs) {
    this.orgIdentifier = orgIdentifier;
    this.accountIdentifier = accountIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.pipelineIdentifier = pipelineIdentifier;
    this.pipelineExecutionUuid = pipelineExecutionUuid;
    this.stageDetail = stageDetail;
    this.nodeExecutionId = nodeExecutionId;
    this.startTs = startTs;
    this.endTs = endTs;
  }

  private String nodeExecutionId;
  private Long startTs;
  private Long endTs;

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new ProjectScope(accountIdentifier, orgIdentifier, stageDetail.getIdentifier());
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, stageDetail.getIdentifier());
    return Resource.builder()
        .identifier(pipelineIdentifier)
        .type(ResourceTypeConstants.PIPELINE)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return PipelineOutboxEvents.STAGE_END;
  }
}
