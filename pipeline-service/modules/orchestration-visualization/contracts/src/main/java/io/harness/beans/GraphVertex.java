/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.GraphDelegateSelectionLogParams;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.UnitProgress;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.tasks.ProgressData;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(CDC)
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphVertex implements Serializable {
  private String uuid;
  private Ambiance ambiance;
  private String planNodeId;
  private String identifier;
  private String name;
  private Long startTs;
  private Long endTs;
  private Duration initialWaitDuration;
  private Long lastUpdatedAt;
  private String stepType;
  private Status status;
  private FailureInfo failureInfo;
  private PmsStepParameters stepParameters;
  private ExecutionMode mode;

  private String logBaseKey;
  private List<ExecutableResponse> executableResponses;
  private List<GraphDelegateSelectionLogParams> graphDelegateSelectionLogParams;
  private List<InterruptEffect> interruptHistories;
  private Map<String, PmsOutcome> outcomeDocuments;
  private List<String> retryIds;
  private Boolean executionInputConfigured;
  private Map<String, List<ProgressData>> progressDataMap;

  private SkipInfo skipInfo;
  private NodeRunInfo nodeRunInfo;
  // skip
  private SkipType skipType;

  private List<UnitProgress> unitProgresses;
  private OrchestrationMap progressData;
  private Map<String, PmsStepDetails> stepDetails;

  // UI
  @Builder.Default RepresentationStrategy representationStrategy = RepresentationStrategy.CAMELCASE;

  public PmsStepParameters getPmsStepParameters() {
    return PmsStepParameters.parse(
        OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(stepParameters));
  }

  public Map<String, PmsOutcome> getPmsOutcomes() {
    if (EmptyPredicate.isEmpty(outcomeDocuments)) {
      return new LinkedHashMap<>();
    }

    Map<String, PmsOutcome> outcomes = new LinkedHashMap<>();
    for (Map.Entry<String, ?> entry : outcomeDocuments.entrySet()) {
      outcomes.put(
          entry.getKey(), entry.getValue() == null ? null : PmsOutcome.parse((Map<String, Object>) entry.getValue()));
    }
    return outcomes;
  }

  public OrchestrationMap getPmsProgressData() {
    return OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(progressData);
  }
}
