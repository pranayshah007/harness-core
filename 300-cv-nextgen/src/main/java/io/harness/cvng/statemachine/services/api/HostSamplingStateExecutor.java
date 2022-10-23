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
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisInput.AnalysisInputBuilder;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentTimeSeriesAnalysisState;
import io.harness.cvng.statemachine.entities.HostSamplingState;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HostSamplingStateExecutor extends AnalysisStateExecutor<HostSamplingState> {
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private TimeSeriesRecordService timeSeriesRecordService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;

  @Override
  public AnalysisState execute(HostSamplingState analysisState) {
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(analysisState.getVerificationJobInstanceId());
    VerificationJob verificationJob = verificationJobInstance.getResolvedJob();
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJob.getPreActivityTimeRange(verificationJobInstance.getDeploymentStartTime());
    List<TimeSeriesRecordDTO> preDeploymentTimeSeriesRecords =
        timeSeriesRecordService.getTimeSeriesRecordDTOs(analysisState.getInputs().getVerificationTaskId(),
            preDeploymentTimeRange.get().getStartTime(), preDeploymentTimeRange.get().getEndTime());

    List<TimeSeriesRecordDTO> postDeploymentTimeSeriesRecords =
        timeSeriesRecordService.getTimeSeriesRecordDTOs(analysisState.getInputs().getVerificationTaskId(),
            analysisState.getInputs().getStartTime(), analysisState.getInputs().getEndTime());

    Set<String> preDeploymentHosts =
        preDeploymentTimeSeriesRecords.stream().map(TimeSeriesRecordDTO::getHost).collect(Collectors.toSet());
    Set<String> postDeploymentHosts =
        postDeploymentTimeSeriesRecords.stream().map(TimeSeriesRecordDTO::getHost).collect(Collectors.toSet());

    Set<String> newHosts = new HashSet<>(postDeploymentHosts);
    Set<String> commonHosts = new HashSet<>(preDeploymentHosts);
    commonHosts.retainAll(postDeploymentHosts);
    newHosts.removeAll(commonHosts);
    AnalysisInputBuilder analysisInputBuilder = AnalysisInput.builder();
    analysisInputBuilder.startTime(analysisState.getInputs().getStartTime());
    analysisInputBuilder.endTime(analysisState.getInputs().getEndTime());
    analysisInputBuilder.verificationTaskId(analysisState.getInputs().getVerificationTaskId());
    analysisState.setStatus(AnalysisStatus.RUNNING);
    switch (verificationJob.getType()) {
      case CANARY:
        analysisInputBuilder.learningEngineTaskType(LearningEngineTaskType.CANARY_DEPLOYMENT_TIME_SERIES);
        if (newHosts.isEmpty()) {
          /*
           For example:
           Pre deployment hosts are n1, n2
           Post deployment hosts are also n1, n2
           */
          Set<String> controlHosts = new HashSet<>(postDeploymentHosts);
          Set<String> testHosts = new HashSet<>();
          analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts).testHosts(testHosts);
          analysisState.setInputs(analysisInputBuilder.build());
          analysisState.setControlHosts(controlHosts);
          analysisState.setTestHosts(testHosts);
        } else if (!newHosts.equals(postDeploymentHosts)) {
          /*
           For example:
           Pre deployment hosts are n1, n2
           Post deployment hosts are n1, n2, n3
           */
          Set<String> testHosts = new HashSet<>(newHosts);
          Set<String> controlHosts = new HashSet<>(postDeploymentHosts);
          controlHosts.removeAll(testHosts);
          analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts).testHosts(testHosts);
          analysisState.setInputs(analysisInputBuilder.build());
          analysisState.setControlHosts(controlHosts);
          analysisState.setTestHosts(testHosts);
        } else {
          /*
           For example:
           Pre deployment hosts are n1, n2
           Post deployment hosts are n3, n4
           */
          Set<String> testHosts = new HashSet<>();
          Set<String> controlHosts = new HashSet<>(preDeploymentHosts);
          analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts).testHosts(testHosts);
          analysisState.setInputs(analysisInputBuilder.build());
          analysisState.setControlHosts(controlHosts);
          analysisState.setTestHosts(testHosts);
        }
        break;
      case ROLLING:
      case BLUE_GREEN:
        Set<String> controlHosts = new HashSet<>(preDeploymentHosts);
        Set<String> testHosts = new HashSet<>(postDeploymentHosts);
        analysisState.setLearningEngineTaskType(LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES);
        analysisInputBuilder.learningEngineTaskType(LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES);
        analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts).testHosts(testHosts);
        analysisState.setInputs(analysisInputBuilder.build());
        analysisState.setControlHosts(controlHosts);
        analysisState.setTestHosts(testHosts);
        break;
      case AUTO:
        if (newHosts.isEmpty()) {
          /*
            For example:
            Pre deployments hosts and post deployments hosts both are n1, n2
          */
          controlHosts = new HashSet<>(preDeploymentHosts);
          testHosts = new HashSet<>(postDeploymentHosts);
          analysisInputBuilder =
              analysisInputBuilder.controlHosts(controlHosts)
                  .testHosts(testHosts)
                  .learningEngineTaskType(LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES);
          analysisState.setInputs(analysisInputBuilder.build());
          analysisState.setLearningEngineTaskType(LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES);
          analysisState.setControlHosts(controlHosts);
          analysisState.setTestHosts(testHosts);
        } else {
          controlHosts = new HashSet<>(preDeploymentHosts);
          testHosts = new HashSet<>(newHosts);
          analysisState.setTestHosts(testHosts);
          analysisState.setControlHosts(controlHosts);
          analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts).testHosts(testHosts);
          if (newHosts.equals(postDeploymentHosts)) {
            /*
              For example:
              Pre deployment hosts are n1, n2
              Post deployment hosts are n3, n4
             */
            analysisState.setLearningEngineTaskType(LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES);
            analysisInputBuilder.learningEngineTaskType(LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES);
            analysisState.setInputs(analysisInputBuilder.build());
          } else {
            /*
              For example:
              Pre deployment hosts are n1, n2
              Post deployment hosts are n1, n2, n3
             */
            analysisState.setLearningEngineTaskType(LearningEngineTaskType.CANARY_DEPLOYMENT_TIME_SERIES);
            analysisInputBuilder.learningEngineTaskType(LearningEngineTaskType.CANARY_DEPLOYMENT_TIME_SERIES);
            analysisState.setInputs(analysisInputBuilder.build());
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
    if (analysisState.getControlHosts() != null || analysisState.getTestHosts() != null) {
      return AnalysisStatus.TRANSITION;
    }
    if (analysisState.getStatus() == AnalysisStatus.SUCCESS) {
      return AnalysisStatus.SUCCESS;
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
    DeploymentTimeSeriesAnalysisState deploymentTimeSeriesAnalysisState = new DeploymentTimeSeriesAnalysisState();
    deploymentTimeSeriesAnalysisState.setInputs(analysisState.getInputs());
    deploymentTimeSeriesAnalysisState.setStatus(AnalysisStatus.CREATED);
    deploymentTimeSeriesAnalysisState.setVerificationJobInstanceId(analysisState.getVerificationJobInstanceId());
    return deploymentTimeSeriesAnalysisState;
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
