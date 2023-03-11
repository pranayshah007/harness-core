/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.audits.events;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public final class NodeExecutionOutboxEvents {
  public static final String NODE_EXECUTION_EVENT = "NodeExecutionEvent";
  public static final String PIPELINE_START = "PipelineStart";
  public static final String PIPELINE_PAUSE = "PipelinePause";
  public static final String PIPELINE_RESUME = "PipelineResume";
  public static final String PIPELINE_END = "PipelineEnd";
  public static final String PIPELINE_TIMEOUT = "PipelineTimeout";
  public static final String PIPELINE_ABORT = "PipelineAbort";
  public static final String STAGE_START = "StageStart";
  public static final String STAGE_END = "StageEnd";
}