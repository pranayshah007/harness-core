package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentTimeSeriesAnalysisState;
import io.harness.cvng.statemachine.entities.HostSamplingState;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;

public class DeploymentLogHostSamplingStateExecutor extends HostSamplingStateExecutor<HostSamplingState> {
  @Inject VerificationJobInstanceService verificationJobInstanceService;

  @Inject HostRecordService hostRecordService;

  @Override
  protected Set<String> getPostDeploymentHosts(
      VerificationJobInstance verificationJobInstance, AnalysisInput analysisInput) {
    return hostRecordService.get(
        analysisInput.getVerificationTaskId(), analysisInput.getStartTime(), analysisInput.getEndTime());
  }

  @Override
  protected Set<String> getPreDeploymentHosts(
      VerificationJobInstance verificationJobInstance, String verificationTaskId) {
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJobInstanceService.getPreDeploymentTimeRange(verificationJobInstance.getUuid());
    return hostRecordService.get(
        verificationTaskId, preDeploymentTimeRange.get().getStartTime(), preDeploymentTimeRange.get().getEndTime());
  }

  @Override
  protected LearningEngineTask.LearningEngineTaskType getBeforeAfterTaskType() {
    return LearningEngineTask.LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_LOG;
  }

  @Override
  protected LearningEngineTask.LearningEngineTaskType getCanaryTaskType() {
    return LearningEngineTask.LearningEngineTaskType.CANARY_DEPLOYMENT_LOG;
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
}
