package io.harness.cvng.statemachine.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.NAVEEN;

import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.entities.DeploymentLogFeedbackState;
import io.harness.cvng.statemachine.services.api.DeploymentLogFeedbackStateExecutor;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DeploymentLogFeedbackStateExecutorTest {
  private String verificationTaskId;
  private Instant startTime;
  private Instant endTime;
  private BuilderFactory builderFactory;
  private VerificationJobInstance verificationJobInstance;

  @Mock private VerificationJobInstanceService verificationJobInstanceService;
  @Mock private LogAnalysisService logAnalysisService;
  @Mock private TimeSeriesAnalysisService timeSeriesAnalysisService;

  private DeploymentLogFeedbackState deploymentLogFeedbackState;
  private final DeploymentLogFeedbackStateExecutor deploymentLogFeedbackStateExecutor =
      new DeploymentLogFeedbackStateExecutor();

  public List<TimeSeriesRecordDTO> getTimeSeriesRecordDTO(List<String> hosts) {
    return hosts.stream().map(h -> TimeSeriesRecordDTO.builder().host(h).build()).collect(Collectors.toList());
  }

  @Before
  public void setup() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);

    verificationTaskId = generateUuid();
    startTime = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    endTime = startTime.plus(5, ChronoUnit.MINUTES);

    verificationJobInstance = VerificationJobInstance.builder()
                                  .deploymentStartTime(Instant.now())
                                  .startTime(Instant.now().plus(Duration.ofMinutes(2)))
                                  .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
                                  .build();

    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(startTime)
                              .verificationJobInstanceId(verificationJobInstance.getUuid())
                              .endTime(endTime)
                              .build();

    deploymentLogFeedbackState = new DeploymentLogFeedbackState();
    deploymentLogFeedbackState.setInputs(input);
    FieldUtils.writeField(
        deploymentLogFeedbackStateExecutor, "verificationJobInstanceService", verificationJobInstanceService, true);
    FieldUtils.writeField(
        deploymentLogFeedbackStateExecutor, "timeSeriesAnalysisService", timeSeriesAnalysisService, true);
    FieldUtils.writeField(deploymentLogFeedbackStateExecutor, "logAnalysisService", logAnalysisService, true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testExecuteSuccess() {
    String workerTaskId = UUID.randomUUID().toString();
    when(verificationJobInstanceService.getVerificationJobInstance(
             deploymentLogFeedbackState.getInputs().getVerificationJobInstanceId()))
        .thenReturn(verificationJobInstance);
    when(logAnalysisService.scheduleDeploymentLogFeedbackTask(deploymentLogFeedbackState.getInputs()))
        .thenReturn(workerTaskId);
    AnalysisState analysisState = deploymentLogFeedbackStateExecutor.execute(deploymentLogFeedbackState);
    assert analysisState.getType() == AnalysisState.StateType.DEPLOYMENT_LOG_FEEDBACK_STATE;
  }
}
