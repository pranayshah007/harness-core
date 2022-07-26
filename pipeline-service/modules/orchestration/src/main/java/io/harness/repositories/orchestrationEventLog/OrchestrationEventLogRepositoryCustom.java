/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.orchestrationEventLog;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.OrchestrationEventLog;

import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface OrchestrationEventLogRepositoryCustom {
  List<OrchestrationEventLog> findUnprocessedEvents(String planExecutionId, long lastUpdatedAt);
  void deleteLogsForGivenPlanExecutionId(String planExecutionId);
}
