/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.SystemWrapper;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.services.api.VerificationServiceSecretManager;
import io.harness.exception.InvalidArgumentsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.metrics.TimeSeriesDataRecord;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.intfc.VerificationService;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class LearningEngineAnalysisServiceImplTest extends WingsBaseTest {
  @Inject private VerificationServiceSecretManager verificationServiceSecretManager;
  @Inject private VerificationService learningEngineService;
  @Inject private HPersistence persistence;

  private String cvConfigId;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    cvConfigId = generateUUID();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetLatestTaskForCvConfigIds_EmptyConfigIds() {
    Optional<LearningEngineAnalysisTask> task = learningEngineService.getLatestTaskForCvConfigIds(null);
    assertThat(task).isEmpty();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetLatestTaskForCvConfigIds_EmptyTask() {
    Optional<LearningEngineAnalysisTask> task =
        learningEngineService.getLatestTaskForCvConfigIds(Collections.singletonList(cvConfigId));
    assertThat(task).isEmpty();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetLatestTaskForCvConfigIds_NonEmptyTask() {
    LearningEngineAnalysisTask logMLTask = LearningEngineAnalysisTask.builder()
                                               .cvConfigId(cvConfigId)
                                               .analysis_minute(1)
                                               .executionStatus(ExecutionStatus.SUCCESS)
                                               .ml_analysis_type(MLAnalysisType.LOG_ML)
                                               .build();
    persistence.save(logMLTask);
    LearningEngineAnalysisTask timeSeriesTask = LearningEngineAnalysisTask.builder()
                                                    .cvConfigId(cvConfigId)
                                                    .analysis_minute(2)
                                                    .executionStatus(ExecutionStatus.FAILED)
                                                    .ml_analysis_type(MLAnalysisType.TIME_SERIES)
                                                    .build();
    persistence.save(timeSeriesTask);
    LearningEngineAnalysisTask timeSeriesQueuedTask = LearningEngineAnalysisTask.builder()
                                                          .cvConfigId(cvConfigId)
                                                          .analysis_minute(3)
                                                          .executionStatus(ExecutionStatus.QUEUED)
                                                          .ml_analysis_type(MLAnalysisType.TIME_SERIES)
                                                          .build();
    persistence.save(timeSeriesQueuedTask);
    LearningEngineAnalysisTask feedbackTask = LearningEngineAnalysisTask.builder()
                                                  .cvConfigId(cvConfigId)
                                                  .analysis_minute(4)
                                                  .executionStatus(ExecutionStatus.FAILED)
                                                  .ml_analysis_type(MLAnalysisType.FEEDBACK_ANALYSIS)
                                                  .build();
    persistence.save(feedbackTask);
    LearningEngineAnalysisTask timeSeriesNewConfigTask = LearningEngineAnalysisTask.builder()
                                                             .cvConfigId("config2")
                                                             .analysis_minute(5)
                                                             .executionStatus(ExecutionStatus.FAILED)
                                                             .ml_analysis_type(MLAnalysisType.TIME_SERIES)
                                                             .build();
    persistence.save(timeSeriesNewConfigTask);

    Optional<LearningEngineAnalysisTask> learningEngineAnalysisTask =
        learningEngineService.getLatestTaskForCvConfigIds(Collections.singletonList(cvConfigId));
    assertThat(learningEngineAnalysisTask).isPresent();
    learningEngineAnalysisTask.ifPresent(task -> {
      assertThat(task.getAnalysis_minute()).isEqualTo(2);
      assertThat(task.getMl_analysis_type()).isEqualTo(MLAnalysisType.TIME_SERIES);
    });
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Log_NoData() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.LOG_ML, minute);
    assertThat(hasData).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Log_WithDataOutsideWindow() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    LogDataRecord logDataRecord = LogDataRecord.builder().cvConfigId(cvConfigId).timeStamp(minute - 20).build();
    persistence.save(logDataRecord);
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.LOG_ML, minute);
    assertThat(hasData).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Log_WithOtherConfigId() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    LogDataRecord logDataRecord = LogDataRecord.builder().cvConfigId("config2").timeStamp(minute - 10).build();
    persistence.save(logDataRecord);
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.LOG_ML, minute);
    assertThat(hasData).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Log_WithHearBeatRecord() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    LogDataRecord logDataRecord =
        LogDataRecord.builder().cvConfigId(cvConfigId).clusterLevel(ClusterLevel.H0).timeStamp(minute - 10).build();
    persistence.save(logDataRecord);
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.LOG_ML, minute);
    assertThat(hasData).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Log_WithData() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    LogDataRecord logDataRecord =
        LogDataRecord.builder().cvConfigId(cvConfigId).clusterLevel(ClusterLevel.L2).timeStamp(minute - 10).build();
    persistence.save(logDataRecord);
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.LOG_ML, minute);
    assertThat(hasData).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_LogCluster_WithData() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    LogDataRecord logDataRecord =
        LogDataRecord.builder().cvConfigId(cvConfigId).clusterLevel(ClusterLevel.L1).timeStamp(minute - 10).build();
    persistence.save(logDataRecord);
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.LOG_CLUSTER, minute);
    assertThat(hasData).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Metric_NoData() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.TIME_SERIES, minute);
    assertThat(hasData).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Metric_WithDataOutsideWindow() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    TimeSeriesDataRecord record = TimeSeriesDataRecord.builder().cvConfigId(cvConfigId).timeStamp(minute - 20).build();
    persistence.save(record);
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.TIME_SERIES, minute);
    assertThat(hasData).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Metric_WithOtherConfigId() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    TimeSeriesDataRecord record = TimeSeriesDataRecord.builder().cvConfigId("config2").timeStamp(minute - 10).build();
    persistence.save(record);
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.TIME_SERIES, minute);
    assertThat(hasData).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Metric_WithHearBeatRecord() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    TimeSeriesDataRecord record =
        TimeSeriesDataRecord.builder().cvConfigId(cvConfigId).level(ClusterLevel.H0).timeStamp(minute - 10).build();
    persistence.save(record);
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.TIME_SERIES, minute);
    assertThat(hasData).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Metric_WithData() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    TimeSeriesDataRecord record = TimeSeriesDataRecord.builder().cvConfigId(cvConfigId).timeStamp(minute - 10).build();
    persistence.save(record);
    boolean hasData = learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.TIME_SERIES, minute);
    assertThat(hasData).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckIfAnalysisHasData_Feedback() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(SystemWrapper.currentTimeMillis());
    assertThatThrownBy(
        () -> learningEngineService.checkIfAnalysisHasData(cvConfigId, MLAnalysisType.FEEDBACK_ANALYSIS, minute))
        .isInstanceOf(InvalidArgumentsException.class);
  }
}
