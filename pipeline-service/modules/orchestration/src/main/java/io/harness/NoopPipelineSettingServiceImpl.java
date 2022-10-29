/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public class NoopPipelineSettingServiceImpl implements PipelineSettingsService {
  @Override
  public PlanExecutionSettingResponse shouldQueuePlanExecution(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    return PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build();
  }

  @Override
  public long getMaxPipelineCreationCount(String accountId) {
    return Long.MAX_VALUE;
  }

  @Override
  public int getMaxConcurrencyBasedOnEdition(String accountId, long childCount) {
    return Integer.MAX_VALUE;
  }
}
