/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.TimeSeriesRecord;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(CV)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public class SLODebugResponse {
  ProjectParams projectParams;
  SimpleServiceLevelObjective simpleServiceLevelObjective;

  List<ServiceLevelIndicator> serviceLevelIndicatorList;

  SLOHealthIndicator sloHealthIndicator;

  Map<String, VerificationTask> sliIdentifierToVerificationTaskMap;

  Map<String, List<DataCollectionTask>> sliIdentifierToDataCollectionTaskMap;

  Map<String, AnalysisStateMachine> sliIdentifierToAnalysisStateMachineMap;

  Map<String, List<SLIRecord>> sliIdentifierToSLIRecordMap;

  Map<String, List<TimeSeriesRecord>> sliIdentifierToTimeSeriesRecords;

  Map<String, AnalysisOrchestrator> sliIdentifierToAnalysisOrchestrator;
}
