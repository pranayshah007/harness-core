/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.pms.sdk.execution.beans.StageModuleInfo;

public interface ExecutionSummaryModuleInfoProvider {
  /**
   * Returns the module info to be stored at the pipeline level.
   * @param event
   * @return
   */
  PipelineModuleInfo getPipelineLevelModuleInfo(OrchestrationEvent event);

  /**
   * Returns the module info which should be published at the stage level.
   * @param event
   * @return
   */
  StageModuleInfo getStageLevelModuleInfo(OrchestrationEvent event);

  /**
   * Returns a boolean to check if the moduleInfoUpdate should run or not
   * @param event
   * @return
   */
  boolean shouldRun(OrchestrationEvent event);
}
