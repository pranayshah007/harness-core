/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PipelineEventTypeConstants {
  String ALL_EVENTS = "AllEvents";
  String PIPELINE_START = "PipelineStart";
  String PIPELINE_SUCCESS = "PipelineSuccess";
  String PIPELINE_FAILED = "PipelineFailed";
  String PIPELINE_PAUSED = "PipelinePaused";
  String STAGE_SUCCESS = "StageSuccess";
  String STAGE_START = "StageStart";
  String STAGE_FAILED = "StageFailed";
  String STEP_FAILED = "StepFailed";
  String PIPELINE_END = "PipelineEnd";
}
