/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dto.FailureInfoDTO;
import io.harness.interrupts.InterruptEffectDTO;
import io.harness.logging.UnitProgress;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.execution.ExecutionStatus;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
@OwnedBy(PIPELINE)
public class ExecutionNode {
  String uuid;
  String setupId;
  String name;
  String identifier;
  String baseFqn;
  Map<String, OrchestrationMap> outcomes;
  OrchestrationMap stepParameters;
  Long startTs;
  Long endTs;
  String stepType;
  ExecutionStatus status;
  FailureInfoDTO failureInfo;
  SkipInfo skipInfo;
  NodeRunInfo nodeRunInfo;
  List<ExecutableResponse> executableResponses;
  List<UnitProgress> unitProgresses;
  OrchestrationMap progressData;
  List<DelegateInfo> delegateInfoList;
  List<InterruptEffectDTO> interruptHistories;
  Map<String, OrchestrationMap> stepDetails;
  StrategyMetadata strategyMetadata;
  Boolean executionInputConfigured;
  String logBaseKey;
}
