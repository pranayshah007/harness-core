/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.TestUserProvider.testUserProvider;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.service.impl.WorkflowExecutionBaselineServiceImpl.BASELINE_TTL;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.PipelineExecution;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.baseline.WorkflowExecutionBaseline;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.TimeSeriesDataRecord;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateMachineExecutor;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

/**
 * Created by rsingh on 2/16/18.
 */
@Slf4j
@OwnedBy(HarnessTeam.CV)
public class WorkflowExecutionBaselineServiceTest extends WingsBaseTest {
  private static final SecureRandom random = new SecureRandom();
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;

  private String appId;
  private String workflowId;
  private final String userEmail = "rsingh@harness.io";
  private final String userName = "raghu";
  private final User user = User.Builder.anUser().email(userEmail).name(userName).build();

  @Before
  public void setUp() {
    appId = UUID.randomUUID().toString();
    workflowId = UUID.randomUUID().toString();
    wingsPersistence.save(user);
    UserThreadLocal.set(user);
  }

  @Test()
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNoWorkflowExecution() {
    String workflowExecutionId = UUID.randomUUID().toString();
    try {
      workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
      fail("Did not fail for invalid workflow execution id");
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.BASELINE_CONFIGURATION_ERROR);
      assertThat(e.getMessage())
          .isEqualTo("No workflow execution found with id: " + workflowExecutionId + " appId: " + appId);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNoPipelineExecutionsForPipeline() {
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().workflowId(workflowId).workflowType(WorkflowType.PIPELINE).build();
    workflowExecution.setAppId(appId);
    String workflowExecutionId = wingsPersistence.save(workflowExecution);
    try {
      workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.BASELINE_CONFIGURATION_ERROR);
      assertThat(e.getMessage()).isEqualTo("Pipeline has not been executed.");
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNoWorkflowExecutionsForPipeline() {
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().workflowId(workflowId).workflowType(WorkflowType.PIPELINE).build();
    workflowExecution.setAppId(appId);
    PipelineExecution pipelineExecution = PipelineExecution.Builder.aPipelineExecution().build();
    workflowExecution.setPipelineExecution(pipelineExecution);
    String workflowExecutionId = wingsPersistence.save(workflowExecution);
    try {
      workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.BASELINE_CONFIGURATION_ERROR);
      assertThat(e.getMessage()).isEqualTo("No workflows have been executed for this pipeline.");
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNoVerificationSteps() {
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().workflowId(workflowId).workflowType(WorkflowType.PIPELINE).build();
    workflowExecution.setAppId(appId);
    PipelineStageExecution pipelineStageExecution = PipelineStageExecution.builder().build();
    PipelineExecution pipelineExecution = PipelineExecution.Builder.aPipelineExecution()
                                              .withPipelineStageExecutions(Lists.newArrayList(pipelineStageExecution))
                                              .build();
    workflowExecution.setPipelineExecution(pipelineExecution);
    String workflowExecutionId = wingsPersistence.save(workflowExecution);
    try {
      workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.BASELINE_CONFIGURATION_ERROR);
      assertThat(e.getMessage())
          .isEqualTo("Either there is no workflow execution with verification steps "
              + "or verification steps haven't been executed for the workflow.");
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testMarkAndCreateBaselinesForPipeline() {
    int numOfWorkflowExecutions = 10;
    List<String> envIds = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<String> workflowExecutionIds = new ArrayList<>();
    List<WorkflowExecution> workflowExecutions = new ArrayList<>();
    String pipelineExecutionId = UUID.randomUUID().toString();

    for (int i = 0; i < numOfWorkflowExecutions; i++) {
      String envId = UUID.randomUUID().toString();
      envIds.add(envId);
      String serviceId = UUID.randomUUID().toString();
      serviceIds.add(serviceId);

      WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                .workflowId(workflowId)
                                                .envId(envId)
                                                .serviceIds(Lists.newArrayList(serviceId))
                                                .pipelineExecutionId(pipelineExecutionId)
                                                .build();
      workflowExecution.setAppId(appId);

      String workflowExecutionId = wingsPersistence.save(workflowExecution);
      workflowExecutionIds.add(workflowExecutionId);
      workflowExecutions.add(workflowExecution);
      StateExecutionInstance stateExecutionInstance =
          aStateExecutionInstance().executionUuid(workflowExecutionId).stateType(StateType.DYNA_TRACE.name()).build();

      createDataRecords(workflowExecutionId, appId);
      stateExecutionInstance.setAppId(appId);
      wingsPersistence.save(stateExecutionInstance);
    }

    verifyValidUntil(workflowExecutionIds, Date.from(OffsetDateTime.now().plusMonths(7).toInstant()), true);

    for (String workflowExecutionId : workflowExecutionIds) {
      WorkflowExecution workflowExecution =
          wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionId);
      assertThat(workflowExecution.isBaseline()).isFalse();
    }

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .workflowId(workflowId)
                                              .workflowType(WorkflowType.PIPELINE)
                                              .uuid(pipelineExecutionId)
                                              .build();
    workflowExecution.setAppId(appId);
    PipelineStageExecution pipelineStageExecution =
        PipelineStageExecution.builder().workflowExecutions(workflowExecutions).build();
    PipelineExecution pipelineExecution = PipelineExecution.Builder.aPipelineExecution()
                                              .withPipelineStageExecutions(Lists.newArrayList(pipelineStageExecution))
                                              .build();
    workflowExecution.setPipelineExecution(pipelineExecution);
    String workflowExecutionId = wingsPersistence.save(workflowExecution);
    Set<WorkflowExecutionBaseline> workflowExecutionBaselines =
        workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
    assertThat(workflowExecutionBaselines).hasSize(numOfWorkflowExecutions);
    assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count())
        .isEqualTo(numOfWorkflowExecutions);

    for (WorkflowExecutionBaseline baseline : workflowExecutionBaselines) {
      assertThat(baseline.getAppId()).isEqualTo(appId);
      assertThat(baseline.getWorkflowId()).isEqualTo(workflowId);
      assertThat(baseline.getPipelineExecutionId()).isEqualTo(workflowExecutionId);
      assertThat(envIds.contains(baseline.getEnvId())).isTrue();
      assertThat(serviceIds.contains(baseline.getServiceId())).isTrue();
      assertThat(workflowExecutionIds.contains(baseline.getWorkflowExecutionId())).isTrue();
    }

    for (String executionId : workflowExecutionIds) {
      WorkflowExecution workflowExecution1 = wingsPersistence.getWithAppId(WorkflowExecution.class, appId, executionId);
      assertThat(workflowExecution1.isBaseline()).isTrue();
    }

    WorkflowExecution savedPipelineExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);
    pipelineExecution = savedPipelineExecution.getPipelineExecution();
    List<PipelineStageExecution> pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
    if (isNotEmpty(pipelineStageExecutions)) {
      pipelineStageExecutions.forEach(
          stageExecution -> stageExecution.getWorkflowExecutions().forEach(pipelineWorkflowExecution -> {
            assertThat(pipelineWorkflowExecution.isBaseline()).isTrue();
          }));
    }

    verifyValidUntil(workflowExecutionIds, Date.from(OffsetDateTime.now().plusMonths(7).toInstant()), false);

    // unmark the baseline and verify
    workflowExecutionBaselines = workflowExecutionService.markBaseline(appId, workflowExecutionId, false);
    assertThat(workflowExecutionBaselines).hasSize(numOfWorkflowExecutions);
    assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count()).isEqualTo(0);
    savedPipelineExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);

