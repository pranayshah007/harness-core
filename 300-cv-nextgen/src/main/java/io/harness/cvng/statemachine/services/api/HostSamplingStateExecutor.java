/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.CanaryAnalysisState;
import io.harness.cvng.statemachine.entities.HostSamplingState;
import io.harness.cvng.statemachine.entities.ImprovisedCanaryAnalysisState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HostSamplingStateExecutor extends AnalysisStateExecutor<HostSamplingState> {
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private TimeSeriesRecordService timeSeriesRecordService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;

  @Override
  public AnalysisState execute(HostSamplingState analysisState) {
    VerificationJobInstance verificationJobInstance = analysisState.getVerificationJobInstance();
    VerificationJob verificationJob = verificationJobInstance.getResolvedJob();
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJob.getPreActivityTimeRange(verificationJobInstance.getDeploymentStartTime());
    List<TimeSeriesRecordDTO> preDeploymentTimeSeriesRecords =
        timeSeriesRecordService.getTimeSeriesRecordDTOs(analysisState.getInputs().getVerificationTaskId(),
            preDeploymentTimeRange.get().getStartTime(), preDeploymentTimeRange.get().getEndTime());

    List<TimeSeriesRecordDTO> postDeploymentTimeSeriesRecords =
        timeSeriesRecordService.getTimeSeriesRecordDTOs(analysisState.getInputs().getVerificationTaskId(),
            analysisState.getInputs().getStartTime(), analysisState.getInputs().getEndTime());

    Set<String> preDeploymentHosts = new HashSet<>();
    Set<String> postDeploymentHosts = new HashSet<>();
    for (TimeSeriesRecordDTO timeSeriesRecordDTO : preDeploymentTimeSeriesRecords) {
      preDeploymentHosts.add(timeSeriesRecordDTO.getHost());
    }
    for (TimeSeriesRecordDTO timeSeriesRecordDTO : postDeploymentTimeSeriesRecords) {
      postDeploymentHosts.add(timeSeriesRecordDTO.getHost());
    }

    // Case 1: v2 = postDeploymentHosts - (preDeploymentHosts and postDeploymentHosts intersection)
    // is None in that case test hosts is None and control data is postdeployment host
    // Case 2: v2 = postDeploymentHosts - (preDeploymentHosts and postDeploymentHosts intersection)
    // is not None and it's not equal to postDeployment Hosts
    // in that case control hosts are postDeployment old hosts, and test host are v2
    // Case 3: v2 = postDeploymentHosts - (preDeploymentHosts and postDeploymentHosts intersection)
    // is not None and is equal to postDeployment hosts
    // in that case test hosts is None and control hosts is postdeployment hosts
    Set<String> newHosts = new HashSet<>(postDeploymentHosts);
    Set<String> intersection = new HashSet<>(preDeploymentHosts);
    intersection.retainAll(postDeploymentHosts);
    newHosts.removeAll(intersection);
    switch (verificationJob.getType()) {
      case CANARY:
        // always canary
        analysisState.setLearningEngineTaskType(LearningEngineTaskType.CANARY);
        analysisState.setStatus(AnalysisStatus.RUNNING);
        if (newHosts.isEmpty()) {
          // predeployment nodes: n1, n2
          // postdeployment nodes: n1, n2
          Set<String> controlHosts = new HashSet<>(postDeploymentHosts);
          Set<String> testHosts = new HashSet<>();
          analysisState.setControlHosts(controlHosts);
          analysisState.setTestHosts(testHosts);
        } else if (!newHosts.equals(postDeploymentHosts)) {
          // predeployment nodes: n1, n2
          // postdeployment nodes: n1, n2, n3
          Set<String> testHosts = new HashSet<>(newHosts);
          Set<String> controlHosts = new HashSet<>(postDeploymentHosts);
          controlHosts.removeAll(testHosts);
          analysisState.setControlHosts(controlHosts);
          analysisState.setTestHosts(testHosts);
        } else {
          // predeployment nodes: n1, n2
          // postdeploymnet nodes: n3, n4
          Set<String> testHosts = new HashSet<>();
          Set<String> controlHosts = new HashSet<>();
          analysisState.setControlHosts(controlHosts);
          analysisState.setTestHosts(testHosts);
        }
        break;
      case ROLLING:
      case BLUE_GREEN:
        // always improvised canary
        analysisState.setLearningEngineTaskType(LearningEngineTaskType.IMPROVISED_CANARY);
        Set<String> controlHosts = new HashSet<>(preDeploymentHosts);
        Set<String> testHosts = new HashSet<>(postDeploymentHosts);
        analysisState.setControlHosts(controlHosts);
        analysisState.setTestHosts(testHosts);
        break;
      case AUTO:
        if (newHosts.isEmpty()) {
          // it's before after
          // predeployment nodes: n1, n2
          // postdeployment nodes: n1, n2
          controlHosts = new HashSet<>(preDeploymentHosts);
          testHosts = new HashSet<>(postDeploymentHosts);
          analysisState.setControlHosts(controlHosts);
          analysisState.setTestHosts(testHosts);
          analysisState.setLearningEngineTaskType(LearningEngineTaskType.IMPROVISED_CANARY);
        } else {
          // predeployment nodes: n1, n2 (or n1, n2)
          // postdeployment nodes: n1, n2, n3 (or n3, n4)
          controlHosts = new HashSet<>(preDeploymentHosts);
          testHosts = new HashSet<>(newHosts);
          analysisState.setTestHosts(testHosts);
          analysisState.setControlHosts(controlHosts);
          if (newHosts.equals(postDeploymentHosts)) {
            analysisState.setLearningEngineTaskType(LearningEngineTaskType.IMPROVISED_CANARY);
          } else {
            analysisState.setLearningEngineTaskType(LearningEngineTaskType.CANARY);
          }
        }
        break;
      default:
        log.warn("Unrecognized verification job type.");
    }
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(HostSamplingState analysisState) {
    if (!analysisState.getControlHosts().isEmpty() && !analysisState.getTestHosts().isEmpty()) {
      return AnalysisStatus.TRANSITION;
    }
    return AnalysisStatus.RUNNING;
  }

  @Override
  public AnalysisState handleRerun(HostSamplingState analysisState) {
    analysisState.setControlHosts(new HashSet<>());
    analysisState.setTestHosts(new HashSet<>());
    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    analysisState.setStatus(AnalysisStatus.RUNNING);
    return execute(analysisState);
  }

  @Override
  public AnalysisState handleRunning(HostSamplingState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(HostSamplingState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(HostSamplingState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    switch (analysisState.getLearningEngineTaskType()) {
      case CANARY:
        CanaryAnalysisState canaryAnalysisState = new CanaryAnalysisState();
        canaryAnalysisState.setLearningEngineTaskType(analysisState.getLearningEngineTaskType());
        canaryAnalysisState.setInputs(analysisState.getInputs());
        canaryAnalysisState.setControlHosts(analysisState.getControlHosts());
        canaryAnalysisState.setTestHosts(analysisState.getTestHosts());
        return canaryAnalysisState;
      case IMPROVISED_CANARY:
        ImprovisedCanaryAnalysisState improvisedCanaryAnalysisState = new ImprovisedCanaryAnalysisState();
        improvisedCanaryAnalysisState.setLearningEngineTaskType(analysisState.getLearningEngineTaskType());
        improvisedCanaryAnalysisState.setInputs(analysisState.getInputs());
        improvisedCanaryAnalysisState.setControlHosts(analysisState.getControlHosts());
        improvisedCanaryAnalysisState.setTestHosts(analysisState.getTestHosts());
        return improvisedCanaryAnalysisState;
      default:
        throw new AnalysisStateMachineException("Unknown learning engine task typein handleTransition "
            + "of HostSamplingState: " + analysisState.getLearningEngineTaskType());
    }
  }

  @Override
  public AnalysisState handleRetry(HostSamplingState analysisState) {
    if (analysisState.getRetryCount() >= getMaxRetry()) {
      analysisState.setStatus(AnalysisStatus.FAILED);
    } else {
      return handleRerun(analysisState);
    }
    return analysisState;
  }
}
