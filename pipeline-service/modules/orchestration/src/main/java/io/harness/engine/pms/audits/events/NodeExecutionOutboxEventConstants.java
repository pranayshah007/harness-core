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
public final class NodeExecutionOutboxEventConstants {
  // Event Constants
  public static final String NODE_EXECUTION_EVENT = "NodeExecutionEvent";
  public static final String PIPELINE_START = "PipelineStart";
  public static final String PIPELINE_END = "PipelineEnd";
  public static final String PIPELINE_TIMEOUT = "PipelineTimeout";
  public static final String PIPELINE_ABORT = "PipelineAbort";
  public static final String STAGE_START = "StageStart";
  public static final String STAGE_END = "StageEnd";

  // Constants for stringUsages
  public static final String NODE_START_INFO = "NodeStartInfo";
  public static final String NODE_UPDATE_INFO = "NodeUpdateInfo";
  public static final String PIPELINE = "PIPELINE";
  public static final String STAGE = "STAGE";
  public static final String AUDIT_NOT_SUPPORTED_MSG = "Currently Audits are not supported for NodeGroup of type: %s";
  public static final String UNEXPECTED_ERROR_MSG = "Unexpected error occurred during handling of nodeGroup: %s";
  public static final String UNEXPECTED_ERROR_MSG_FOR_WITH_NODE_ID =
      "Unexpected error occurred during handling of nodeExecutionEvent with nodeExecutionId: %s";
  public static final String FIELDS_NOT_POPULATED_MSG =
      "Required fields to send an outBoxEvent are not populated in nodeInfo!";
}