    pipelineExecution = savedPipelineExecution.getPipelineExecution();
    pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
    if (isNotEmpty(pipelineStageExecutions)) {
      pipelineStageExecutions.forEach(
          stageExecution -> stageExecution.getWorkflowExecutions().forEach(pipelineWorkflowExecution -> {
            assertThat(pipelineWorkflowExecution.isBaseline()).isFalse();
          }));
    }

    // mark again and verify
    workflowExecutionBaselines = workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
    assertThat(workflowExecutionBaselines).hasSize(numOfWorkflowExecutions);
    assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count())
        .isEqualTo(numOfWorkflowExecutions);

    for (WorkflowExecutionBaseline baseline : workflowExecutionBaselines) {
      assertThat(baseline.getAppId()).isEqualTo(appId);
      assertThat(baseline.getWorkflowId()).isEqualTo(workflowId);
      assertThat(baseline.getPipelineExecutionId()).isEqualTo(workflowExecutionId);
      assertThat(envIds.contains(baseline.getEnvId())).isTrue();
      assertThat(serviceIds.contains(baseline.getServiceId())).isTrue();
      assertThat(workflowExecutionIds.contains(baseline.getWorkflowExecutionId())).isTrue();
    }

    for (String executionId : workflowExecutionIds) {
      WorkflowExecution workflowExecution1 = wingsPersistence.getWithAppId(WorkflowExecution.class, appId, executionId);
      assertThat(workflowExecution1.isBaseline()).isTrue();
    }

    savedPipelineExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);
    pipelineExecution = savedPipelineExecution.getPipelineExecution();
    pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
    if (isNotEmpty(pipelineStageExecutions)) {
      pipelineStageExecutions.forEach(
          stageExecution -> stageExecution.getWorkflowExecutions().forEach(pipelineWorkflowExecution -> {
            assertThat(pipelineWorkflowExecution.isBaseline()).isTrue();
          }));
    }
  }

  private List<TimeSeriesDataRecord> createDataRecords(WorkflowExecution workflowExecution) {
    List<TimeSeriesDataRecord> dataRecords = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      dataRecords.add(TimeSeriesDataRecord.builder()
                          .workflowExecutionId(workflowExecution.getUuid())
                          .accountId(workflowExecution.getAccountId())
                          .build());
    }
    return dataRecords;
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testMarkBaselinesUpdateForPipeline() {
    int numOfWorkflowExecutions = 10;
    int numOfPipelines = 5;
    List<String> envIds = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<List<String>> workflowExecutionIds = new ArrayList<>();
    List<String> pipelineIds = new ArrayList<>();

    for (int n = 0; n < numOfWorkflowExecutions; n++) {
      envIds.add(UUID.randomUUID().toString());
      serviceIds.add(UUID.randomUUID().toString());
    }

    String pipeLineExecId = "";
    for (int i = 0; i < numOfPipelines; i++) {
      pipeLineExecId = UUID.randomUUID().toString();
      log.info("running for pipeline " + i);
      workflowExecutionIds.add(new ArrayList<>());
      List<WorkflowExecution> workflowExecutions = new ArrayList<>();
      for (int j = 0; j < numOfWorkflowExecutions; j++) {
        WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                  .workflowId(workflowId)
                                                  .envId(envIds.get(j))
                                                  .serviceIds(Lists.newArrayList(serviceIds.get(j)))
                                                  .uuid("workflowExecution-" + i + "-" + j)
                                                  .pipelineExecutionId(pipeLineExecId)
                                                  .build();
        workflowExecution.setAppId(appId);

        String workflowExecutionId = wingsPersistence.save(workflowExecution);
        workflowExecutionIds.get(i).add(workflowExecutionId);
        workflowExecutions.add(workflowExecution);
        StateExecutionInstance stateExecutionInstance =
            aStateExecutionInstance().executionUuid(workflowExecutionId).stateType(StateType.DYNA_TRACE.name()).build();
        stateExecutionInstance.setAppId(appId);
        wingsPersistence.save(stateExecutionInstance);
      }

      for (String workflowExecutionId : workflowExecutionIds.get(i)) {
        WorkflowExecution workflowExecution =
            wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionId);
        assertThat(workflowExecution.isBaseline()).isFalse();
      }

      WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                .workflowId(workflowId)
                                                .workflowType(WorkflowType.PIPELINE)
                                                .uuid(pipeLineExecId)
                                                .build();
      workflowExecution.setAppId(appId);
      PipelineStageExecution pipelineStageExecution =
          PipelineStageExecution.builder().workflowExecutions(workflowExecutions).build();
      PipelineExecution pipelineExecution = PipelineExecution.Builder.aPipelineExecution()
                                                .withPipelineStageExecutions(Lists.newArrayList(pipelineStageExecution))
                                                .build();
      workflowExecution.setPipelineExecution(pipelineExecution);
      wingsPersistence.save(workflowExecution);
      pipelineIds.add(pipeLineExecId);

      Set<WorkflowExecutionBaseline> workflowExecutionBaselines =
          workflowExecutionService.markBaseline(appId, pipeLineExecId, true);
      assertThat(workflowExecutionBaselines).hasSize(numOfWorkflowExecutions);
      assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count())
          .isEqualTo(numOfWorkflowExecutions);

      for (WorkflowExecutionBaseline baseline : workflowExecutionBaselines) {
        assertThat(baseline.getAppId()).isEqualTo(appId);
        assertThat(baseline.getWorkflowId()).isEqualTo(workflowId);
        assertThat(envIds.contains(baseline.getEnvId())).isTrue();
        assertThat(serviceIds.contains(baseline.getServiceId())).isTrue();
        assertThat(workflowExecutionIds.get(i).contains(baseline.getWorkflowExecutionId())).isTrue();
      }

      for (int pipeline = 0; pipeline <= i; pipeline++) {
        WorkflowExecution pipelineExec =
            wingsPersistence.getWithAppId(WorkflowExecution.class, appId, pipelineIds.get(pipeline));
        assertThat(pipelineExec.isBaseline()).isEqualTo(pipeline == i);

        for (String executionId : workflowExecutionIds.get(pipeline)) {
          WorkflowExecution workflowExecution1 =
              wingsPersistence.getWithAppId(WorkflowExecution.class, appId, executionId);
          assertThat(workflowExecution1.isBaseline()).isEqualTo(pipeline == i);
        }
      }

      // unmark and verify
      workflowExecutionBaselines = workflowExecutionService.markBaseline(appId, pipeLineExecId, false);
      assertThat(workflowExecutionBaselines).hasSize(numOfWorkflowExecutions);
      assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count()).isEqualTo(0);

      for (WorkflowExecutionBaseline baseline : workflowExecutionBaselines) {
        assertThat(baseline.getAppId()).isEqualTo(appId);
        assertThat(baseline.getWorkflowId()).isEqualTo(workflowId);
        assertThat(envIds.contains(baseline.getEnvId())).isTrue();
        assertThat(serviceIds.contains(baseline.getServiceId())).isTrue();
      }

      for (String executionId : workflowExecutionIds.get(i)) {
        WorkflowExecution workflowExecution1 =
            wingsPersistence.getWithAppId(WorkflowExecution.class, appId, executionId);
        assertThat(workflowExecution1.isBaseline()).isFalse();
      }

      WorkflowExecution savedPipelineExecution = wingsPersistence.get(WorkflowExecution.class, pipeLineExecId);
      pipelineExecution = savedPipelineExecution.getPipelineExecution();
      List<PipelineStageExecution> pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
      if (isNotEmpty(pipelineStageExecutions)) {
        pipelineStageExecutions.forEach(
            stageExecution -> stageExecution.getWorkflowExecutions().forEach(pipelineWorkflowExecution -> {
              assertThat(pipelineWorkflowExecution.isBaseline()).isFalse();
            }));
      }

      // mark again and verify
      workflowExecutionBaselines = workflowExecutionService.markBaseline(appId, pipeLineExecId, true);
      assertThat(workflowExecutionBaselines).hasSize(numOfWorkflowExecutions);
      assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count())
          .isEqualTo(numOfWorkflowExecutions);

      for (WorkflowExecutionBaseline baseline : workflowExecutionBaselines) {
        assertThat(baseline.getAppId()).isEqualTo(appId);
        assertThat(baseline.getWorkflowId()).isEqualTo(workflowId);
        assertThat(envIds.contains(baseline.getEnvId())).isTrue();
        assertThat(serviceIds.contains(baseline.getServiceId())).isTrue();
      }

      for (String executionId : workflowExecutionIds.get(i)) {
        WorkflowExecution workflowExecution1 =
            wingsPersistence.getWithAppId(WorkflowExecution.class, appId, executionId);
        assertThat(workflowExecution1.isBaseline()).isTrue();
        List<TimeSeriesDataRecord> dataRecords = wingsPersistence.createQuery(TimeSeriesDataRecord.class)
                                                     .filter("workflowExecutionId", executionId)
                                                     .asList();
        for (TimeSeriesDataRecord dataRecord : dataRecords) {
          assertThat(dataRecord.getValidUntil()).isEqualTo(BASELINE_TTL);
        }
      }

      savedPipelineExecution = wingsPersistence.get(WorkflowExecution.class, pipeLineExecId);
      pipelineExecution = savedPipelineExecution.getPipelineExecution();
      pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
      if (isNotEmpty(pipelineStageExecutions)) {
        pipelineStageExecutions.forEach(
            stageExecution -> stageExecution.getWorkflowExecutions().forEach(pipelineWorkflowExecution -> {
              assertThat(pipelineWorkflowExecution.isBaseline()).isTrue();
            }));
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testMarkAndCreateBaselinesForWorkflow() {
    int numOfWorkflowExecutions = 10;
    List<String> workflowExecutionIds = new ArrayList<>();
    List<WorkflowExecution> workflowExecutions = new ArrayList<>();
    String envId = UUID.randomUUID().toString();
    String serviceId = UUID.randomUUID().toString();

    for (int i = 0; i < numOfWorkflowExecutions; i++) {
      WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                .workflowId(workflowId)
                                                .envId(envId)
                                                .serviceIds(Lists.newArrayList(serviceId))
                                                .workflowType(WorkflowType.ORCHESTRATION)
                                                .build();
      workflowExecution.setAppId(appId);

      String workflowExecutionId = wingsPersistence.save(workflowExecution);
      workflowExecutionIds.add(workflowExecutionId);
      workflowExecutions.add(workflowExecution);
      StateExecutionInstance stateExecutionInstance =
          aStateExecutionInstance().executionUuid(workflowExecutionId).stateType(StateType.DYNA_TRACE.name()).build();
      stateExecutionInstance.setAppId(appId);
      wingsPersistence.save(stateExecutionInstance);
    }

    for (String workflowExecutionId : workflowExecutionIds) {
      WorkflowExecution workflowExecution =
          wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionId);
      assertThat(workflowExecution.isBaseline()).isFalse();
    }

    for (int i = 0; i < numOfWorkflowExecutions; i++) {
      String workflowExecutionId = workflowExecutionIds.get(i);
      Set<WorkflowExecutionBaseline> workflowExecutionBaselines =
          workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
      assertThat(workflowExecutionBaselines).hasSize(1);
      assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count()).isEqualTo(1);
      WorkflowExecutionBaseline baseline = workflowExecutionBaselines.iterator().next();
      assertThat(baseline.getAppId()).isEqualTo(appId);
      assertThat(baseline.getWorkflowId()).isEqualTo(workflowId);
      assertThat(baseline.getEnvId()).isEqualTo(envId);
      assertThat(baseline.getServiceId()).isEqualTo(serviceId);

      for (String executionId : workflowExecutionIds) {
        WorkflowExecution workflowExecution =
            wingsPersistence.getWithAppId(WorkflowExecution.class, appId, executionId);
        if (workflowExecutionId.equals(executionId)) {
          assertThat(workflowExecution.isBaseline()).isTrue();
        } else {
          assertThat(workflowExecution.isBaseline()).isFalse();
        }
      }

      // unmark and test
      workflowExecutionBaselines = workflowExecutionService.markBaseline(appId, workflowExecutionId, false);
      assertThat(workflowExecutionBaselines).hasSize(1);
      assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count()).isEqualTo(0);

      for (String executionId : workflowExecutionIds) {
        WorkflowExecution workflowExecution =
            wingsPersistence.getWithAppId(WorkflowExecution.class, appId, executionId);
        assertThat(workflowExecution.isBaseline()).isFalse();
      }

      // mark again and test
      workflowExecutionBaselines = workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
      assertThat(workflowExecutionBaselines).hasSize(1);
      assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count()).isEqualTo(1);

      for (String executionId : workflowExecutionIds) {
        WorkflowExecution workflowExecution =
            wingsPersistence.getWithAppId(WorkflowExecution.class, appId, executionId);
        if (workflowExecutionId.equals(executionId)) {
          assertThat(workflowExecution.isBaseline()).isTrue();
        } else {
          assertThat(workflowExecution.isBaseline()).isFalse();
        }
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testMarkAndCreateBaselinesForPipelineAndWorkflow() {
    int numOfWorkflowExecutions = 10;
    List<String> envIds = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<String> workflowExecutionIds = new ArrayList<>();
    List<WorkflowExecution> workflowExecutions = new ArrayList<>();
    String pipelineExecutionId = UUID.randomUUID().toString();

    for (int i = 0; i < numOfWorkflowExecutions; i++) {
      String envId = UUID.randomUUID().toString();
      envIds.add(envId);
      String serviceId = UUID.randomUUID().toString();
      serviceIds.add(serviceId);

      WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                .workflowId(workflowId)
                                                .envId(envId)
                                                .serviceIds(Lists.newArrayList(serviceId))
                                                .pipelineExecutionId(pipelineExecutionId)
                                                .build();
      workflowExecution.setAppId(appId);

      String workflowExecutionId = wingsPersistence.save(workflowExecution);
      workflowExecutionIds.add(workflowExecutionId);
      workflowExecutions.add(workflowExecution);
      StateExecutionInstance stateExecutionInstance =
          aStateExecutionInstance().executionUuid(workflowExecutionId).stateType(StateType.DYNA_TRACE.name()).build();
      stateExecutionInstance.setAppId(appId);
      wingsPersistence.save(stateExecutionInstance);
    }

    for (String workflowExecutionId : workflowExecutionIds) {
      WorkflowExecution workflowExecution =
          wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionId);
      assertThat(workflowExecution.isBaseline()).isFalse();
    }

    WorkflowExecution piplelineExecution = WorkflowExecution.builder()
                                               .workflowId(workflowId)
                                               .workflowType(WorkflowType.PIPELINE)
                                               .uuid(pipelineExecutionId)
                                               .build();
    piplelineExecution.setAppId(appId);
    PipelineStageExecution pipelineStageExecution =
        PipelineStageExecution.builder().workflowExecutions(workflowExecutions).build();
    PipelineExecution pipelineExecution = PipelineExecution.Builder.aPipelineExecution()
                                              .withPipelineStageExecutions(Lists.newArrayList(pipelineStageExecution))
                                              .build();
    piplelineExecution.setPipelineExecution(pipelineExecution);
    wingsPersistence.save(piplelineExecution);
    Set<WorkflowExecutionBaseline> workflowExecutionBaselines =
        workflowExecutionService.markBaseline(appId, pipelineExecutionId, true);
    assertThat(workflowExecutionBaselines).hasSize(numOfWorkflowExecutions);

    for (WorkflowExecutionBaseline baseline : workflowExecutionBaselines) {
      assertThat(baseline.getAppId()).isEqualTo(appId);
      assertThat(baseline.getWorkflowId()).isEqualTo(workflowId);
      assertThat(baseline.getPipelineExecutionId()).isEqualTo(pipelineExecutionId);
      assertThat(envIds.contains(baseline.getEnvId())).isTrue();
      assertThat(serviceIds.contains(baseline.getServiceId())).isTrue();
      assertThat(workflowExecutionIds.contains(baseline.getWorkflowExecutionId())).isTrue();
    }

    for (String executionId : workflowExecutionIds) {
      WorkflowExecution workflowExecution1 = wingsPersistence.getWithAppId(WorkflowExecution.class, appId, executionId);
      assertThat(workflowExecution1.isBaseline()).isTrue();
    }

    WorkflowExecution savedPipelineExecution = wingsPersistence.get(WorkflowExecution.class, pipelineExecutionId);
    pipelineExecution = savedPipelineExecution.getPipelineExecution();
    List<PipelineStageExecution> pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
    if (isNotEmpty(pipelineStageExecutions)) {
      pipelineStageExecutions.forEach(
          stageExecution -> stageExecution.getWorkflowExecutions().forEach(pipelineWorkflowExecution -> {
            assertThat(pipelineWorkflowExecution.isBaseline()).isTrue();
          }));
    }

    final int workflowNum = random.nextInt(10) % numOfWorkflowExecutions;
    String envId = envIds.get(workflowNum);
    String serviceId = serviceIds.get(workflowNum);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .workflowId(workflowId)
                                              .envId(envId)
                                              .workflowType(WorkflowType.ORCHESTRATION)
                                              .serviceIds(Lists.newArrayList(serviceId))
                                              .build();
    workflowExecution.setAppId(appId);
    String workflowExecutionId = wingsPersistence.save(workflowExecution);

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().executionUuid(workflowExecutionId).stateType(StateType.DYNA_TRACE.name()).build();
    stateExecutionInstance.setAppId(appId);
    wingsPersistence.save(stateExecutionInstance);

    workflowExecutionBaselines = workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
    assertThat(workflowExecutionBaselines).hasSize(1);
    WorkflowExecutionBaseline workflowExecutionBaseline = workflowExecutionBaselines.iterator().next();
    assertThat(workflowExecutionBaseline.getEnvId()).isEqualTo(envId);
    assertThat(workflowExecutionBaseline.getServiceId()).isEqualTo(serviceId);
    assertThat(workflowExecutionBaseline.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowExecutionBaseline.getWorkflowExecutionId()).isEqualTo(workflowExecutionId);
    assertThat(workflowExecutionBaseline.getPipelineExecutionId()).isNull();

    savedPipelineExecution = wingsPersistence.get(WorkflowExecution.class, pipelineExecutionId);
    pipelineExecution = savedPipelineExecution.getPipelineExecution();
    pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
    AtomicInteger numOfBaselineWorkflow = new AtomicInteger(0);
    AtomicInteger numOfNonBaselineWorkflow = new AtomicInteger(0);
    if (isNotEmpty(pipelineStageExecutions)) {
      pipelineStageExecutions.forEach(stageExecution -> {
        for (int i = 0; i < numOfWorkflowExecutions; i++) {
          if (i == workflowNum) {
            assertThat(stageExecution.getWorkflowExecutions().get(i).isBaseline()).isFalse();
            numOfNonBaselineWorkflow.incrementAndGet();
          } else {
            assertThat(stageExecution.getWorkflowExecutions().get(i).isBaseline()).isTrue();
            numOfBaselineWorkflow.incrementAndGet();
          }
        }
      });
    }

    assertThat(numOfNonBaselineWorkflow.get()).isEqualTo(1);
    assertThat(numOfBaselineWorkflow.get()).isEqualTo(numOfWorkflowExecutions - 1);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetBaselineDetails() throws IllegalAccessException {
    testUserProvider.setActiveUser(EmbeddedUser.builder().uuid(user.getUuid()).name(userName).email(userEmail).build());

    StateMachineExecutor stateMachineExecutor = Mockito.mock(StateMachineExecutor.class);
    int numOfWorkflowExecutions = 10;
    List<String> envIds = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<String> workflowExecutionIds = new ArrayList<>();
    List<String> stateExecutionIds = new ArrayList<>();
    List<WorkflowExecution> workflowExecutions = new ArrayList<>();
    String pipelineExecutionId = UUID.randomUUID().toString();

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        mock(WorkflowStandardParamsExtensionService.class);
    for (int i = 0; i < numOfWorkflowExecutions; i++) {
      String envId = UUID.randomUUID().toString();
      envIds.add(envId);
      String serviceId = UUID.randomUUID().toString();
      serviceIds.add(serviceId);

      WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                .workflowId(workflowId)
                                                .envId(envId)
                                                .serviceIds(Lists.newArrayList(serviceId))
                                                .pipelineExecutionId(pipelineExecutionId)
                                                .build();
      workflowExecution.setAppId(appId);

      String workflowExecutionId = wingsPersistence.save(workflowExecution);
      workflowExecutionIds.add(workflowExecutionId);
      workflowExecutions.add(workflowExecution);
      StateExecutionInstance stateExecutionInstance =
          aStateExecutionInstance().executionUuid(workflowExecutionId).stateType(StateType.DYNA_TRACE.name()).build();
      stateExecutionInstance.setAppId(appId);
      String stateExecutionId = wingsPersistence.save(stateExecutionInstance);
      stateExecutionIds.add(stateExecutionId);

      ExecutionContextImpl executionContext = Mockito.mock(ExecutionContextImpl.class);
      WorkflowStandardParams workflowStandardParams = Mockito.mock(WorkflowStandardParams.class);
      when(executionContext.fetchWorkflowStandardParamsFromContext()).thenReturn(workflowStandardParams);
      when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
      when(executionContext.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
          .thenReturn(PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(serviceId).build()).build());
      when(stateMachineExecutor.getExecutionContext(appId, workflowExecutionId, stateExecutionId))
          .thenReturn(executionContext);

      when(workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams))
          .thenReturn(anEnvironment().uuid(envId).build());
      on(executionContext).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    }

    FieldUtils.writeField(workflowExecutionService, "stateMachineExecutor", stateMachineExecutor, true);
    FieldUtils.writeField(workflowExecutionService, "workflowStandardParamsExtensionService",
        workflowStandardParamsExtensionService, true);

    for (int i = 0; i < numOfWorkflowExecutions; i++) {
      WorkflowExecution workflowExecution =
          wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionIds.get(i));
      assertThat(workflowExecution.isBaseline()).isFalse();

      WorkflowExecutionBaseline baselineDetails = workflowExecutionService.getBaselineDetails(
          appId, workflowExecutionIds.get(i), stateExecutionIds.get(i), workflowExecutionIds.get(i));
      assertThat(baselineDetails)
          .isEqualTo(WorkflowExecutionBaseline.builder()
                         .workflowId(workflowId)
                         .envId(envIds.get(i))
                         .serviceId(serviceIds.get(i))
                         .workflowExecutionId(workflowExecutionIds.get(i))
                         .pipelineExecutionId(workflowExecution.getPipelineExecutionId())
                         .build());
    }

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .workflowId(workflowId)
                                              .workflowType(WorkflowType.PIPELINE)
                                              .uuid(pipelineExecutionId)
                                              .build();
    workflowExecution.setAppId(appId);
    PipelineStageExecution pipelineStageExecution =
        PipelineStageExecution.builder().workflowExecutions(workflowExecutions).build();
    PipelineExecution pipelineExecution = PipelineExecution.Builder.aPipelineExecution()
                                              .withPipelineStageExecutions(Lists.newArrayList(pipelineStageExecution))
                                              .build();
    workflowExecution.setPipelineExecution(pipelineExecution);
    String workflowExecutionId = wingsPersistence.save(workflowExecution);

    Set<WorkflowExecutionBaseline> workflowExecutionBaselines =
        workflowExecutionService.markBaseline(appId, workflowExecutionId, true);
    assertThat(workflowExecutionBaselines).hasSize(numOfWorkflowExecutions);
    assertThat(wingsPersistence.createQuery(WorkflowExecutionBaseline.class).count())
        .isEqualTo(numOfWorkflowExecutions);

    for (WorkflowExecutionBaseline baseline : workflowExecutionBaselines) {
      assertThat(baseline.getAppId()).isEqualTo(appId);
      assertThat(baseline.getWorkflowId()).isEqualTo(workflowId);
      assertThat(baseline.getPipelineExecutionId()).isEqualTo(workflowExecutionId);
      assertThat(envIds.contains(baseline.getEnvId())).isTrue();
      assertThat(serviceIds.contains(baseline.getServiceId())).isTrue();
      assertThat(workflowExecutionIds.contains(baseline.getWorkflowExecutionId())).isTrue();
    }

    for (int i = 0; i < numOfWorkflowExecutions; i++) {
      WorkflowExecution workflowExecution1 =
          wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionIds.get(i));
      assertThat(workflowExecution1.isBaseline()).isTrue();
      WorkflowExecutionBaseline baselineDetails = workflowExecutionService.getBaselineDetails(
          appId, workflowExecutionIds.get(i), stateExecutionIds.get(i), workflowExecutionIds.get(i));
      assertThat(baselineDetails).isNotNull();
      assertThat(baselineDetails.getCreatedBy().getEmail()).isEqualTo(userEmail);
      assertThat(baselineDetails.getCreatedBy().getName()).isEqualTo(userName);
      assertThat(baselineDetails.getLastUpdatedBy().getEmail()).isEqualTo(userEmail);
      assertThat(baselineDetails.getLastUpdatedBy().getName()).isEqualTo(userName);
    }

    WorkflowExecution savedPipelineExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);
    pipelineExecution = savedPipelineExecution.getPipelineExecution();
    List<PipelineStageExecution> pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
    if (isNotEmpty(pipelineStageExecutions)) {
      pipelineStageExecutions.forEach(
          stageExecution -> stageExecution.getWorkflowExecutions().forEach(pipelineWorkflowExecution -> {
            assertThat(pipelineWorkflowExecution.isBaseline()).isTrue();
          }));
    }
  }

  private void createDataRecords(String workflowExecutionId, String accountId) {
    for (int i = 0; i < 100; i++) {
      wingsPersistence.save(
          TimeSeriesDataRecord.builder().workflowExecutionId(workflowExecutionId).accountId(accountId).build());
      wingsPersistence.save(
          NewRelicMetricAnalysisRecord.builder().workflowExecutionId(workflowExecutionId).appId(appId).build());
      TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
      timeSeriesMLAnalysisRecord.setWorkflowExecutionId(workflowExecutionId);
      timeSeriesMLAnalysisRecord.setAppId(appId);
      wingsPersistence.save(timeSeriesMLAnalysisRecord);

      LogMLAnalysisRecord logMLAnalysisRecord = new LogMLAnalysisRecord();
      logMLAnalysisRecord.setWorkflowExecutionId(workflowExecutionId);
      logMLAnalysisRecord.setAppId(appId);
      wingsPersistence.save(logMLAnalysisRecord);
    }
  }

  private void verifyValidUntil(List<String> workflowExecutionIds, Date validUntil, boolean greater) {
    workflowExecutionIds.forEach(workflowExecutionId -> {
      List<NewRelicMetricDataRecord> newRelicMetricDataRecords =
          wingsPersistence.createQuery(NewRelicMetricDataRecord.class, excludeAuthority).asList();
      newRelicMetricDataRecords.forEach(newRelicMetricDataRecord -> {
        if (greater) {
          assertThat(validUntil.getTime() > newRelicMetricDataRecord.getValidUntil().getTime()).isTrue();
        } else {
          assertThat(validUntil.getTime() < newRelicMetricDataRecord.getValidUntil().getTime()).isTrue();
        }
      });

      List<NewRelicMetricAnalysisRecord> newRelicMetricAnalysisRecords =
          wingsPersistence.createQuery(NewRelicMetricAnalysisRecord.class, excludeAuthority).asList();
      newRelicMetricAnalysisRecords.forEach(newRelicMetricAnalysisRecord -> {
        if (greater) {
          assertThat(validUntil.getTime() > newRelicMetricAnalysisRecord.getValidUntil().getTime()).isTrue();
        } else {
          assertThat(validUntil.getTime() < newRelicMetricAnalysisRecord.getValidUntil().getTime()).isTrue();
        }
      });

      List<TimeSeriesMLAnalysisRecord> timeSeriesMLAnalysisRecords =
          wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority).asList();
      timeSeriesMLAnalysisRecords.forEach(timeSeriesMLAnalysisRecord -> {
        if (greater) {
          assertThat(validUntil.getTime() > timeSeriesMLAnalysisRecord.getValidUntil().getTime()).isTrue();
        } else {
          assertThat(validUntil.getTime() < timeSeriesMLAnalysisRecord.getValidUntil().getTime()).isTrue();
        }
      });

      List<LogMLAnalysisRecord> logMLAnalysisRecords =
          wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList();
      logMLAnalysisRecords.forEach(logMLAnalysisRecord -> {
        if (greater) {
          assertThat(validUntil.getTime() > logMLAnalysisRecord.getValidUntil().getTime()).isTrue();
        } else {
          assertThat(validUntil.getTime() < logMLAnalysisRecord.getValidUntil().getTime()).isTrue();
        }
      });
    });
  }
}
