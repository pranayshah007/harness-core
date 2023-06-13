/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
public enum PipelineEventType {
  @JsonProperty(PipelineEventTypeConstants.ALL_EVENTS) ALL_EVENTS("Pipeline", PipelineEventTypeConstants.ALL_EVENTS),
  @JsonProperty(PipelineEventTypeConstants.PIPELINE_START)
  PIPELINE_START("Pipeline", PipelineEventTypeConstants.PIPELINE_START),
  @JsonProperty(PipelineEventTypeConstants.PIPELINE_SUCCESS)
  PIPELINE_SUCCESS("Pipeline", PipelineEventTypeConstants.PIPELINE_SUCCESS),
  @JsonProperty(PipelineEventTypeConstants.PIPELINE_FAILED)
  PIPELINE_FAILED("Pipeline", PipelineEventTypeConstants.PIPELINE_FAILED),
  @JsonProperty(PipelineEventTypeConstants.PIPELINE_END)
  PIPELINE_END("Pipeline", PipelineEventTypeConstants.PIPELINE_END),
  @JsonProperty(PipelineEventTypeConstants.PIPELINE_PAUSED)
  PIPELINE_PAUSED("Pipeline", PipelineEventTypeConstants.PIPELINE_PAUSED),
  @JsonProperty(PipelineEventTypeConstants.STAGE_SUCCESS)
  STAGE_SUCCESS("Stage", PipelineEventTypeConstants.STAGE_SUCCESS),
  @JsonProperty(PipelineEventTypeConstants.STAGE_FAILED) STAGE_FAILED("Stage", PipelineEventTypeConstants.STAGE_FAILED),
  @JsonProperty(PipelineEventTypeConstants.STAGE_START) STAGE_START("Stage", PipelineEventTypeConstants.STAGE_START),
  @JsonProperty(PipelineEventTypeConstants.STEP_FAILED) STEP_FAILED("Step", PipelineEventTypeConstants.STEP_FAILED);

  private String level;
  private String displayName;
  public static List<PipelineEventType> notifyOnlyUserEvents =
      Arrays.asList(PipelineEventType.PIPELINE_START, PipelineEventType.PIPELINE_END);
  public static List<PipelineEventType> startEvents = Arrays.asList(PIPELINE_START, STAGE_START);

  PipelineEventType(String level, String displayName) {
    this.level = level;
    this.displayName = displayName;
  }

  public String getLevel() {
    return level;
  }

  public String getDisplayName() {
    return displayName;
  }
}
