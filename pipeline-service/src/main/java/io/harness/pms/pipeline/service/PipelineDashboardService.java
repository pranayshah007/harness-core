/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.Dashboard.DashboardPipelineExecutionInfo;
import io.harness.pms.Dashboard.DashboardPipelineHealthInfo;

@OwnedBy(HarnessTeam.CDC)
public interface PipelineDashboardService {
  DashboardPipelineHealthInfo getDashboardPipelineHealthInfo(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, long previousStartInterval, String moduleInfo);

  DashboardPipelineExecutionInfo getDashboardPipelineExecutionInfo(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, String moduleInfo);
}
