/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import static io.harness.cvng.utils.VerifyStepMetricsAnalysisUtils.getAppliedDeploymentAnalysisTypeFromLearningEngineTaskType;

import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.ExecutionLogger;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisInput.AnalysisInputBuilder;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.HostSamplingState;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.cvng.verificationjob.utils.VerificationJobInstanceServiceInstanceUtils;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public abstract class HostSamplingStateExecutor<T extends HostSamplingState> extends AnalysisStateExecutor<T> {
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private TimeSeriesRecordService timeSeriesRecordService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Inject private ExecutionLogService executionLogService;

  protected abstract Set<String> getPostDeploymentHosts(
      VerificationJobInstance verificationJobInstance, AnalysisInput analysisInput);

  protected abstract Set<String> getPreDeploymentHosts(
      VerificationJobInstance verificationJobInstance, String verificationTaskId);

  protected abstract LearningEngineTaskType getBeforeAfterTaskType();

  protected abstract LearningEngineTaskType getCanaryTaskType();

  @Override
  public AnalysisState execute(HostSamplingState analysisState) {
    VerificationJobInstance verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(
        analysisState.getInputs().getVerificationJobInstanceId());
    VerificationJob verificationJob = verificationJobInstance.getResolvedJob();

    Set<String> preDeploymentHosts, postDeploymentHosts, newHosts, commonHosts;
    if (VerificationJobInstanceServiceInstanceUtils.canUseNodesFromCD(verificationJobInstance)) {
      preDeploymentHosts =
          new HashSet<>(verificationJobInstance.getServiceInstanceDetails().getServiceInstancesBeforeDeployment());
      postDeploymentHosts =
          new HashSet<>(verificationJobInstance.getServiceInstanceDetails().getServiceInstancesAfterDeployment());
    } else {
      preDeploymentHosts =
          getPreDeploymentHosts(verificationJobInstance, analysisState.getInputs().getVerificationTaskId());
      postDeploymentHosts = getPostDeploymentHosts(verificationJobInstance, analysisState.getInputs());
    }
    newHosts = new HashSet<>(postDeploymentHosts);
    commonHosts = new HashSet<>(preDeploymentHosts);
    commonHosts.retainAll(postDeploymentHosts);
    newHosts.removeAll(commonHosts);
    AnalysisInputBuilder analysisInputBuilder = AnalysisInput.builder();
    analysisInputBuilder.startTime(analysisState.getInputs().getStartTime());
    analysisInputBuilder.endTime(analysisState.getInputs().getEndTime());
    analysisInputBuilder.verificationTaskId(analysisState.getInputs().getVerificationTaskId());
    analysisState.setStatus(AnalysisStatus.RUNNING);
    ExecutionLogger executionLogger = executionLogService.getLogger(analysisState.getInputs().getVerificationTaskId(),
        analysisState.getInputs().getStartTime(), analysisState.getInputs().getEndTime());
    Set<String> testHosts, controlHosts;
    switch (verificationJob.getType()) {
      case CANARY:
        analysisInputBuilder.learningEngineTaskType(getCanaryTaskType());
        if (VerificationJobInstanceServiceInstanceUtils.canUseNodesFromCD(verificationJobInstance)) {
          testHosts = new HashSet<>(VerificationJobInstanceServiceInstanceUtils.getTestNodes(verificationJobInstance));
          controlHosts =
              new HashSet<>(VerificationJobInstanceServiceInstanceUtils.getControlNodes(verificationJobInstance));
        } else if (VerificationJobInstanceServiceInstanceUtils.isNodeRegExEnabled(verificationJobInstance)) {
          testHosts = VerificationJobInstanceServiceInstanceUtils.filterValidTestNodes(
              postDeploymentHosts, verificationJobInstance, executionLogger);
          controlHosts = VerificationJobInstanceServiceInstanceUtils.filterValidControlNodes(
              postDeploymentHosts, verificationJobInstance, executionLogger);
          if (StringUtils.isNotEmpty(verificationJobInstance.getServiceInstanceDetails().getTestNodeRegExPattern())) {
            controlHosts.removeAll(testHosts);
          } else {
            testHosts.removeAll(controlHosts);
          }
        } else if (newHosts.isEmpty()) {
          /*
           For example:
           Pre deployment hosts are n1, n2
           Post deployment hosts are also n1, n2
           */
          controlHosts = postDeploymentHosts;
          testHosts = new HashSet<>();
        } else if (!newHosts.equals(postDeploymentHosts)) {
          /*
           For example:
           Pre deployment hosts are n1, n2
           Post deployment hosts are n1, n2, n3
           */
          testHosts = newHosts;
          controlHosts = postDeploymentHosts;
          controlHosts.removeAll(testHosts);
        } else {
          /*
           For example:
           Pre deployment hosts are n1, n2
           Post deployment hosts are n3, n4
           */
          testHosts = postDeploymentHosts;
          controlHosts = new HashSet<>();
        }
        analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts).testHosts(testHosts);
        analysisState.setInputs(analysisInputBuilder.build());
        analysisState.setControlHosts(controlHosts);
        analysisState.setTestHosts(testHosts);
        break;
      case ROLLING:
      case BLUE_GREEN:
        if (VerificationJobInstanceServiceInstanceUtils.canUseNodesFromCD(verificationJobInstance)) {
          testHosts = new HashSet<>(VerificationJobInstanceServiceInstanceUtils.getTestNodes(verificationJobInstance));
          controlHosts =
              new HashSet<>(VerificationJobInstanceServiceInstanceUtils.getControlNodes(verificationJobInstance));
        } else {
          testHosts = VerificationJobInstanceServiceInstanceUtils.filterValidTestNodes(
              postDeploymentHosts, verificationJobInstance, executionLogger);
          controlHosts = VerificationJobInstanceServiceInstanceUtils.filterValidControlNodes(
              preDeploymentHosts, verificationJobInstance, executionLogger);
        }
        analysisState.setLearningEngineTaskType(getBeforeAfterTaskType());
        analysisInputBuilder.learningEngineTaskType(getBeforeAfterTaskType());
        analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts).testHosts(testHosts);
        analysisState.setInputs(analysisInputBuilder.build());
        analysisState.setControlHosts(controlHosts);
        analysisState.setTestHosts(testHosts);
        break;
      case AUTO:
        if (VerificationJobInstanceServiceInstanceUtils.canUseNodesFromCD(verificationJobInstance)) {
          testHosts = new HashSet<>(VerificationJobInstanceServiceInstanceUtils.getTestNodes(verificationJobInstance));
          controlHosts =
              new HashSet<>(VerificationJobInstanceServiceInstanceUtils.getControlNodes(verificationJobInstance));
          analysisState.setTestHosts(testHosts);
          analysisState.setControlHosts(controlHosts);
          analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts).testHosts(testHosts);
          if (VerificationJobInstanceServiceInstanceUtils.isValidCanaryDeployment(
                  verificationJobInstance.getServiceInstanceDetails())) {
            analysisState.setLearningEngineTaskType(getCanaryTaskType());
            analysisInputBuilder.learningEngineTaskType(getCanaryTaskType());
            analysisState.setInputs(analysisInputBuilder.build());
          } else {
            analysisState.setLearningEngineTaskType(getBeforeAfterTaskType());
            analysisInputBuilder.learningEngineTaskType(getBeforeAfterTaskType());
            analysisState.setInputs(analysisInputBuilder.build());
          }
        } else if (newHosts.isEmpty()) {
          /*
            For example:
            Pre deployments hosts and post deployments hosts both are n1, n2
          */
          controlHosts = VerificationJobInstanceServiceInstanceUtils.filterValidControlNodes(
              preDeploymentHosts, verificationJobInstance, executionLogger);
          testHosts = VerificationJobInstanceServiceInstanceUtils.filterValidTestNodes(
              postDeploymentHosts, verificationJobInstance, executionLogger);
          analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts)
                                     .testHosts(testHosts)
                                     .learningEngineTaskType(getBeforeAfterTaskType());
          analysisState.setInputs(analysisInputBuilder.build());
          analysisState.setLearningEngineTaskType(getBeforeAfterTaskType());
          analysisState.setControlHosts(controlHosts);
          analysisState.setTestHosts(testHosts);
        } else {
          controlHosts = VerificationJobInstanceServiceInstanceUtils.filterValidControlNodes(
              preDeploymentHosts, verificationJobInstance, executionLogger);
          testHosts = VerificationJobInstanceServiceInstanceUtils.filterValidTestNodes(
              newHosts, verificationJobInstance, executionLogger);
          analysisState.setTestHosts(testHosts);
          analysisState.setControlHosts(controlHosts);
          analysisInputBuilder = analysisInputBuilder.controlHosts(controlHosts).testHosts(testHosts);
          if (newHosts.equals(postDeploymentHosts)) {
            /*
              For example:
              Pre deployment hosts are n1, n2
              Post deployment hosts are n3, n4
             */
            analysisState.setLearningEngineTaskType(getBeforeAfterTaskType());
            analysisInputBuilder.learningEngineTaskType(getBeforeAfterTaskType());
            analysisState.setInputs(analysisInputBuilder.build());
          } else {
            /*
              For example:
              Pre deployment hosts are n1, n2
              Post deployment hosts are n1, n2, n3
             */
            analysisState.setLearningEngineTaskType(getCanaryTaskType());
            analysisInputBuilder.learningEngineTaskType(getCanaryTaskType());
            analysisState.setInputs(analysisInputBuilder.build());
          }
        }
        updateAppliedDeploymentAnalysisTypeForAutoVerification(
            verificationJobInstance.getUuid(), analysisState.getInputs().getVerificationTaskId(), analysisState);
        break;
      default:
        log.warn("Unrecognized verification job type.");
    }
    return analysisState;
  }

  private void updateAppliedDeploymentAnalysisTypeForAutoVerification(
      String verificationJobInstanceId, String verificationTaskId, HostSamplingState analysisState) {
    verificationJobInstanceService.updateAppliedDeploymentAnalysisTypeForVerificationTaskId(verificationJobInstanceId,
        verificationTaskId,
        getAppliedDeploymentAnalysisTypeFromLearningEngineTaskType(analysisState.getLearningEngineTaskType()));
  }

  @Override
  public AnalysisStatus getExecutionStatus(HostSamplingState analysisState) {
    return analysisState.getStatus();
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
    analysisState.setStatus(AnalysisStatus.TRANSITION);
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(HostSamplingState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  public abstract AnalysisState handleTransition(HostSamplingState analysisState);

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