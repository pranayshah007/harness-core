/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dto;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.RepresentationStrategy;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.UnitProgress;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphVertexDTO {
  String uuid;
  AmbianceDTO ambiance;
  String planNodeId;
  String identifier;
  String name;
  Long startTs;
  Long endTs;
  Duration initialWaitDuration;
  Long lastUpdatedAt;
  String stepType;
  Status status;
  FailureInfoDTO failureInfo;
  SkipInfo skipInfo;
  NodeRunInfo nodeRunInfo;
  PmsStepParameters stepParameters;
  ExecutionMode mode;
  private Boolean executionInputConfigured;

  private String logBaseKey;
  List<GraphDelegateSelectionLogParams> graphDelegateSelectionLogParams;
  List<ExecutableResponse> executableResponses;
  List<InterruptEffect> interruptHistories;
  Map<String, PmsOutcome> outcomes;
  List<String> retryIds;

  List<UnitProgress> unitProgresses;
  OrchestrationMap progressData;
  Map<String, PmsStepDetails> stepDetails;

  // skip
  SkipType skipType;

  // UI
  RepresentationStrategy representationStrategy = RepresentationStrategy.CAMELCASE;
  StrategyMetadata strategyMetadata;

  public Map<String, OrchestrationMap> getOrchestrationMapOutcomes() {
    return OrchestrationMapBackwardCompatibilityUtils.convertToOrchestrationMap(outcomes);
  }

  public Map<String, OrchestrationMap> getOrchestrationMapStepDetails() {
    return OrchestrationMapBackwardCompatibilityUtils.convertToOrchestrationMap(stepDetails);
  }
}
